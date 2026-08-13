package com.cookgenie.domain.ingredient.repository;

import com.cookgenie.domain.ingredient.entity.IngredientUnit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientUnitRepository extends JpaRepository<IngredientUnit, Long> {

    List<IngredientUnit> findByIngredientId(Long ingredientId);
}
