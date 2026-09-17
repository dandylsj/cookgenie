package com.cookgenie.domain.fridge.dto;

import com.cookgenie.domain.fridge.entity.FridgeMember;
import com.cookgenie.domain.fridge.entity.FridgeRole;
import java.time.LocalDateTime;
import lombok.Getter;

/** GET /fridges/{fridgeId}/members 응답 한 건. */
@Getter
public class FridgeMemberResponse {

    private final Long userId;
    private final String nickname;
    private final FridgeRole role;
    private final LocalDateTime joinedAt;

    public FridgeMemberResponse(FridgeMember member) {
        this.userId = member.getUser().getId();
        this.nickname = member.getUser().getNickname();
        this.role = member.getRole();
        this.joinedAt = member.getJoinedAt();
    }
}
