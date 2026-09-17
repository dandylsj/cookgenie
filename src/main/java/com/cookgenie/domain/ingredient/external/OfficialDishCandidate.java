package com.cookgenie.domain.ingredient.external;

import java.math.BigDecimal;

/**
 * 식약처 "전국통합식품영양성분정보(음식)" 공공데이터 검색 결과 한 건(100g/100ml 기준으로 정규화된 값).
 * "음식"은 조리된 메뉴(짜장면, 김치찌개 등) 기준 데이터라 브랜드명이 아니라 restNm(제공 업체명)이 붙는다 -
 * {@link OfficialFoodCandidate}(가공식품, mfrNm=제조사)와 개념이 달라서 필드명을 분리해서 둔다.
 */
public record OfficialDishCandidate(
        String foodCd,
        String foodNm,
        String restNm,
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
