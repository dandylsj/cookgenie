package com.cookgenie.domain.recipe.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AiRecipeGenerateRequest {

    /** 예: "매콤하게", "국물 요리로" 같은 추가 요청사항. 없어도 된다. */
    private String note;

    /**
     * true(기본값, 생략해도 true) - 냉장고에 있는 재료를 기준으로 레시피를 생성한다(냉장고에 재료가 없으면 에러).
     * false - 냉장고 재료를 무시하고 note에만 맞는 레시피를 자유롭게 생성한다.
     */
    private Boolean useFridgeIngredients;

    /** null이면 기본값(true)으로 취급한다. */
    public boolean isUseFridgeIngredients() {
        return useFridgeIngredients == null || useFridgeIngredients;
    }
}
