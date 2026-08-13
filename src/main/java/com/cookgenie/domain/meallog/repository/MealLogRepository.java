package com.cookgenie.domain.meallog.repository;

import com.cookgenie.domain.meallog.entity.MealLog;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealLogRepository extends JpaRepository<MealLog, Long> {

    List<MealLog> findByUserIdAndMealDate(Long userId, LocalDate mealDate);

    List<MealLog> findByUserIdAndMealDateBetween(Long userId, LocalDate startDate, LocalDate endDate);
}
