package com.cookgenie.domain.recipe.repository;

import com.cookgenie.domain.recipe.entity.RecipeIngredient;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, Long> {

    List<RecipeIngredient> findByRecipeId(Long recipeId);

    List<RecipeIngredient> findByRecipeIdIn(Collection<Long> recipeIds);
}
