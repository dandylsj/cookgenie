package com.cookgenie.domain.meallog.dto;

import com.cookgenie.domain.meallog.entity.NutritionGoal;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;

@Getter
public class NutritionGoalResponse {

    private final Long id;
    private final Integer targetCalories;
    private final BigDecimal targetCarbohydrateG;
    private final BigDecimal targetProteinG;
    private final BigDecimal targetFatG;
    private final BigDecimal weightKg;
    private final BigDecimal heightCm;
    private final String activityLevel;
    private final LocalDate effectiveDate;

    public NutritionGoalResponse(NutritionGoal goal) {
        this.id = goal.getId();
        this.targetCalories = goal.getTargetCalories();
        this.targetCarbohydrateG = goal.getTargetCarbohydrateG();
        this.targetProteinG = goal.getTargetProteinG();
        this.targetFatG = goal.getTargetFatG();
        this.weightKg = goal.getWeightKg();
        this.heightCm = goal.getHeightCm();
        this.activityLevel = goal.getActivityLevel();
        this.effectiveDate = goal.getEffectiveDate();
    }
}
