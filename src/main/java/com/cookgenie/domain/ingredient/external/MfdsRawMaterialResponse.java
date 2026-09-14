package com.cookgenie.domain.ingredient.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 농촌진흥청 "전국통합식품영양성분정보(원재료성식품)" Open API 응답 매핑.
 * End Point: https://api.data.go.kr/openapi/tn_pubr_public_nutri_material_info_api
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MfdsRawMaterialResponse(Header header, Body body) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String resultCode, String resultMsg) {

        public boolean isSuccess() {
            return "00".equals(resultCode);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items, Integer numOfRows, Integer pageNo, Integer totalCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<MfdsRawMaterialItem> item) {
    }
}
