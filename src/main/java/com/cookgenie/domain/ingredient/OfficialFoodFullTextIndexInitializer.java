package com.cookgenie.domain.ingredient;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 공공데이터 미러 테이블(가공식품/음식)의 부분검색용 FULLTEXT(ngram) 인덱스를 기동 시 없으면 만들어준다.
 *
 * <p>검색이 {@code LIKE '%키워드%'}였을 때는 앞에 %가 붙어서 food_nm의 B-Tree 인덱스를 못 타고, 게다가
 * {@code food_nm OR mfr_nm} 조건이라 매 검색마다 59만 행을 전부 훑었다(EXPLAIN type=ALL). 한글 부분일치를
 * 인덱스로 처리하려면 글자를 2글자씩 쪼개 색인하는 ngram 파서 FULLTEXT 인덱스가 필요한데, JPA {@code @Index}로는
 * FULLTEXT를 만들 수 없고 Flyway도 꺼져 있어서(ddl-auto: update) 여기서 직접 만든다.
 *
 * <p>59만 건에 인덱스를 새로 만드는 건 수십 초가 걸려서 기동을 막지 않도록 비동기로 돌리고, 인덱스가 준비되기
 * 전에는 {@link #isReady}가 false라서 검색은 기존 LIKE로 동작한다(검색이 에러 나는 구간이 없음).
 * MySQL이 아니면(H2 등) 아무것도 하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfficialFoodFullTextIndexInitializer {

    public static final String PROCESSED_FOOD_TABLE = "official_processed_foods";
    public static final String DISH_TABLE = "official_dishes";

    private static final List<FullTextIndex> INDEXES = List.of(
            new FullTextIndex(PROCESSED_FOOD_TABLE, "ft_official_processed_food_nm_mfr", "food_nm, mfr_nm"),
            new FullTextIndex(DISH_TABLE, "ft_official_dish_nm_rest", "food_nm, rest_nm")
    );

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final Set<String> readyTables = ConcurrentHashMap.newKeySet();

    /** 해당 테이블의 FULLTEXT 인덱스가 존재해서 MATCH ... AGAINST 검색을 써도 되는지. */
    public boolean isReady(String table) {
        return readyTables.contains(table);
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void createIndexesIfMissing() {
        if (!isMySql()) {
            return;
        }
        for (FullTextIndex index : INDEXES) {
            try {
                ensureIndex(index);
                readyTables.add(index.table());
            } catch (Exception e) {
                log.warn("[FULLTEXT] {} 인덱스 준비 실패 - 이 테이블 검색은 LIKE로 동작합니다: {}", index.name(), e.getMessage());
            }
        }
    }

    private void ensureIndex(FullTextIndex index) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                Integer.class, index.table(), index.name());
        if (existing != null && existing > 0) {
            return;
        }
        long start = System.currentTimeMillis();
        log.info("[FULLTEXT] {}.{} 인덱스 생성 시작", index.table(), index.name());
        jdbcTemplate.execute("ALTER TABLE " + index.table() + " ADD FULLTEXT INDEX " + index.name()
                + " (" + index.columns() + ") WITH PARSER ngram");
        log.info("[FULLTEXT] {}.{} 인덱스 생성 완료 ({}ms)", index.table(), index.name(), System.currentTimeMillis() - start);
    }

    private boolean isMySql() {
        try (Connection connection = dataSource.getConnection()) {
            return "MySQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException e) {
            return false;
        }
    }

    private record FullTextIndex(String table, String name, String columns) {
    }
}
