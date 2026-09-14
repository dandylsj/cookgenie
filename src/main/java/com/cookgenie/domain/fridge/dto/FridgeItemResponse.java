package com.cookgenie.domain.fridge.dto;

import com.cookgenie.domain.fridge.entity.FridgeItem;
import com.cookgenie.domain.fridge.entity.StorageLocation;
import com.cookgenie.domain.ingredient.entity.NutritionInfo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class FridgeItemResponse {

    private final Long id;
    private final Long fridgeId;
    private final Long ingredientId;
    private final String ingredientName;
    private final String categoryName;
    private final BigDecimal quantity;
    private final String unit;
    private final StorageLocation storageLocation;
    private final LocalDate purchasedAt;
    private final LocalDate expiryDate;
    private final String memo;
    private final Integer calories;
    private final BigDecimal carbohydrateG;
    private final BigDecimal proteinG;
    private final BigDecimal fatG;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    /**
     * @param nutritionInfo 재료의 100g/ml 기준 영양정보. FridgeItem.unit이 NutritionInfo.referenceUnit과 같을 때만
     *                      수량 비례로 환산한다(예: g 대 g). 단위가 다르거나(예: "개") 영양정보 자체가 없으면 null로 둔다.
     */
    public FridgeItemResponse(FridgeItem item, NutritionInfo nutritionInfo) {
        this.id = item.getId();
        this.fridgeId = item.getFridge().getId();
        this.ingredientId = item.getIngredient().getId();
        this.ingredientName = item.getIngredient().getName();
        this.categoryName = item.getIngredient().getCategory() != null
                ? item.getIngredient().getCategory().getName()
                : null;
        this.quantity = item.getQuantity();
        this.unit = item.getUnit();
        this.storageLocation = item.getStorageLocation();
        this.purchasedAt = item.getPurchasedAt();
        this.expiryDate = item.getExpiryDate();
        this.memo = item.getMemo();
        this.createdAt = item.getCreatedAt();
        this.updatedAt = item.getUpdatedAt();

        BigDecimal ratio = resolveRatio(item, nutritionInfo);
        this.calories = scaleInt(nutritionInfo != null ? nutritionInfo.getCalories() : null, ratio);
        this.carbohydrateG = scaleDecimal(nutritionInfo != null ? nutritionInfo.getCarbohydrateG() : null, ratio);
        this.proteinG = scaleDecimal(nutritionInfo != null ? nutritionInfo.getProteinG() : null, ratio);
        this.fatG = scaleDecimal(nutritionInfo != null ? nutritionInfo.getFatG() : null, ratio);
    }

    /** 단위가 일치할 때만(예: g-g) quantity/referenceAmount 비율을 계산하고, 아니면 null(계산 불가). */
    private static BigDecimal resolveRatio(FridgeItem item, NutritionInfo nutritionInfo) {
        if (nutritionInfo == null || nutritionInfo.getReferenceAmount() == null
                || nutritionInfo.getReferenceUnit() == null || item.getUnit() == null
                || !nutritionInfo.getReferenceUnit().equalsIgnoreCase(item.getUnit())) {
            return null;
        }
        return item.getQuantity().divide(BigDecimal.valueOf(nutritionInfo.getReferenceAmount()), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal scaleDecimal(BigDecimal perReference, BigDecimal ratio) {
        if (perReference == null || ratio == null) {
            return null;
        }
        return perReference.multiply(ratio).setScale(1, RoundingMode.HALF_UP);
    }

    private static Integer scaleInt(Integer perReference, BigDecimal ratio) {
        if (perReference == null || ratio == null) {
            return null;
        }
        return BigDecimal.valueOf(perReference).multiply(ratio).setScale(0, RoundingMode.HALF_UP).intValue();
    }
}
