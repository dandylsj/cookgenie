package com.cookgenie.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.cookgenie.domain.ingredient.FullTextKeyword;
import com.cookgenie.domain.ingredient.OfficialFoodFullTextIndexInitializer;
import com.cookgenie.domain.ingredient.OfficialFoodSearcher;
import com.cookgenie.domain.ingredient.entity.OfficialProcessedFood;
import com.cookgenie.domain.ingredient.repository.OfficialFoodBulkInsertRepository;
import com.cookgenie.domain.ingredient.repository.OfficialProcessedFoodRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 가공식품 부분검색 응답시간: LIKE (개선 전) vs FULLTEXT 단독 vs LIKE+FULLTEXT 하이브리드 (개선 후, 실제 적용).
 *
 * <p>실제 데이터와 같은 규모(590,542건)의 가짜 데이터를 {@code cookgenie_perf} 스키마에 한 번 적재해두고
 * (다음 실행부터는 재사용), 결과가 많은 검색어부터 결과가 없는 검색어까지 섞어서 같은 검색어를 세 방식으로
 * 각각 30번씩 실행해 평균/p95 응답시간을 비교한다. 빨라져도 결과가 달라지면 의미가 없으므로 FULLTEXT와 LIKE의
 * 매칭 건수가 같은지, 하이브리드가 LIMIT만큼(또는 전체 매칭 건수만큼) 정확히 돌려주는지도 같이 검증한다.
 */
class OfficialFoodSearchPerformanceTest extends PerformanceTestSupport {

    private static final int PAGE_SIZE = 20;
    private static final int WARMUP = 3;
    private static final int RUNS = 30;

    /** 검색어 → 유형. 흔한 식품명 / 중간 / 드문 식품명 / 제조사명 / 극소 / 결과 없음. */
    private static final Map<String, String> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put("라면", "결과 많음");
        KEYWORDS.put("누리제과", "제조사명");
        KEYWORDS.put("떡볶이", "결과 중간");
        KEYWORDS.put("판나코타", "결과 적음");
        KEYWORDS.put("한정판", "결과 극소");
        KEYWORDS.put("트러플리조또", "결과 없음");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private OfficialProcessedFoodRepository repository;
    @Autowired
    private OfficialFoodSearcher searcher;
    @Autowired
    private OfficialFoodBulkInsertRepository bulkInsertRepository;
    @Autowired
    private OfficialFoodFullTextIndexInitializer fullTextIndexInitializer;

    @BeforeEach
    void seedIfNeeded() throws InterruptedException {
        waitForFullTextIndex();
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM official_processed_foods WHERE food_cd LIKE 'P%'", Long.class);
        if (count == null || count < OfficialFoodTestData.REAL_DATASET_SIZE) {
            // 운영처럼 "데이터가 이미 있는 테이블에 인덱스를 새로 만드는" 상황을 재현하려고 인덱스를 지우고 적재한다.
            jdbcTemplate.execute("ALTER TABLE official_processed_foods DROP INDEX ft_official_processed_food_nm_mfr");
            jdbcTemplate.execute("TRUNCATE TABLE official_processed_foods");
            long start = System.currentTimeMillis();
            for (int from = 0; from < OfficialFoodTestData.REAL_DATASET_SIZE; from += 5_000) {
                int size = Math.min(5_000, OfficialFoodTestData.REAL_DATASET_SIZE - from);
                bulkInsertRepository.insertProcessedFoods(OfficialFoodTestData.generate(from, size));
            }
            System.out.printf("[seed] %,d건 적재 %,dms%n", OfficialFoodTestData.REAL_DATASET_SIZE, System.currentTimeMillis() - start);

            start = System.currentTimeMillis();
            jdbcTemplate.execute("ALTER TABLE official_processed_foods "
                    + "ADD FULLTEXT INDEX ft_official_processed_food_nm_mfr (food_nm, mfr_nm) WITH PARSER ngram");
            System.out.printf("[seed] FULLTEXT(ngram) 인덱스 생성 %,dms%n", System.currentTimeMillis() - start);
        }
    }

    /** 앱 기동 시 비동기로 인덱스를 만든다 - 준비될 때까지 기다린다(최대 10분). */
    private void waitForFullTextIndex() throws InterruptedException {
        for (int i = 0; i < 600 && !fullTextIndexInitializer.isReady(OfficialFoodFullTextIndexInitializer.PROCESSED_FOOD_TABLE); i++) {
            Thread.sleep(1_000);
        }
        assertThat(fullTextIndexInitializer.isReady(OfficialFoodFullTextIndexInitializer.PROCESSED_FOOD_TABLE)).isTrue();
    }

