package com.cookgenie.domain.ingredient;

import com.cookgenie.domain.ingredient.entity.OfficialDish;
import com.cookgenie.domain.ingredient.entity.OfficialProcessedFood;
import com.cookgenie.domain.ingredient.repository.OfficialDishRepository;
import com.cookgenie.domain.ingredient.repository.OfficialProcessedFoodRepository;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 공공데이터 미러 테이블(가공식품 약 59만 건, 음식) 부분검색. LIKE와 FULLTEXT(ngram)를 상황에 따라 골라 쓴다.
 *
 * <p><b>왜 FULLTEXT로 전부 바꾸지 않았나</b> - 실측(OfficialFoodSearchPerformanceTest) 결과 두 방식의 약점이 정반대였다.
 * <ul>
 *   <li>LIKE '%키워드%': 인덱스를 못 타서 테이블을 앞에서부터 훑지만 LIMIT 20건을 채우면 바로 멈춘다.
 *       그래서 결과가 많은 검색어("라면")는 수 ms로 빠르고, 결과가 적거나 없는 검색어는 59만 행을 끝까지
 *       훑느라 수백 ms가 걸린다.</li>
 *   <li>FULLTEXT MATCH: 인덱스로 바로 찾지만 LIMIT이 있어도 매칭되는 문서를 전부 모은 뒤에 자른다.
 *       그래서 결과가 적은 검색어는 수 ms로 빠르고, 결과가 수만 건인 검색어는 오히려 LIKE보다 수십 배 느리다.</li>
 * </ul>
 * 그래서 LIKE를 {@value #LIKE_TIME_BUDGET_MS}ms 제한(MySQL {@code MAX_EXECUTION_TIME} 힌트)으로 먼저 실행하고,
 * 시간 안에 20건을 못 채우면(= 결과가 드문 검색어라는 뜻) FULLTEXT로 다시 찾는다. 결과가 많은 검색어는 LIKE가,
 * 드문 검색어는 FULLTEXT가 처리하게 되어 두 방식의 최악의 경우를 둘 다 피한다.
 *
 * <p>FULLTEXT 인덱스가 아직 없거나(기동 직후 생성 중, H2 등) 검색어가 FULLTEXT로 LIKE와 같은 결과를 보장할 수
 * 없는 형태면({@link FullTextKeyword}) 제한 없는 기존 LIKE로 찾는다.
 */
@Component
@RequiredArgsConstructor
public class OfficialFoodSearcher {

    static final int LIKE_TIME_BUDGET_MS = 50;
    /** MySQL ER_QUERY_TIMEOUT: MAX_EXECUTION_TIME을 넘겨서 쿼리가 중단됨. */
    private static final int MYSQL_QUERY_TIMEOUT_ERROR = 3024;

    private final JdbcTemplate jdbcTemplate;
    private final OfficialProcessedFoodRepository officialProcessedFoodRepository;
    private final OfficialDishRepository officialDishRepository;
    private final OfficialFoodFullTextIndexInitializer fullTextIndexInitializer;

    public List<OfficialProcessedFood> searchProcessedFoods(String keyword, int limit) {
        return search(keyword, limit, OfficialFoodFullTextIndexInitializer.PROCESSED_FOOD_TABLE, "mfr_nm",
                () -> officialProcessedFoodRepository.findByFoodNmContainingOrMfrNmContaining(keyword, keyword, PageRequest.of(0, limit)),
                ids -> officialProcessedFoodRepository.findAllById(ids).stream()
                        .sorted(Comparator.comparing(OfficialProcessedFood::getId)).toList(),
                phrase -> officialProcessedFoodRepository.searchByFullText(phrase, limit));
    }

    public List<OfficialDish> searchDishes(String keyword, int limit) {
        return search(keyword, limit, OfficialFoodFullTextIndexInitializer.DISH_TABLE, "rest_nm",
                () -> officialDishRepository.findByFoodNmContainingOrRestNmContaining(keyword, keyword, PageRequest.of(0, limit)),
                ids -> officialDishRepository.findAllById(ids).stream()
                        .sorted(Comparator.comparing(OfficialDish::getId)).toList(),
                phrase -> officialDishRepository.searchByFullText(phrase, limit));
    }

    private <T> List<T> search(String keyword, int limit, String table, String brandColumn,
                               Supplier<List<T>> unlimitedLike,
                               Function<List<Long>, List<T>> loadByIds,
                               Function<String, List<T>> fullText) {
        Optional<String> phrase = FullTextKeyword.toPhrase(keyword);
        if (phrase.isEmpty() || !fullTextIndexInitializer.isReady(table)) {
            return unlimitedLike.get();
        }
        String pattern = "%" + escapeLike(keyword.trim()) + "%";
        try {
            // LIKE 쿼리는 JdbcTemplate으로 직접 보낸다 - 시간 초과 예외가 JPA 리포지토리 프록시를 거치면
            // 바깥 트랜잭션이 rollback-only로 표시돼서, 폴백에 성공해도 커밋 시점에 예외가 난다.
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT /*+ MAX_EXECUTION_TIME(" + LIKE_TIME_BUDGET_MS + ") */ id FROM " + table
                            + " WHERE food_nm LIKE ? OR " + brandColumn + " LIKE ? LIMIT ?",
                    Long.class, pattern, pattern, limit);
            return ids.isEmpty() ? List.of() : loadByIds.apply(ids);
        } catch (DataAccessException e) {
            if (!isQueryTimeout(e)) {
                throw e;
            }
            return fullText.apply(phrase.get());
        }
    }

    private static boolean isQueryTimeout(DataAccessException e) {
        return e.getMostSpecificCause() instanceof SQLException sqlException
                && sqlException.getErrorCode() == MYSQL_QUERY_TIMEOUT_ERROR;
    }

    private static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
