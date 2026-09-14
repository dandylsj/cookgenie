package com.cookgenie.domain.ingredient.external;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** 농촌진흥청 원재료성 식품 영양성분 Open API 호출 클라이언트. */
@Slf4j
@Component
public class MfdsRawMaterialClient {

    private final RestClient restClient;
    private final String serviceKey;

    public MfdsRawMaterialClient(
            @Value("${mfds.raw-material-api.base-url}") String baseUrl,
            @Value("${mfds.raw-material-api.service-key}") String serviceKey) {
        this.restClient = RestClient.create(baseUrl);
        this.serviceKey = serviceKey;
    }

    /** 한 페이지를 조회한다. 필터링은 지원되지 않아(NODATA_ERROR) 전체를 페이지 단위로 순회해야 한다. */
    public MfdsRawMaterialResponse fetchPage(int pageNo, int numOfRows) {
        MfdsRawMaterialResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("serviceKey", serviceKey)
                        .queryParam("pageNo", pageNo)
                        .queryParam("numOfRows", numOfRows)
                        .queryParam("type", "json")
                        .build())
                .retrieve()
                .body(MfdsRawMaterialResponse.class);

        if (response == null || response.header() == null || !response.header().isSuccess()) {
            String msg = response != null && response.header() != null ? response.header().resultMsg() : "no response";
            log.warn("[MFDS] 원재료 API 호출 실패 - pageNo={}, message={}", pageNo, msg);
        }
        return response;
    }
}
