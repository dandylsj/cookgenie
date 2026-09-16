package com.cookgenie.domain.shopping.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ShoppingItemCheckRequest {

    @NotNull(message = "checked 값은 필수입니다.")
    private Boolean checked;
}
