package com.cookgenie.domain.meallog.repository;

import com.cookgenie.domain.meallog.entity.NutritionGoal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NutritionGoalRepository extends JpaRepository<NutritionGoal, Long> {

    List<NutritionGoal> findByUserIdOrderByEffectiveDateDesc(Long userId);

    Optional<NutritionGoal> findFirstByUserIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
            Long userId, LocalDate date);
}
