package com.cookgenie.domain.receipt.external;

import java.math.BigDecimal;
import java.util.List;

/** 영수증 이미지에서 Claude가 인식한 식재료 후보 목록. */
public record ReceiptScanResult(List<ScannedItem> items) {

    public record ScannedItem(
            String name,
            String quantityText,
            BigDecimal quantityValue,
            String unit,
            String categoryNameGuess
    ) {}
}
