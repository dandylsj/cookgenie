package com.cookgenie.domain.fridge.repository;

import com.cookgenie.domain.fridge.entity.FridgeMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FridgeMemberRepository extends JpaRepository<FridgeMember, Long> {

    List<FridgeMember> findByFridgeId(Long fridgeId);

    List<FridgeMember> findByUserId(Long userId);

    Optional<FridgeMember> findByFridgeIdAndUserId(Long fridgeId, Long userId);
}
