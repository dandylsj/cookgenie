package com.cookgenie.domain.fridge.dto;

import com.cookgenie.domain.fridge.entity.Fridge;
import com.cookgenie.domain.fridge.entity.FridgeRole;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class FridgeResponse {

    private final Long id;
    private final String name;
    private final Long ownerId;
    private final String ownerNickname;
    private final FridgeRole myRole;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public FridgeResponse(Fridge fridge, FridgeRole myRole) {
        this.id = fridge.getId();
        this.name = fridge.getName();
        this.ownerId = fridge.getOwner().getId();
        this.ownerNickname = fridge.getOwner().getNickname();
        this.myRole = myRole;
        this.createdAt = fridge.getCreatedAt();
        this.updatedAt = fridge.getUpdatedAt();
    }
}
