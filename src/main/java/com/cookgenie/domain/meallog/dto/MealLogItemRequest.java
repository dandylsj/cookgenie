package com.cookgenie.domain.meallog.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MealLogItemRequest {

    @NotNull(message = "식재료는 필수입니다.")
    private Long ingredientId;

    @NotNull(message = "수량은 필수입니다.")
    private BigDecimal quantity;

    @NotNull(message = "단위는 필수입니다.")
    private String unit;
}
