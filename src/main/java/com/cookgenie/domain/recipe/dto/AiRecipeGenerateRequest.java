package com.cookgenie.domain.recipe.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AiRecipeGenerateRequest {

    /** 예: "매콤하게", "국물 요리로" 같은 추가 요청사항. 없어도 된다. */
    private String note;
}
