package com.cookgenie.domain.meallog.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 목표 설정 요청. effectiveDate 생략 시 오늘부터 적용된다. */
@Getter
@NoArgsConstructor
public class NutritionGoalRequest {

    private Integer targetCalories;
    private BigDecimal targetCarbohydrateG;
    private BigDecimal targetProteinG;
    private BigDecimal targetFatG;
    private BigDecimal weightKg;
    private BigDecimal heightCm;
    private String activityLevel;
    private LocalDate effectiveDate;

    public LocalDate getEffectiveDateOrToday() {
        return effectiveDate != null ? effectiveDate : LocalDate.now();
    }
}
