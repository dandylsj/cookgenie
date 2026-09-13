package com.cookgenie.domain.ingredient.dto;

import com.cookgenie.domain.ingredient.entity.DataSource;
import com.cookgenie.domain.ingredient.entity.Ingredient;
import com.cookgenie.domain.ingredient.entity.IngredientType;
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

    public IngredientResponse(Ingredient ingredient) {
        this.id = ingredient.getId();
        this.name = ingredient.getName();
        this.categoryId = ingredient.getCategory() != null ? ingredient.getCategory().getId() : null;
        this.categoryName = ingredient.getCategory() != null ? ingredient.getCategory().getName() : null;
        this.ingredientType = ingredient.getIngredientType();
        this.defaultUnit = ingredient.getDefaultUnit();
        this.dataSource = ingredient.getDataSource();
        this.isVerified = ingredient.getIsVerified();
    }
}
