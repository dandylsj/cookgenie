package com.cookgenie.domain.meallog.repository;

import com.cookgenie.domain.meallog.entity.MealLogItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealLogItemRepository extends JpaRepository<MealLogItem, Long> {

    List<MealLogItem> findByMealLogId(Long mealLogId);
}
