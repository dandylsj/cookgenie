package com.cookgenie.domain.shopping.repository;

import com.cookgenie.domain.shopping.entity.ShoppingItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShoppingItemRepository extends JpaRepository<ShoppingItem, Long> {

    List<ShoppingItem> findByFridgeIdOrderByCheckedAscCreatedAtDesc(Long fridgeId);

    void deleteByFridgeId(Long fridgeId);
}
