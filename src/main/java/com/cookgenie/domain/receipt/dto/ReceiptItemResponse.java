package com.cookgenie.domain.receipt.dto;

import com.cookgenie.domain.receipt.external.ReceiptScanResult;
import java.math.BigDecimal;
import lombok.Getter;

@Getter
public class ReceiptItemResponse {

    private final String name;
    private final String quantityText;
    private final BigDecimal quantityValue;
    private final String unit;
    private final String categoryNameGuess;
    private final Long matchedIngredientId;
    private final Long matchedCategoryId;

    public ReceiptItemResponse(ReceiptScanResult.ScannedItem item, Long matchedIngredientId, Long matchedCategoryId) {
        this.name = item.name();
        this.quantityText = item.quantityText();
        this.quantityValue = item.quantityValue();
        this.unit = item.unit();
        this.categoryNameGuess = item.categoryNameGuess();
        this.matchedIngredientId = matchedIngredientId;
        this.matchedCategoryId = matchedCategoryId;
    }
}
