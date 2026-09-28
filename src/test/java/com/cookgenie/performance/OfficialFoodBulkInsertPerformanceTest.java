package com.cookgenie.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.cookgenie.domain.ingredient.entity.OfficialDish;
import com.cookgenie.domain.ingredient.external.OfficialDishCandidate;
import com.cookgenie.domain.ingredient.repository.OfficialFoodBulkInsertRepository;
import com.cookgenie.domain.ingredient.repository.OfficialDishRepository;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.persistence.EntityManagerFactory;

/**
 * 공공데이터 전체 동기화의 저장 단계: JPA saveAll (개선 전) vs JDBC 배치 INSERT (개선 후).
 *
 * <p>동기화는 정부 API에서 1000건씩 페이지를 받아 저장하는 걸 약 591번 반복한다. 네트워크(정부 API) 시간은
 * 우리가 줄일 수 없으니 빼고, 우리 쪽 저장 단계만 같은 조건(1000건 단위 배치)으로 비교한다.
 * 가공식품 테이블과 구조(컬럼·유니크 키·FULLTEXT 인덱스)가 같은 음식 테이블({@code official_dishes})에 넣는다 -
 * 가공식품 테이블에 넣었다 지우면 FULLTEXT 인덱스에 삭제 표시가 쌓여서 검색 성능 테스트 수치가 오염되기 때문.
 * 두 테이블 다 PK가 IDENTITY라 Hibernate가 INSERT 배치를 꺼버려서 saveAll이 행마다
 * INSERT를 따로 보낸다 - 실제로 몇 번의 INSERT 문이 나가는지도 Hibernate 통계로 같이 센다.
 */
class OfficialFoodBulkInsertPerformanceTest extends PerformanceTestSupport {

    private static final int ROWS = 30_000;
    private static final int PAGE_SIZE = 1_000;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private OfficialDishRepository repository;
    @Autowired
    private OfficialFoodBulkInsertRepository bulkInsertRepository;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM official_dishes WHERE food_cd LIKE 'JPA%' OR food_cd LIKE 'JDBC%'");
    }

    @Test
    void JPA_saveAll_대비_JDBC_배치_INSERT_적재시간() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        // 개선 전: 기존 동기화 코드와 같은 방식 (엔티티로 바꿔서 페이지마다 saveAll)
        statistics.clear();
        long start = System.currentTimeMillis();
        for (int from = 0; from < ROWS; from += PAGE_SIZE) {
            repository.saveAll(dishes("JPA", from).stream().map(this::toEntity).toList());
        }
        long jpaMillis = System.currentTimeMillis() - start;
        long jpaInserts = statistics.getEntityInsertCount();
        long jpaStatements = statistics.getPrepareStatementCount();

        // 개선 후: JDBC batchUpdate + rewriteBatchedStatements=true (페이지 하나 = multi-row INSERT 한 문장)
        start = System.currentTimeMillis();
        for (int from = 0; from < ROWS; from += PAGE_SIZE) {
            bulkInsertRepository.insertDishes(dishes("JDBC", from));
        }
        long jdbcMillis = System.currentTimeMillis() - start;

        Long jpaRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM official_dishes WHERE food_cd LIKE 'JPA%'", Long.class);
        Long jdbcRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM official_dishes WHERE food_cd LIKE 'JDBC%'", Long.class);
        assertThat(jpaRows).isEqualTo(ROWS);
        assertThat(jdbcRows).isEqualTo(ROWS);

        double pages = (double) OfficialFoodTestData.REAL_DATASET_SIZE / ROWS;
        System.out.printf("%n=== 공공데이터 동기화 저장 단계 (%,d건, %,d건/페이지) ===%n", ROWS, PAGE_SIZE);
        System.out.printf("JPA saveAll   : %,6dms (%,.0f건/s), INSERT 문 %,d번 (엔티티 %,d건)%n",
                jpaMillis, ROWS * 1000.0 / jpaMillis, jpaStatements, jpaInserts);
        System.out.printf("JDBC 배치      : %,6dms (%,.0f건/s), INSERT 문 %d번 (페이지당 1번)%n",
                jdbcMillis, ROWS * 1000.0 / jdbcMillis, ROWS / PAGE_SIZE);
        System.out.printf("개선: %.1f배 빠름 (%.1f%% 단축)%n", (double) jpaMillis / jdbcMillis, (1 - (double) jdbcMillis / jpaMillis) * 100);
        System.out.printf("59만 건 환산 저장시간: 약 %.0f초 → 약 %.0f초%n", jpaMillis * pages / 1000, jdbcMillis * pages / 1000);

        assertThat(jdbcMillis).isLessThan(jpaMillis);
    }

    private List<OfficialDishCandidate> dishes(String foodCdPrefix, int from) {
        return OfficialFoodTestData.generate(foodCdPrefix, from, PAGE_SIZE).stream()
                .map(f -> new OfficialDishCandidate(f.foodCd(), f.foodNm(), f.mfrNm(), f.referenceUnit(), f.calories(),
                        f.carbohydrateG(), f.proteinG(), f.fatG(), f.sugarG(), f.sodiumMg(), f.fiberG()))
                .toList();
    }

    private OfficialDish toEntity(OfficialDishCandidate candidate) {
        return OfficialDish.builder()
                .foodCd(candidate.foodCd())
                .foodNm(candidate.foodNm())
                .restNm(candidate.restNm())
                .referenceUnit(candidate.referenceUnit())
                .calories(candidate.calories())
                .carbohydrateG(candidate.carbohydrateG())
                .proteinG(candidate.proteinG())
                .fatG(candidate.fatG())
                .sugarG(candidate.sugarG())
                .sodiumMg(candidate.sodiumMg())
                .fiberG(candidate.fiberG())
                .build();
    }
}
