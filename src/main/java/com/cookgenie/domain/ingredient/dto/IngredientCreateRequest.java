package com.cookgenie.domain.ingredient.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class IngredientCreateRequest {

    @NotBlank(message = "식재료 이름은 필수입니다.")
    private String name;

    @NotBlank(message = "카테고리는 필수입니다.")
    private String categoryName;

    private String defaultUnit;

    /** true면 calories 등을 직접 안 줬을 때 Claude에게 영양정보 추정을 요청한다. 기본 false(토큰 절약 - 나중에 필요할 때만 추정). */
    private Boolean autoEstimateNutrition;

    /** 아래 네 값 중 하나라도 있으면 "기준량당" 영양정보를 직접 입력한 것으로 보고, Claude 호출 없이 그대로 저장한다. */
    private Integer calories;
    private BigDecimal carbohydrateG;
    private BigDecimal proteinG;
    private BigDecimal fatG;

    /** 위 수동 입력값의 기준 단위(g/ml). 안 주면 "g"로 간주한다. */
    private String referenceUnit;

    public boolean hasManualNutrition() {
        return calories != null || carbohydrateG != null || proteinG != null || fatG != null;
    }
}
