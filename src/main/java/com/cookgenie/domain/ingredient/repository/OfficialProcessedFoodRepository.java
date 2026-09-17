package com.cookgenie.domain.ingredient.repository;

import com.cookgenie.domain.ingredient.entity.OfficialProcessedFood;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfficialProcessedFoodRepository extends JpaRepository<OfficialProcessedFood, Long> {

    Optional<OfficialProcessedFood> findFirstByFoodNm(String foodNm);

    List<OfficialProcessedFood> findByFoodNmContainingOrMfrNmContaining(String foodNmKeyword, String mfrNmKeyword, Pageable pageable);

    /**
     * foodCd가 겹치는 후보들을 미리 가져와서, 배치 저장 전에 (foodCd, foodNm, mfrNm) 조합으로 이미 저장된
     * 항목을 걸러낸다. foodCd 하나만으로는 안 된다 - "대표식품코드" 체계라 서로 다른 제조사/상품이 같은
     * foodCd를 공유하는 경우가 흔해서, foodCd만 보고 걸러내면 실제로 다른 상품을 중복으로 오인해 버린다.
     */
    List<OfficialProcessedFood> findByFoodCdIn(Collection<String> foodCds);
}
