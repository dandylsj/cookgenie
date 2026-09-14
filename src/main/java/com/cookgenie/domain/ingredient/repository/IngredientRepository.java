package com.cookgenie.domain.ingredient.repository;

import com.cookgenie.domain.ingredient.entity.DataSource;
import com.cookgenie.domain.ingredient.entity.Ingredient;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    Optional<Ingredient> findByBarcode(String barcode);

    Optional<Ingredient> findByName(String name);

    List<Ingredient> findByNameContaining(String name);

    List<Ingredient> findByDataSourceAndIsVerified(DataSource dataSource, Boolean isVerified);
}
