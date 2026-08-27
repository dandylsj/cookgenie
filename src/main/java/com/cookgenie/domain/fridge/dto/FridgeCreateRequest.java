package com.cookgenie.domain.fridge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FridgeCreateRequest {

    @NotBlank(message = "냉장고 이름은 필수입니다.")
    private String name;
}
