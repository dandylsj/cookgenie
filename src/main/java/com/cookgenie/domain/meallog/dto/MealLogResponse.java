package com.cookgenie.domain.meallog.dto;

import com.cookgenie.domain.meallog.entity.MealLog;
import com.cookgenie.domain.meallog.entity.MealLogItem;
import com.cookgenie.domain.meallog.entity.MealLogType;
import com.cookgenie.domain.meallog.entity.MealType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;

@Getter
public class MealLogResponse {

    private final Long id;
    private final LocalDate mealDate;
    private final MealType mealType;
    private final MealLogType logType;
    private final Long recipeId;
    private final String recipeTitle;
    private final BigDecimal servings;
    private final Integer totalCalories;
    private final BigDecimal totalCarbohydrateG;
    private final BigDecimal totalProteinG;
    private final BigDecimal totalFatG;
    private final List<MealLogItemResponse> items;
    private final LocalDateTime createdAt;

    public MealLogResponse(MealLog mealLog, List<MealLogItem> items) {
        this.id = mealLog.getId();
        this.mealDate = mealLog.getMealDate();
        this.mealType = mealLog.getMealType();
        this.logType = mealLog.getLogType();
        this.recipeId = mealLog.getRecipe() != null ? mealLog.getRecipe().getId() : null;
        this.recipeTitle = mealLog.getRecipe() != null ? mealLog.getRecipe().getTitle() : null;
        this.servings = mealLog.getServings();
        this.totalCalories = mealLog.getTotalCalories();
        this.totalCarbohydrateG = mealLog.getTotalCarbohydrateG();
        this.totalProteinG = mealLog.getTotalProteinG();
        this.totalFatG = mealLog.getTotalFatG();
        this.items = items.stream().map(MealLogItemResponse::new).toList();
        this.createdAt = mealLog.getCreatedAt();
    }
}
