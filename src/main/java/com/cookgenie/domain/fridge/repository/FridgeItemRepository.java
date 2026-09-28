package com.cookgenie.domain.fridge.repository;

import com.cookgenie.domain.fridge.entity.FridgeItem;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FridgeItemRepository extends JpaRepository<FridgeItem, Long> {

    List<FridgeItem> findByFridgeId(Long fridgeId);

    /**
     * 목록/통계 응답은 재료마다 ingredient 이름과 category 이름을 꼭 읽는데, LAZY 연관관계를 그대로 두면
     * 재료 N개당 ingredient/category 조회가 따로 나가서 N+1이 된다 - 한 번의 조인으로 같이 가져온다.
     */
    @Query("select fi from FridgeItem fi join fetch fi.ingredient i left join fetch i.category where fi.fridge.id = :fridgeId")
    List<FridgeItem> findWithIngredientByFridgeId(@Param("fridgeId") Long fridgeId);

    List<FridgeItem> findByFridgeIdAndExpiryDateLessThanEqual(Long fridgeId, LocalDate expiryDate);

    boolean existsByIngredientId(Long ingredientId);

    void deleteByFridgeId(Long fridgeId);
}
