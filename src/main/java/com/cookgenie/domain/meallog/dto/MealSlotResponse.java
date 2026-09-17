package com.cookgenie.domain.meallog.dto;

import com.cookgenie.domain.meallog.entity.MealType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Function;
import lombok.Getter;

/** 하루 상세 화면의 식사 슬롯 하나(예: 점심). 그 슬롯에 기록된 항목들과 슬롯 합계를 담는다. */
@Getter
public class MealSlotResponse {

    private final MealType mealType;
    private final int totalCalories;
    private final BigDecimal totalCarbohydrateG;
    private final BigDecimal totalProteinG;
    private final BigDecimal totalFatG;
    private final List<MealLogResponse> logs;

    public MealSlotResponse(MealType mealType, List<MealLogResponse> logs) {
        this.mealType = mealType;
        this.logs = logs;
        this.totalCalories = logs.stream().mapToInt(log -> log.getTotalCalories() != null ? log.getTotalCalories() : 0).sum();
        this.totalCarbohydrateG = sum(logs, MealLogResponse::getTotalCarbohydrateG);
        this.totalProteinG = sum(logs, MealLogResponse::getTotalProteinG);
        this.totalFatG = sum(logs, MealLogResponse::getTotalFatG);
    }

    private static BigDecimal sum(List<MealLogResponse> logs, Function<MealLogResponse, BigDecimal> extractor) {
        return logs.stream()
                .map(extractor)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(1, RoundingMode.HALF_UP);
    }
}
