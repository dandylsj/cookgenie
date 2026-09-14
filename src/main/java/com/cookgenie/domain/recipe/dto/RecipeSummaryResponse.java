package com.cookgenie.domain.recipe.dto;

import com.cookgenie.domain.recipe.entity.Recipe;
import com.cookgenie.domain.recipe.entity.RecipeType;
import java.math.BigDecimal;
import lombok.Getter;

@Getter
public class RecipeSummaryResponse {

    private final Long id;
    private final String title;
    private final RecipeType recipeType;
    private final String cookingType;
    private final Integer servingSize;
    private final Integer caloriesPerServing;
    private final BigDecimal carbohydrateG;
    private final BigDecimal proteinG;
    private final BigDecimal fatG;
    private final Integer viewCount;
    private final Integer likeCount;
    private final Integer saveCount;
    /** 냉장고 재료 기반 추천일 때만 값이 있다. 일반 목록 조회에서는 null. */
    private final Integer matchedIngredientCount;
    private final Integer totalIngredientCount;

    public RecipeSummaryResponse(Recipe recipe) {
        this(recipe, null, null);
    }

    public RecipeSummaryResponse(Recipe recipe, Integer matchedIngredientCount, Integer totalIngredientCount) {
        this.id = recipe.getId();
        this.title = recipe.getTitle();
        this.recipeType = recipe.getRecipeType();
        this.cookingType = recipe.getCookingType();
        this.servingSize = recipe.getServingSize();
        this.caloriesPerServing = recipe.getCaloriesPerServing();
        this.carbohydrateG = recipe.getCarbohydrateG();
        this.proteinG = recipe.getProteinG();
        this.fatG = recipe.getFatG();
        this.viewCount = recipe.getViewCount();
        this.likeCount = recipe.getLikeCount();
        this.saveCount = recipe.getSaveCount();
        this.matchedIngredientCount = matchedIngredientCount;
        this.totalIngredientCount = totalIngredientCount;
    }
}
