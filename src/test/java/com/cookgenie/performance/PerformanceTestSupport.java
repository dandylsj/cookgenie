package com.cookgenie.performance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 성능 측정 테스트 공통 설정.
 *
 * <p>실제 MySQL 8에서 돌려야 의미가 있어서(FULLTEXT ngram, rewriteBatchedStatements 등 H2로는 재현 불가)
 * 평소 {@code ./gradlew test}에서는 건너뛰고, {@code PERF_TEST=true} 환경변수를 줄 때만 실행된다.
 * 개발용 DB를 더럽히지 않도록 별도 스키마({@code cookgenie_perf}, 없으면 자동 생성)를 쓴다.
 *
 * <pre>
 * PERF_TEST=true PERF_DB_PASSWORD=비밀번호 ./gradlew test --tests "com.cookgenie.performance.*" -i
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "PERF_TEST", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://${PERF_DB_HOST:localhost}:${PERF_DB_PORT:3306}/cookgenie_perf"
                + "?createDatabaseIfNotExist=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
                + "&allowPublicKeyRetrieval=true&useSSL=false&rewriteBatchedStatements=true",
        "spring.datasource.username=${PERF_DB_USERNAME:root}",
        "spring.datasource.password=${PERF_DB_PASSWORD:root}",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.format_sql=false",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN",
        "JWT_SECRET_KEY=cGVyZm9ybWFuY2UtdGVzdC1zZWNyZXQta2V5LXBlcmZvcm1hbmNlLXRlc3Qtc2VjcmV0",
        "ANTHROPIC_API_KEY=dummy", "YOUTUBE_API_KEY=dummy",
        "COUPANG_ACCESS_KEY=dummy", "COUPANG_SECRET_KEY=dummy",
        "MFDS_PROCESSED_FOOD_API_KEY=dummy", "MFDS_DISH_API_KEY=dummy",
        "KAKAO_REST_API_KEY=dummy", "GOOGLE_CLIENT_ID=dummy", "GOOGLE_CLIENT_SECRET=dummy"
})
public abstract class PerformanceTestSupport {

    /** warmup회 버린 뒤 runs회 실행해서 걸린 시간(ms, 소수점) 목록을 돌려준다. */
    protected static Timing measure(int warmup, int runs, Supplier<?> action) {
        for (int i = 0; i < warmup; i++) {
            action.get();
        }
        List<Double> millis = new ArrayList<>();
        for (int i = 0; i < runs; i++) {
            long start = System.nanoTime();
            action.get();
            millis.add((System.nanoTime() - start) / 1_000_000.0);
        }
        return new Timing(millis);
    }

    protected record Timing(List<Double> millis) {
        public double avg() {
            return millis.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        }

        public double p95() {
            List<Double> sorted = new ArrayList<>(millis);
            Collections.sort(sorted);
            return sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1);
        }
    }
}
