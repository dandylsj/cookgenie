package com.cookgenie.domain.shopping.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ShoppingItemAddRequest {

    @NotBlank(message = "재료 이름은 필수입니다.")
    private String name;
}
