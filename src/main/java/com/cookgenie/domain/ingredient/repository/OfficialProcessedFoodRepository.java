package com.cookgenie.domain.ingredient.repository;

import com.cookgenie.domain.ingredient.entity.OfficialProcessedFood;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfficialProcessedFoodRepository extends JpaRepository<OfficialProcessedFood, Long> {

    Optional<OfficialProcessedFood> findFirstByFoodNm(String foodNm);

    boolean existsByFoodCd(String foodCd);

    List<OfficialProcessedFood> findByFoodNmContainingOrMfrNmContaining(String foodNmKeyword, String mfrNmKeyword, Pageable pageable);
}
