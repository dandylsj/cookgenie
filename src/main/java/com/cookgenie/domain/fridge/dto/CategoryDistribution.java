package com.cookgenie.domain.fridge.dto;

/** 냉장고 재료의 카테고리별 개수. 카테고리가 없는 재료는 name="미분류"로 묶인다. */
public record CategoryDistribution(String categoryName, long count) {
}
