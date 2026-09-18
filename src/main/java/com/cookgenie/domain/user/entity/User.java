package com.cookgenie.domain.user.entity;

import com.cookgenie.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User extends BaseTimeEntity {

    /** 회원가입 없이 바로 시작하는 게스트 계정의 provider 값. */
    public static final String GUEST_PROVIDER = "GUEST";

    /** 카카오 소셜 로그인 계정의 provider 값. */
    public static final String KAKAO_PROVIDER = "KAKAO";

    /** 게스트 계정을 정식 회원으로 전환하지 않았을 때 보관되는 기간(일). GuestCleanupScheduler가 이 값으로 삭제 대상을 정한다. */
    public static final int GUEST_RETENTION_DAYS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "login_id", unique = true, length = 50)
    private String loginId;

    @Column(length = 255)
    private String password;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(length = 20)
    private String provider;

    @Column(name = "provider_id", length = 100)
    private String providerId;

    @Column(name = "profile_image_url", length = 255)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
        this.password = null;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public boolean isGuest() {
        return GUEST_PROVIDER.equals(this.provider);
    }

    /** 게스트 계정을 정식 회원 계정으로 전환한다. 같은 유저 row를 그대로 쓰므로 냉장고/재료 등 기존 데이터는 이관 없이 유지된다. */
    public void upgradeFromGuest(String loginId, String encodedPassword, String email,
                                  String nickname, String profileImageUrl) {
        this.loginId = loginId;
        this.password = encodedPassword;
        this.email = email;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.provider = null;
        this.providerId = null;
    }
}
