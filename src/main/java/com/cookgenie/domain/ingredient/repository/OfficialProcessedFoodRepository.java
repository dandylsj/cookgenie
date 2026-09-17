package com.cookgenie.domain.ingredient.repository;

import com.cookgenie.domain.ingredient.entity.OfficialProcessedFood;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfficialProcessedFoodRepository extends JpaRepository<OfficialProcessedFood, Long> {

    Optional<OfficialProcessedFood> findFirstByFoodNm(String foodNm);

    boolean existsByFoodCd(String foodCd);

    List<OfficialProcessedFood> findByFoodNmContainingOrMfrNmContaining(String foodNmKeyword, String mfrNmKeyword, Pageable pageable);

    /** 이미 저장된 foodCd만 골라낸다 - 정부 API 페이지 사이에 같은 항목이 겹쳐서 나오는 경우가 있어서 배치 저장 전에 걸러낸다. */
    @Query("select o.foodCd from OfficialProcessedFood o where o.foodCd in :foodCds")
    List<String> findExistingFoodCds(@Param("foodCds") Collection<String> foodCds);
}
