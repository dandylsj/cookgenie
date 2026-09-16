package com.cookgenie.domain.ingredient.external;

import java.math.BigDecimal;

/**
 * 공공데이터포털 "전국통합식품영양성분정보(원재료성식품)표준데이터" CSV의 한 행에서 필요한 값만 뽑아낸 것.
 * 이 표준데이터는 이름을 밑줄로 조리상태/품종별로 쪼개는 대신, 대표식품코드/대표식품명(품종·조리상태를 뗀
 * 진짜 재료명)과 식품세분류명(조리상태: 생것/구운것 등)을 이미 별도 컬럼으로 구조화해서 제공한다.
 */
public record RawFoodCsvRow(
        String representativeCode,
        String representativeName,
        String categoryName,
        String stateName,
        Integer referenceAmount,
        String referenceUnit,
        Integer calories,
        BigDecimal carbohydrateG,
        BigDecimal proteinG,
        BigDecimal fatG,
        BigDecimal sugarG,
        BigDecimal sodiumMg,
        BigDecimal fiberG
) {
}
