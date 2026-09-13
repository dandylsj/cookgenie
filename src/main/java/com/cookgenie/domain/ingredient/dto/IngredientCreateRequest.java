package com.cookgenie.domain.ingredient.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class IngredientCreateRequest {

    @NotBlank(message = "식재료 이름은 필수입니다.")
    private String name;

    @NotBlank(message = "카테고리는 필수입니다.")
    private String categoryName;

    private String defaultUnit;
}
