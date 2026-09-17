package com.cookgenie.domain.ingredient.repository;

import com.cookgenie.domain.ingredient.entity.OfficialDish;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfficialDishRepository extends JpaRepository<OfficialDish, Long> {

    Optional<OfficialDish> findFirstByFoodNm(String foodNm);

    List<OfficialDish> findByFoodNmContainingOrRestNmContaining(String foodNmKeyword, String restNmKeyword, Pageable pageable);

    /** foodCd가 겹치는 후보들을 미리 가져와서, 배치 저장 전에 (foodCd, foodNm, restNm) 조합으로 중복을 걸러낸다. */
    List<OfficialDish> findByFoodCdIn(Collection<String> foodCds);
}
