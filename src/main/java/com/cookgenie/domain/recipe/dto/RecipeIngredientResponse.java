package com.cookgenie.domain.recipe.dto;

import com.cookgenie.domain.recipe.entity.RecipeIngredient;
import java.math.BigDecimal;
import lombok.Getter;

@Getter
public class RecipeIngredientResponse {

    private final Long id;
    private final Long ingredientId;
    private final String ingredientNameText;
    private final String quantityText;
    private final BigDecimal quantityValue;
    private final String unit;
    private final boolean matched;

    public RecipeIngredientResponse(RecipeIngredient recipeIngredient) {
        this.id = recipeIngredient.getId();
        this.ingredientId = recipeIngredient.getIngredient() != null ? recipeIngredient.getIngredient().getId() : null;
        this.ingredientNameText = recipeIngredient.getIngredientNameText();
        this.quantityText = recipeIngredient.getQuantityText();
        this.quantityValue = recipeIngredient.getQuantityValue();
        this.unit = recipeIngredient.getUnit();
        this.matched = recipeIngredient.getIngredient() != null;
    }
}
