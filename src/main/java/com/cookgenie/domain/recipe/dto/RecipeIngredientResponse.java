package com.cookgenie.domain.recipe.dto;

import com.cookgenie.domain.recipe.entity.RecipeIngredient;
import java.math.BigDecimal;
import java.util.Set;
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
    private final Boolean inFridge;

    public RecipeIngredientResponse(RecipeIngredient recipeIngredient) {
        this(recipeIngredient, null);
    }

    /**
     * fridgeIngredientNames가 주어지면(소문자/trim 정규화된 이름 집합) 이 재료가 그 냉장고에 있는지(inFridge)를 함께 계산한다.
     * null이면 냉장고 맥락이 없다는 뜻이라 inFridge도 null로 내려간다(장바구니 담기 버튼 노출 여부는 프론트에서 null이 아닐 때만 판단).
     */
    public RecipeIngredientResponse(RecipeIngredient recipeIngredient, Set<String> fridgeIngredientNames) {
        this.id = recipeIngredient.getId();
        this.ingredientId = recipeIngredient.getIngredient() != null ? recipeIngredient.getIngredient().getId() : null;
        this.ingredientNameText = recipeIngredient.getIngredientNameText();
        this.quantityText = recipeIngredient.getQuantityText();
        this.quantityValue = recipeIngredient.getQuantityValue();
        this.unit = recipeIngredient.getUnit();
        this.matched = recipeIngredient.getIngredient() != null;

        String resolvedName = this.matched ? recipeIngredient.getIngredient().getName() : this.ingredientNameText;
        this.inFridge = fridgeIngredientNames == null
                ? null
                : fridgeIngredientNames.contains(resolvedName.trim().toLowerCase());
    }
}
