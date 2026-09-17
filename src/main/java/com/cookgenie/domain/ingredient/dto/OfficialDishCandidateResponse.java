package com.cookgenie.domain.ingredient.dto;

import com.cookgenie.domain.ingredient.entity.OfficialDish;
import com.cookgenie.domain.ingredient.external.OfficialDishCandidate;
import java.math.BigDecimal;
import lombok.Getter;

/** GET /ingredients/dish-search 응답 한 건. 전부 100g/100ml 기준으로 정규화된 값이다. */
@Getter
public class OfficialDishCandidateResponse {

    private final String foodCd;
    private final String foodNm;
    private final String restNm;
    private final String referenceUnit;
    private final Integer calories;
    private final BigDecimal carbohydrateG;
    private final BigDecimal proteinG;
    private final BigDecimal fatG;
    private final BigDecimal sugarG;
    private final BigDecimal sodiumMg;
    private final BigDecimal fiberG;

    public OfficialDishCandidateResponse(OfficialDishCandidate candidate) {
        this.foodCd = candidate.foodCd();
        this.foodNm = candidate.foodNm();
        this.restNm = candidate.restNm();
        this.referenceUnit = candidate.referenceUnit();
        this.calories = candidate.calories();
        this.carbohydrateG = candidate.carbohydrateG();
        this.proteinG = candidate.proteinG();
        this.fatG = candidate.fatG();
        this.sugarG = candidate.sugarG();
        this.sodiumMg = candidate.sodiumMg();
        this.fiberG = candidate.fiberG();
    }

    /** {@link com.cookgenie.domain.ingredient.repository.OfficialDishRepository} 검색 결과용. */
    public OfficialDishCandidateResponse(OfficialDish dish) {
        this.foodCd = dish.getFoodCd();
        this.foodNm = dish.getFoodNm();
        this.restNm = dish.getRestNm();
        this.referenceUnit = dish.getReferenceUnit();
        this.calories = dish.getCalories();
        this.carbohydrateG = dish.getCarbohydrateG();
        this.proteinG = dish.getProteinG();
        this.fatG = dish.getFatG();
        this.sugarG = dish.getSugarG();
        this.sodiumMg = dish.getSodiumMg();
        this.fiberG = dish.getFiberG();
    }
}
