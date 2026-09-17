package com.cookgenie.domain.ingredient.external;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 식품의약품안전처 공공데이터포털 "전국통합식품영양성분정보(가공식품)표준데이터" API로 이름 검색을 한다.
 * 브랜드/상품명이 붙은 가공식품(예: "오뚜기 마요네즈")도 정부가 실측한 실제 값을 그때그때 조회할 수 있어서,
 * {@link ClaudeNutritionClient}의 AI 추정(학습된 지식 기반 짐작)보다 먼저 시도하는 게 더 정확하다.
 * 원본 CSV가 31만 건·130MB에 달해 번들할 수 없으므로 앱에 파일을 두지 않고 매번 이 API로 조회한다.
 *
 * <p>data.go.kr 서비스키는 인코딩/디코딩 두 형태가 있는데, 여기서는 <b>디코딩(원본) 키</b>를 설정값으로 받아
 * 직접 한 번만 URL 인코딩한 뒤 {@link URI#create}로 감싸서 RestClient가 다시 인코딩(이중 인코딩)하지
 * 못하게 한다 - {@code CoupangProductClient}에서 겪었던 것과 같은 종류의 버그를 미리 피하는 것.
 * 401이 나면 인코딩된 키를 그대로 설정값에 넣어보는 것도 시도해볼 것(포털 안내에도 명시된 흔한 이슈).
 */
@Slf4j
@Component
public class MfdsProcessedFoodClient {

    private static final String BASE_URL = "https://api.data.go.kr/openapi/tn_pubr_public_nutri_process_info_api";
    private static final Pattern REFERENCE_PATTERN = Pattern.compile("(\\d+)\\s*([a-zA-Z가-힣]*)");

    private final String serviceKey;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public MfdsProcessedFoodClient(
            @Value("${mfds.processed-food-api-key}") String serviceKey,
            ObjectMapper objectMapper) {
        this.serviceKey = serviceKey;
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
    }

    /**
     * 이름으로 가공식품을 검색해 기준량이 100g/100ml인 첫 매칭 결과를 영양정보로 변환한다.
     * 매칭 실패, 호출 실패(키 미설정/네트워크 오류 등)면 empty를 반환한다 - 호출부는 기존 AI 추정으로 넘어가면 된다.
     */
    public Optional<NutritionEstimate> search(String name) {
        try {
            String query = "serviceKey=" + encode(serviceKey)
                    + "&type=json&numOfRows=10&pageNo=1&foodNm=" + encode(name);
            URI uri = URI.create(BASE_URL + "?" + query);

            String body = restClient.get().uri(uri).retrieve().body(String.class);
            return parseFirstMatch(name, body);
        } catch (Exception e) {
            log.warn("[식약처 가공식품 조회] 호출 실패 - name={}, error={}", name, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<NutritionEstimate> parseFirstMatch(String name, String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        JsonNode root = objectMapper.readTree(body);
        JsonNode header = root.path("response").path("header");
        String resultCode = header.path("resultCode").asString("");
        if (!"00".equals(resultCode)) {
            log.warn("[식약처 가공식품 조회] resultCode={} resultMsg={} name={}",
                    resultCode, header.path("resultMsg").asString(""), name);
            return Optional.empty();
        }

        JsonNode itemsNode = root.path("response").path("body").path("items").path("item");
        List<JsonNode> items = new ArrayList<>();
        if (itemsNode.isArray()) {
            itemsNode.forEach(items::add);
        } else if (itemsNode.isObject()) {
            items.add(itemsNode);
        }

        return items.stream()
                .map(this::toEstimate)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    /** 기준량이 100g/100ml가 아닌 항목은(우리 스키마가 항상 100 기준이라) 건너뛴다. */
    private Optional<NutritionEstimate> toEstimate(JsonNode item) {
        String[] referenceAmountUnit = parseReference(text(item, "nutConSrtrQua"));
        Integer referenceAmount = parseInt(referenceAmountUnit[0]);
        if (referenceAmount == null || referenceAmount != 100) {
            return Optional.empty();
        }
        return Optional.of(new NutritionEstimate(
                true,
                referenceAmountUnit[1],
                parseInt(text(item, "enerc")),
                parseDecimal(text(item, "chocdf")),
                parseDecimal(text(item, "prot")),
                parseDecimal(text(item, "fatce")),
                parseDecimal(text(item, "sugar")),
                parseDecimal(text(item, "nat")),
                parseDecimal(text(item, "fibtg"))
        ));
    }

    private String text(JsonNode item, String field) {
        String value = item.path(field).asString(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String[] parseReference(String raw) {
        if (raw == null) {
            return new String[]{null, "g"};
        }
        Matcher matcher = REFERENCE_PATTERN.matcher(raw);
        if (matcher.find()) {
            String unit = matcher.group(2);
            return new String[]{matcher.group(1), unit.isBlank() ? "g" : unit};
        }
        return new String[]{null, "g"};
    }

    private Integer parseInt(String value) {
        BigDecimal decimal = parseDecimal(value);
        return decimal != null ? decimal.intValue() : null;
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
