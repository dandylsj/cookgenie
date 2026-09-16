package com.cookgenie.domain.receipt.dto;

import java.util.List;
import lombok.Getter;

@Getter
public class ReceiptScanResponse {

    private final List<ReceiptItemResponse> items;

    public ReceiptScanResponse(List<ReceiptItemResponse> items) {
        this.items = items;
    }
}
