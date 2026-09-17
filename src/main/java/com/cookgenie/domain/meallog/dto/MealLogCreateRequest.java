package com.cookgenie.domain.meallog.dto;

import com.cookgenie.domain.meallog.entity.MealLogType;
import com.cookgenie.domain.meallog.entity.MealType;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 식단 기록 추가 요청. logType이 RECIPE면 recipeId(+servings)로, FREEFORM이면 items로 기록한다.
 * servings 생략 시 1로 처리한다.
 */
@Getter
@NoArgsConstructor
public class MealLogCreateRequest {

    @NotNull(message = "날짜는 필수입니다.")
    private LocalDate mealDate;

    @NotNull(message = "식사 종류(아침/점심/저녁/간식)는 필수입니다.")
    private MealType mealType;

    @NotNull(message = "기록 방식(레시피/직접입력)은 필수입니다.")
    private MealLogType logType;

    private Long recipeId;

    private BigDecimal servings;

    private List<MealLogItemRequest> items;

    public BigDecimal getServingsOrDefault() {
        return servings != null ? servings : BigDecimal.ONE;
    }
}
