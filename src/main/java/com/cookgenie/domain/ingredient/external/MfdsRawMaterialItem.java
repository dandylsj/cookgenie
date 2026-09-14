package com.cookgenie.domain.ingredient.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 원재료성 식품 1건. 필드명은 API 응답의 실제 키(camelCase 약어)를 그대로 따른다.
 * foodNm: 식품명(예: "양파_생것"), foodLv3Nm: 식품대분류명(예: "채소류"),
 * nutConSrtrQua: 영양성분함량기준량(예: "100g"), enerc: 에너지(kcal),
 * prot: 단백질(g), fatce: 지방(g), chocdf: 탄수화물(g), sugar: 당류(g),
 * fibtg: 식이섬유(g), nat: 나트륨(mg)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MfdsRawMaterialItem(
        String foodCd,
        String foodNm,
        String foodLv3Nm,
        String nutConSrtrQua,
        String enerc,
        String prot,
        String fatce,
        String chocdf,
        String sugar,
        String fibtg,
        String nat
) {
}
