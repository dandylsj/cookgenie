package com.cookgenie.domain.recipe.repository;

import com.cookgenie.domain.recipe.entity.RecipeTag;
import com.cookgenie.domain.recipe.entity.RecipeTagId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeTagRepository extends JpaRepository<RecipeTag, RecipeTagId> {

    List<RecipeTag> findByIdRecipeId(Long recipeId);

    List<RecipeTag> findByIdTagId(Long tagId);
}
