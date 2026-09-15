package com.cookgenie.domain.ingredient.dto;

/** 카테고리별 추천 재료 이름. 실제 등록 전이라 ingredientId는 없고, 이 이름 그대로 POST /ingredients에 보내면 등록된다. */
public record IngredientSuggestionResponse(String name) {
}
