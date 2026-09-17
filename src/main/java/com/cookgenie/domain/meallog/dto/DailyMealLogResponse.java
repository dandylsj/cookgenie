package com.cookgenie.domain.meallog.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import lombok.Getter;

/** GET /meal-logs?date= 응답. 그날 6개 식사 슬롯 전체 + 총 섭취량/목표량(목표 미설정이면 target*는 전부 null). */
@Getter
public class DailyMealLogResponse {

    private final LocalDate date;
    private final int totalCalories;
    private final BigDecimal totalCarbohydrateG;
    private final BigDecimal totalProteinG;
    private final BigDecimal totalFatG;
    private final Integer targetCalories;
    private final BigDecimal targetCarbohydrateG;
    private final BigDecimal targetProteinG;
    private final BigDecimal targetFatG;
    private final List<MealSlotResponse> meals;

    public DailyMealLogResponse(LocalDate date, List<MealSlotResponse> meals, NutritionGoalResponse goal) {
        this.date = date;
        this.meals = meals;
        this.totalCalories = meals.stream().mapToInt(MealSlotResponse::getTotalCalories).sum();
        this.totalCarbohydrateG = sum(meals, MealSlotResponse::getTotalCarbohydrateG);
        this.totalProteinG = sum(meals, MealSlotResponse::getTotalProteinG);
        this.totalFatG = sum(meals, MealSlotResponse::getTotalFatG);
        this.targetCalories = goal != null ? goal.getTargetCalories() : null;
        this.targetCarbohydrateG = goal != null ? goal.getTargetCarbohydrateG() : null;
        this.targetProteinG = goal != null ? goal.getTargetProteinG() : null;
        this.targetFatG = goal != null ? goal.getTargetFatG() : null;
    }

    private static BigDecimal sum(List<MealSlotResponse> meals, Function<MealSlotResponse, BigDecimal> extractor) {
        return meals.stream()
                .map(extractor)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
