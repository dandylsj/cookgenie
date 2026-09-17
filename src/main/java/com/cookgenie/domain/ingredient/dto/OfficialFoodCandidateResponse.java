package com.cookgenie.domain.ingredient.dto;

import com.cookgenie.domain.ingredient.entity.OfficialProcessedFood;
import com.cookgenie.domain.ingredient.external.OfficialFoodCandidate;
import java.math.BigDecimal;
import lombok.Getter;

/** GET /ingredients/official-search 응답 한 건. 전부 100g/100ml 기준으로 정규화된 값이다. */
@Getter
public class OfficialFoodCandidateResponse {

    private final String foodCd;
    private final String foodNm;
    private final String mfrNm;
    private final String referenceUnit;
    private final Integer calories;
    private final BigDecimal carbohydrateG;
    private final BigDecimal proteinG;
    private final BigDecimal fatG;
    private final BigDecimal sugarG;
    private final BigDecimal sodiumMg;
    private final BigDecimal fiberG;

    public OfficialFoodCandidateResponse(OfficialFoodCandidate candidate) {
        this.foodCd = candidate.foodCd();
        this.foodNm = candidate.foodNm();
        this.mfrNm = candidate.mfrNm();
        this.referenceUnit = candidate.referenceUnit();
        this.calories = candidate.calories();
        this.carbohydrateG = candidate.carbohydrateG();
        this.proteinG = candidate.proteinG();
        this.fatG = candidate.fatG();
        this.sugarG = candidate.sugarG();
        this.sodiumMg = candidate.sodiumMg();
        this.fiberG = candidate.fiberG();
    }

    /** {@link com.cookgenie.domain.ingredient.repository.OfficialProcessedFoodRepository} 검색 결과용. */
    public OfficialFoodCandidateResponse(OfficialProcessedFood food) {
        this.foodCd = food.getFoodCd();
        this.foodNm = food.getFoodNm();
        this.mfrNm = food.getMfrNm();
        this.referenceUnit = food.getReferenceUnit();
        this.calories = food.getCalories();
        this.carbohydrateG = food.getCarbohydrateG();
        this.proteinG = food.getProteinG();
        this.fatG = food.getFatG();
        this.sugarG = food.getSugarG();
        this.sodiumMg = food.getSodiumMg();
        this.fiberG = food.getFiberG();
    }
}
