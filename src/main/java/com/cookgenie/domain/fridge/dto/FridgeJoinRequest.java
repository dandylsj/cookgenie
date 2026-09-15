package com.cookgenie.domain.fridge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FridgeJoinRequest {

    @NotBlank(message = "초대코드는 필수입니다.")
    @Pattern(regexp = "\\d{4}", message = "초대코드는 4자리 숫자입니다.")
    private String inviteCode;
}
