package com.cookgenie.domain.ingredient.external;

import java.math.BigDecimal;

/**
 * 식약처 가공식품 공공데이터 검색 결과 한 건(100g/100ml 기준으로 정규화된 값).
 * foodNm은 브랜드명이 안 들어있는 경우가 많아서, 같은 이름이 여러 제조사(mfrNm)로 나올 수 있다 —
 * 화면에는 foodNm과 mfrNm을 같이 보여줘서 사용자가 정확한 제품을 직접 고르게 한다.
 */
public record OfficialFoodCandidate(
        String foodCd,
        String foodNm,
        String mfrNm,
        String referenceUnit,
        Integer calories,
        BigDecimal carbohydrateG,
        BigDecimal proteinG,
        BigDecimal fatG,
        BigDecimal sugarG,
        BigDecimal sodiumMg,
        BigDecimal fiberG
) {
    public NutritionEstimate toEstimate() {
        return new NutritionEstimate(true, referenceUnit, calories, carbohydrateG, proteinG, fatG, sugarG, sodiumMg, fiberG);
    }
}
