package com.cookgenie.domain.ingredient.dto;

import com.cookgenie.domain.ingredient.entity.Category;
import lombok.Getter;

@Getter
public class CategoryResponse {

    private final Long id;
    private final String name;
    private final String iconUrl;

    public CategoryResponse(Category category) {
        this.id = category.getId();
        this.name = category.getName();
        this.iconUrl = category.getIconUrl();
    }
}
