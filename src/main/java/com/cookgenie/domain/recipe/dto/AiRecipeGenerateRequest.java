package com.cookgenie.domain.recipe.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AiRecipeGenerateRequest {

    /** 예: "매콤하게", "국물 요리로" 같은 추가 요청사항. useFridgeIngredients=false면 이 내용이 사실상 유일한 요청 기준이 되므로 필수. */
    private String note;

    /** true(기본값)면 냉장고 재료를 기준으로 레시피를 만들고, false면 냉장고 재료를 무시하고 note에 적힌 요청대로만 자유롭게 생성한다. */
    private Boolean useFridgeIngredients;

    public boolean isUseFridgeIngredients() {
        return useFridgeIngredients == null || useFridgeIngredients;
    }
}