    @Test
    void 검색방식별_응답시간_비교() {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM official_processed_foods", Long.class);
        System.out.printf("%n=== 가공식품 부분검색 응답시간 (총 %,d건, LIMIT %d, 검색어당 %d회, 단위 ms) ===%n", total, PAGE_SIZE, RUNS);
        System.out.printf("%-8s %-6s %9s | %8s %8s | %8s %8s | %8s %8s%n",
                "검색어", "유형", "매칭건수", "LIKE avg", "p95", "FT avg", "p95", "하이브리드", "p95");

        double likeWorstP95 = 0;
        double fullTextWorstP95 = 0;
        double hybridWorstP95 = 0;
        double likeSum = 0;
        double fullTextSum = 0;
        double hybridSum = 0;
        for (Map.Entry<String, String> entry : KEYWORDS.entrySet()) {
            String keyword = entry.getKey();
            String phrase = FullTextKeyword.toPhrase(keyword).orElseThrow();

            // 1) 결과가 같은지 먼저 확인
            Long likeCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM official_processed_foods WHERE food_nm LIKE ? OR mfr_nm LIKE ?",
                    Long.class, "%" + keyword + "%", "%" + keyword + "%");
            Long fullTextCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM official_processed_foods WHERE MATCH(food_nm, mfr_nm) AGAINST(? IN BOOLEAN MODE)",
                    Long.class, phrase);
            assertThat(fullTextCount).as("'%s' FULLTEXT 매칭 건수가 LIKE와 같아야 함", keyword).isEqualTo(likeCount);
            List<OfficialProcessedFood> hybridResult = searcher.searchProcessedFoods(keyword, PAGE_SIZE);
            assertThat(hybridResult).hasSize((int) Math.min(PAGE_SIZE, likeCount));
            assertThat(hybridResult).allMatch(food -> food.getFoodNm().contains(keyword)
                    || (food.getMfrNm() != null && food.getMfrNm().contains(keyword)));

            // 2) 응답시간 측정
            Timing like = measure(WARMUP, RUNS,
                    () -> repository.findByFoodNmContainingOrMfrNmContaining(keyword, keyword, PageRequest.of(0, PAGE_SIZE)));
            Timing fullText = measure(WARMUP, RUNS, () -> repository.searchByFullText(phrase, PAGE_SIZE));
            Timing hybrid = measure(WARMUP, RUNS, () -> searcher.searchProcessedFoods(keyword, PAGE_SIZE));

            likeWorstP95 = Math.max(likeWorstP95, like.p95());
            fullTextWorstP95 = Math.max(fullTextWorstP95, fullText.p95());
            hybridWorstP95 = Math.max(hybridWorstP95, hybrid.p95());
            likeSum += like.avg();
            fullTextSum += fullText.avg();
            hybridSum += hybrid.avg();

            System.out.printf("%-8s %-6s %,9d | %8.1f %8.1f | %8.1f %8.1f | %8.1f %8.1f%n",
                    keyword, entry.getValue(), likeCount,
                    like.avg(), like.p95(), fullText.avg(), fullText.p95(), hybrid.avg(), hybrid.p95());
        }
        int n = KEYWORDS.size();
        System.out.printf("검색어 평균 : LIKE %.1fms / FULLTEXT 단독 %.1fms / 하이브리드 %.1fms%n",
                likeSum / n, fullTextSum / n, hybridSum / n);
        System.out.printf("최악 p95   : LIKE %.1fms / FULLTEXT 단독 %.1fms / 하이브리드 %.1fms%n",
                likeWorstP95, fullTextWorstP95, hybridWorstP95);
        System.out.printf("하이브리드 개선율(LIKE 대비): 평균 %.1f%% 단축, 최악 p95 %.1f%% 단축%n",
                (1 - hybridSum / likeSum) * 100, (1 - hybridWorstP95 / likeWorstP95) * 100);

        printExplain("LIKE", "SELECT * FROM official_processed_foods WHERE food_nm LIKE '%한정판%' OR mfr_nm LIKE '%한정판%' LIMIT 20");
        printExplain("FULLTEXT", "SELECT * FROM official_processed_foods WHERE MATCH(food_nm, mfr_nm) AGAINST('\"한정판\"' IN BOOLEAN MODE) LIMIT 20");

        assertThat(hybridWorstP95).isLessThan(likeWorstP95);
        assertThat(hybridWorstP95).isLessThan(fullTextWorstP95);
    }

    private void printExplain(String label, String sql) {
        Map<String, Object> plan = jdbcTemplate.queryForMap("EXPLAIN " + sql);
        System.out.printf("[EXPLAIN %s] type=%s, key=%s, rows=%s%n", label, plan.get("type"), plan.get("key"), plan.get("rows"));
    }
}
