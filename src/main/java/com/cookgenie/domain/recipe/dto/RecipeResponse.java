package com.cookgenie.domain.recipe.dto;

import com.cookgenie.domain.recipe.entity.Recipe;
import com.cookgenie.domain.recipe.entity.RecipeIngredient;
import com.cookgenie.domain.recipe.entity.RecipeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.Getter;

@Getter
public class RecipeResponse {

    private final Long id;
    private final String title;
    private final RecipeType recipeType;
    private final String cookingType;
    private final List<String> instructions;
    private final String sourceUrl;
    private final String authorNickname;
    private final Integer servingSize;
    private final Integer caloriesPerServing;
    private final BigDecimal carbohydrateG;
    private final BigDecimal proteinG;
    private final BigDecimal fatG;
    private final Integer viewCount;
    private final Integer likeCount;
    private final Integer saveCount;
    private final List<String> tags;
    private final List<RecipeIngredientResponse> ingredients;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public RecipeResponse(Recipe recipe, List<RecipeIngredient> ingredients, List<String> tags) {
        this(recipe, ingredients, tags, null);
    }

    /** fridgeIngredientNames를 주면 각 재료의 inFridge(그 냉장고에 있는지)를 함께 계산해 내려준다. */
    public RecipeResponse(Recipe recipe, List<RecipeIngredient> ingredients, List<String> tags,
                           Set<String> fridgeIngredientNames) {
        this.id = recipe.getId();
        this.title = recipe.getTitle();
        this.recipeType = recipe.getRecipeType();
        this.cookingType = recipe.getCookingType();
        this.instructions = recipe.getInstructions() == null || recipe.getInstructions().isBlank()
                ? List.of()
                : List.of(recipe.getInstructions().split("\n"));
        this.sourceUrl = recipe.getSourceUrl();
        this.authorNickname = recipe.getAuthorNickname();
        this.servingSize = recipe.getServingSize();
        this.caloriesPerServing = recipe.getCaloriesPerServing();
        this.carbohydrateG = recipe.getCarbohydrateG();
        this.proteinG = recipe.getProteinG();
        this.fatG = recipe.getFatG();
        this.viewCount = recipe.getViewCount();
        this.likeCount = recipe.getLikeCount();
        this.saveCount = recipe.getSaveCount();
        this.tags = tags;
        this.ingredients = ingredients.stream()
                .map(ri -> new RecipeIngredientResponse(ri, fridgeIngredientNames))
                .toList();
        this.createdAt = recipe.getCreatedAt();
        this.updatedAt = recipe.getUpdatedAt();
    }
}
