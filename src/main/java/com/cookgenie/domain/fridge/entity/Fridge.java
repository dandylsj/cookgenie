package com.cookgenie.domain.fridge.entity;

import com.cookgenie.common.entity.BaseTimeEntity;
import com.cookgenie.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fridges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Fridge extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "invite_code", length = 4)
    private String inviteCode;

    @Column(name = "invite_code_expiry_date")
    private LocalDateTime inviteCodeExpiryDate;

    public void updateInviteCode(String inviteCode, LocalDateTime expiryDate) {
        this.inviteCode = inviteCode;
        this.inviteCodeExpiryDate = expiryDate;
    }
}
