package com.cookgenie.domain.fridge.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FridgeInviteCodeResponse {

    private String inviteCode;
    private LocalDateTime expiryDate;
}
