package com.cookgenie.domain.ingredient.dto;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 식재료의 기준량당 영양정보를 사용자가 직접 입력/수정할 때 쓰는 요청. */
@Getter
@NoArgsConstructor
public class NutritionUpdateRequest {

    private Integer calories;
    private BigDecimal carbohydrateG;
    private BigDecimal proteinG;
    private BigDecimal fatG;

    /** 기준 단위(g/ml). 안 주면 기존 값(신규면 "g")을 그대로 쓴다. */
    private String referenceUnit;
}
