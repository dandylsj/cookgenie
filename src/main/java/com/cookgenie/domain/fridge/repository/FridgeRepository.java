package com.cookgenie.domain.fridge.repository;

import com.cookgenie.domain.fridge.entity.Fridge;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FridgeRepository extends JpaRepository<Fridge, Long> {

    List<Fridge> findByOwnerId(Long ownerId);

    Optional<Fridge> findByInviteCodeAndInviteCodeExpiryDateAfter(String inviteCode, LocalDateTime now);

    boolean existsByInviteCodeAndInviteCodeExpiryDateAfter(String inviteCode, LocalDateTime now);
}
