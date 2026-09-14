package com.cookgenie.domain.recipe.repository;

import com.cookgenie.domain.recipe.entity.Recipe;
import com.cookgenie.domain.recipe.entity.RecipeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    List<Recipe> findByRecipeType(RecipeType recipeType);

    List<Recipe> findByTitleContaining(String title);

    Optional<Recipe> findBySourceUrl(String sourceUrl);
}
