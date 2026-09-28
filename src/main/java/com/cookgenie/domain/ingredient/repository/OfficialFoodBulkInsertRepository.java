package com.cookgenie.domain.ingredient.repository;

import com.cookgenie.domain.ingredient.external.OfficialDishCandidate;
import com.cookgenie.domain.ingredient.external.OfficialFoodCandidate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 식약처 공공데이터 미러 테이블(가공식품 약 59만 건, 음식) 전체 동기화용 JDBC 배치 INSERT.
 *
 * <p>{@code OfficialProcessedFood}/{@code OfficialDish}는 PK가 {@code GenerationType.IDENTITY}라서
 * Hibernate가 INSERT 배치를 아예 꺼버린다 - {@code saveAll(1000건)}을 해도 실제로는 INSERT 문이 1000번,
 * DB 왕복도 1000번 나간다(59만 건이면 59만 번). 그래서 이 대량 적재 경로만 JPA를 거치지 않고
 * {@link JdbcTemplate#batchUpdate}로 묶어서 보내고, JDBC URL의 {@code rewriteBatchedStatements=true}로
 * MySQL 드라이버가 이걸 multi-row INSERT 한 문장으로 다시 써서 보내게 한다.
 */
@Repository
@RequiredArgsConstructor
public class OfficialFoodBulkInsertRepository {

    private static final String INSERT_PROCESSED_FOOD = "INSERT INTO official_processed_foods "
            + "(food_cd, food_nm, mfr_nm, reference_unit, calories, carbohydrate_g, protein_g, fat_g, sugar_g, sodium_mg, fiber_g) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_DISH = "INSERT INTO official_dishes "
            + "(food_cd, food_nm, rest_nm, reference_unit, calories, carbohydrate_g, protein_g, fat_g, sugar_g, sodium_mg, fiber_g) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    public void insertProcessedFoods(List<OfficialFoodCandidate> items) {
        if (items.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(INSERT_PROCESSED_FOOD, items, items.size(), (ps, item) -> {
            ps.setString(1, item.foodCd());
            ps.setString(2, item.foodNm());
            ps.setString(3, item.mfrNm());
            ps.setString(4, item.referenceUnit());
            ps.setObject(5, item.calories());
            ps.setBigDecimal(6, item.carbohydrateG());
            ps.setBigDecimal(7, item.proteinG());
            ps.setBigDecimal(8, item.fatG());
            ps.setBigDecimal(9, item.sugarG());
            ps.setBigDecimal(10, item.sodiumMg());
            ps.setBigDecimal(11, item.fiberG());
        });
    }

    public void insertDishes(List<OfficialDishCandidate> items) {
        if (items.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(INSERT_DISH, items, items.size(), (ps, item) -> {
            ps.setString(1, item.foodCd());
            ps.setString(2, item.foodNm());
            ps.setString(3, item.restNm());
            ps.setString(4, item.referenceUnit());
            ps.setObject(5, item.calories());
            ps.setBigDecimal(6, item.carbohydrateG());
            ps.setBigDecimal(7, item.proteinG());
            ps.setBigDecimal(8, item.fatG());
            ps.setBigDecimal(9, item.sugarG());
            ps.setBigDecimal(10, item.sodiumMg());
            ps.setBigDecimal(11, item.fiberG());
        });
    }
}
