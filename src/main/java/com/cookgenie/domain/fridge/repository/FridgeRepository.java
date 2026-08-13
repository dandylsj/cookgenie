package com.cookgenie.domain.fridge.repository;

import com.cookgenie.domain.fridge.entity.Fridge;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FridgeRepository extends JpaRepository<Fridge, Long> {

    List<Fridge> findByOwnerId(Long ownerId);
}
