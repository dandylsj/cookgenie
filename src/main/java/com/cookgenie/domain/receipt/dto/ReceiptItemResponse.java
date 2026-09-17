package com.cookgenie.domain.receipt.dto;

import com.cookgenie.domain.ingredient.dto.OfficialDishCandidateResponse;
import com.cookgenie.domain.ingredient.dto.OfficialFoodCandidateResponse;
import com.cookgenie.domain.receipt.external.ReceiptScanResult;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * 인식된 항목 한 건. matchedIngredientId가 있으면 이미 등록된 식재료(그대로 재사용). 없고
 * matchedProcessedFood/matchedDish가 있으면 식약처 공식 데이터와 이름이 일치한 것 - AI 추정 없이 그 값을
 * 그대로 POST /ingredients의 직접 입력값으로 넘겨 등록할 수 있다. 셋 다 없으면 처음 보는 이름이라
 * autoEstimateNutrition=true(AI 추정) 또는 영양정보 없이 등록해야 한다.
 */
@Getter
public class ReceiptItemResponse {

    private final String name;
    private final String quantityText;
    private final BigDecimal quantityValue;
    private final String unit;
    private final String categoryNameGuess;
    private final Long matchedIngredientId;
    private final Long matchedCategoryId;
    private final OfficialFoodCandidateResponse matchedProcessedFood;
    private final OfficialDishCandidateResponse matchedDish;

    public ReceiptItemResponse(
            ReceiptScanResult.ScannedItem item,
            Long matchedIngredientId,
            Long matchedCategoryId,
            OfficialFoodCandidateResponse matchedProcessedFood,
            OfficialDishCandidateResponse matchedDish) {
        this.name = item.name();
        this.quantityText = item.quantityText();
        this.quantityValue = item.quantityValue();
        this.unit = item.unit();
        this.categoryNameGuess = item.categoryNameGuess();
        this.matchedIngredientId = matchedIngredientId;
        this.matchedCategoryId = matchedCategoryId;
        this.matchedProcessedFood = matchedProcessedFood;
        this.matchedDish = matchedDish;
    }
}
