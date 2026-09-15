package com.cookgenie.domain.recipe.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AiRecipeGenerateRequest {

    /** 예: "매콤하게", "국물 요리로" 같은 추가 요청사항. 없어도 된다. */
    private String note;

    /**
     * true(기본값)면 냉장고에 있는 재료만으로 레시피를 만든다(냉장고가 비어있으면 에러).
     * false면 냉장고 재료와 무관하게 note 요청 내용만으로 자유롭게 레시피를 생성한다.
     */
    private Boolean useFridgeIngredients;

    public boolean shouldUseFridgeIngredients() {
        return useFridgeIngredients == null || useFridgeIngredients;
    }
}
