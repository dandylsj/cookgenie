package com.cookgenie.domain.auth.dto;

import com.cookgenie.domain.user.entity.User;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class UserInfoResponse {

    private final Long id;
    private final String loginId;
    private final String email;
    private final String nickname;
    private final String profileImageUrl;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final boolean isGuest;
    private final LocalDateTime guestExpiresAt;

    public UserInfoResponse(User user) {
        this.id = user.getId();
        this.loginId = user.getLoginId();
        this.email = user.getEmail();
        this.nickname = user.getNickname();
        this.profileImageUrl = user.getProfileImageUrl();
        this.createdAt = user.getCreatedAt();
        this.updatedAt = user.getUpdatedAt();
        this.isGuest = user.isGuest();
        this.guestExpiresAt = user.isGuest() ? user.getCreatedAt().plusDays(User.GUEST_RETENTION_DAYS) : null;
    }
}
