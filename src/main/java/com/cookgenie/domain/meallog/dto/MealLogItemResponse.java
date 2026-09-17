package com.cookgenie.domain.meallog.dto;

import com.cookgenie.domain.meallog.entity.MealLogItem;
import java.math.BigDecimal;
import lombok.Getter;

@Getter
public class MealLogItemResponse {

    private final Long id;
    private final Long ingredientId;
    private final String ingredientName;
    private final BigDecimal quantity;
    private final String unit;
    private final Integer calories;
    private final BigDecimal carbohydrateG;
    private final BigDecimal proteinG;
    private final BigDecimal fatG;

    public MealLogItemResponse(MealLogItem item) {
        this.id = item.getId();
        this.ingredientId = item.getIngredient() != null ? item.getIngredient().getId() : null;
        this.ingredientName = item.getIngredient() != null ? item.getIngredient().getName() : null;
        this.quantity = item.getQuantity();
        this.unit = item.getUnit();
        this.calories = item.getCalories();
        this.carbohydrateG = item.getCarbohydrateG();
        this.proteinG = item.getProteinG();
        this.fatG = item.getFatG();
    }
}
