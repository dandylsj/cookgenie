package com.cookgenie.domain.meallog.dto;

import java.time.LocalDate;
import java.util.List;
import lombok.Getter;

/** GET /meal-logs/calendar 응답의 날짜 한 칸. */
@Getter
public class DailyMealSummaryResponse {

    private final LocalDate date;
    private final int totalCalories;
    private final List<MealSummaryItem> meals;

    public DailyMealSummaryResponse(LocalDate date, int totalCalories, List<MealSummaryItem> meals) {
        this.date = date;
        this.totalCalories = totalCalories;
        this.meals = meals;
    }
}
