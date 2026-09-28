package com.cookgenie.domain.ingredient.repository;

import com.cookgenie.domain.ingredient.entity.OfficialDish;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfficialDishRepository extends JpaRepository<OfficialDish, Long> {

    Optional<OfficialDish> findFirstByFoodNm(String foodNm);

    List<OfficialDish> findByFoodNmContainingOrRestNmContaining(String foodNmKeyword, String restNmKeyword, Pageable pageable);

    /**
     * FULLTEXT(ngram) 인덱스를 쓰는 부분검색. {@code booleanQuery}는 {@code FullTextKeyword}가 만든
     * 따옴표 구문("키워드")이라 ngram 토큰이 연속으로 붙어 있는 행만 찾는다 - 즉 LIKE '%키워드%'와 같은 결과를
     * 인덱스로 찾는다. 인덱스는 {@code OfficialFoodFullTextIndexInitializer}가 만든다.
     */
    @Query(value = "SELECT * FROM official_dishes WHERE MATCH(food_nm, rest_nm) AGAINST(:booleanQuery IN BOOLEAN MODE) LIMIT :limit",
            nativeQuery = true)
    List<OfficialDish> searchByFullText(@Param("booleanQuery") String booleanQuery, @Param("limit") int limit);

    /** foodCd가 겹치는 후보들을 미리 가져와서, 배치 저장 전에 (foodCd, foodNm, restNm) 조합으로 중복을 걸러낸다. */
    List<OfficialDish> findByFoodCdIn(Collection<String> foodCds);
}
