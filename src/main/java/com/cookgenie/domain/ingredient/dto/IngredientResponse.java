package com.cookgenie.domain.ingredient.dto;

import com.cookgenie.domain.ingredient.entity.DataSource;
import com.cookgenie.domain.ingredient.entity.Ingredient;
import com.cookgenie.domain.ingredient.entity.IngredientType;
import com.cookgenie.domain.ingredient.entity.NutritionInfo;
import java.math.BigDecimal;
import lombok.Getter;

@Getter
public class IngredientResponse {

    private final Long id;
    private final String name;
    private final Long categoryId;
    private final String categoryName;
    private final IngredientType ingredientType;
    private final String defaultUnit;
    private final DataSource dataSource;
    private final Boolean isVerified;
    private final Integer referenceAmount;
    private final String referenceUnit;
    private final Integer referenceCalories;
    private final BigDecimal referenceCarbohydrateG;
    private final BigDecimal referenceProteinG;
    private final BigDecimal referenceFatG;

    /** @param nutritionInfo 영양정보가 없는 재료(수동 등록 등)면 null. 이 경우 reference* 필드는 전부 null. */
    public IngredientResponse(Ingredient ingredient, NutritionInfo nutritionInfo) {
        this.id = ingredient.getId();
        this.name = ingredient.getName();
        this.categoryId = ingredient.getCategory() != null ? ingredient.getCategory().getId() : null;
        this.categoryName = ingredient.getCategory() != null ? ingredient.getCategory().getName() : null;
        this.ingredientType = ingredient.getIngredientType();
        this.defaultUnit = ingredient.getDefaultUnit();
        this.dataSource = ingredient.getDataSource();
        this.isVerified = ingredient.getIsVerified();
        this.referenceAmount = nutritionInfo != null ? nutritionInfo.getReferenceAmount() : null;
        this.referenceUnit = nutritionInfo != null ? nutritionInfo.getReferenceUnit() : null;
        this.referenceCalories = nutritionInfo != null ? nutritionInfo.getCalories() : null;
        this.referenceCarbohydrateG = nutritionInfo != null ? nutritionInfo.getCarbohydrateG() : null;
        this.referenceProteinG = nutritionInfo != null ? nutritionInfo.getProteinG() : null;
        this.referenceFatG = nutritionInfo != null ? nutritionInfo.getFatG() : null;
    }
}
