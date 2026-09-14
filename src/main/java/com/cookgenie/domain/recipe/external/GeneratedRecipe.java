package com.cookgenie.domain.recipe.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

/** Claude가 생성한 레시피. 1인분(servingSize) 기준 영양정보. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeneratedRecipe(
        String title,
        String cookingType,
        Integer servingSize,
        Integer caloriesPerServing,
        BigDecimal carbohydrateG,
        BigDecimal proteinG,
        BigDecimal fatG,
        List<GeneratedIngredient> ingredients,
        List<String> tags,
        List<String> instructions
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GeneratedIngredient(String name, String quantityText, BigDecimal quantityValue, String unit) {
    }
}
