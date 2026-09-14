package com.cookgenie.domain.ingredient.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/** LLM이 추정한 식재료 100g(또는 100ml) 기준 평균 영양정보. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NutritionEstimate(
        boolean isValidFood,
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
