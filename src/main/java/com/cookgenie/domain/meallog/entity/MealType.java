package com.cookgenie.domain.meallog.entity;

/** 하루 식사 슬롯. 화면에는 항상 이 순서(아침→점심→저녁→오전간식→오후간식→저녁간식)로 6개를 고정 표시한다. */
public enum MealType {
    BREAKFAST,
    LUNCH,
    DINNER,
    MORNING_SNACK,
    AFTERNOON_SNACK,
    EVENING_SNACK
}
