package com.cookgenie.domain.fridge.dto;

import com.cookgenie.domain.fridge.entity.StorageLocation;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FridgeItemCreateRequest {

    @NotNull(message = "식재료는 필수입니다.")
    private Long ingredientId;

    @NotNull(message = "수량은 필수입니다.")
    private BigDecimal quantity;

    @NotNull(message = "단위는 필수입니다.")
    private String unit;

    @NotNull(message = "보관 위치는 필수입니다.")
    private StorageLocation storageLocation;

    @NotNull(message = "구매일은 필수입니다.")
    private LocalDate purchasedAt;

    private LocalDate expiryDate;

    private String memo;
}
