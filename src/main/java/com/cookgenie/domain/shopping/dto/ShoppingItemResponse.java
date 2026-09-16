package com.cookgenie.domain.shopping.dto;

import com.cookgenie.domain.shopping.entity.ShoppingItem;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class ShoppingItemResponse {

    private final Long id;
    private final String name;
    private final boolean checked;
    private final LocalDateTime createdAt;

    public ShoppingItemResponse(ShoppingItem item) {
        this.id = item.getId();
        this.name = item.getName();
        this.checked = item.isChecked();
        this.createdAt = item.getCreatedAt();
    }
}
