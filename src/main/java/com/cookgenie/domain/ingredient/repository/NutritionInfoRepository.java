package com.cookgenie.domain.ingredient.repository;

import com.cookgenie.domain.ingredient.entity.NutritionInfo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NutritionInfoRepository extends JpaRepository<NutritionInfo, Long> {

    Optional<NutritionInfo> findByIngredientId(Long ingredientId);
}
