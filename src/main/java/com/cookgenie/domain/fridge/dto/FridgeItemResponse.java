package com.cookgenie.domain.fridge.dto;

import com.cookgenie.domain.fridge.entity.FridgeItem;
import com.cookgenie.domain.fridge.entity.StorageLocation;
import java.math.BigDecimal;
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
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public FridgeItemResponse(FridgeItem item) {
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
    }
}
