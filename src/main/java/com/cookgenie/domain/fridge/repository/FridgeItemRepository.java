package com.cookgenie.domain.fridge.repository;

import com.cookgenie.domain.fridge.entity.FridgeItem;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FridgeItemRepository extends JpaRepository<FridgeItem, Long> {

    List<FridgeItem> findByFridgeId(Long fridgeId);

    List<FridgeItem> findByFridgeIdAndExpiryDateLessThanEqual(Long fridgeId, LocalDate expiryDate);

    boolean existsByIngredientId(Long ingredientId);

    void deleteByFridgeId(Long fridgeId);
}
