package com.cookgenie.domain.meallog.dto;

import com.cookgenie.domain.meallog.entity.MealType;
import lombok.Getter;

/** 달력 한 칸에 표시할 식사 한 줄(예: "점심 - 돼지목살구이"). label은 레시피면 제목, 직접입력이면 재료명(+N개). */
@Getter
public class MealSummaryItem {

    private final MealType mealType;
    private final String label;

    public MealSummaryItem(MealType mealType, String label) {
        this.mealType = mealType;
        this.label = label;
    }
}
