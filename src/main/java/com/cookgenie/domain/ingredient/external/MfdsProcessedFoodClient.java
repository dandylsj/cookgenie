package com.cookgenie.domain.ingredient.external;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 *
 * <p><b>기준량 정규화</b>: 실제 데이터를 보면 기준량(nutConSrtrQua)이 100g/100ml가 아닌 항목이 많다
 * (1회 제공량, 포장 전체 중량 기준 등 제각각). 100이 아니면 버리는 대신, 100 기준으로 비례 환산해서
 * 항상 우리 스키마(referenceAmount=100 고정)에 맞춰 반환한다 - 이전에는 100이 아니면 통째로 걸러버려서
 * 흔한 가공식품 다수가 매칭돼도 영양정보가 안 잡히는 문제가 있었음.
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
     * 이름(부분 일치)으로 가공식품을 검색해 최대 limit개의 후보를 반환한다. 화면에서 사용자가 직접 정확한
     * 제품을 고를 수 있게 하는 용도. 호출 실패(키 미설정/네트워크 오류 등)면 빈 리스트.
     *
     * <p><b>주의</b>: 이 API의 foodNm 파라미터는 완전 일치만 지원한다(부분/포함 검색 불가) - 실측으로 확인됨.
     * "실온"→"닭"처럼 좁혀가며 찾는 부분검색 UX는 {@link com.cookgenie.domain.ingredient.OfficialProcessedFoodSyncService}가
     * foodNm 없이 전체 데이터를 로컬 DB로 복사해둔 뒤 그 테이블에서 LIKE 검색하는 방식으로 만든다
     * ({@link com.cookgenie.domain.ingredient.repository.OfficialProcessedFoodRepository}).
     */
    public List<OfficialFoodCandidate> searchCandidates(String keyword, int limit) {
        try {
            String query = "serviceKey=" + encode(serviceKey)
                    + "&type=json&numOfRows=" + Math.max(1, limit) + "&pageNo=1&foodNm=" + encode(keyword);
            URI uri = URI.create(BASE_URL + "?" + query);

            String body = restClient.get().uri(uri).retrieve().body(String.class);
            return parseCandidates(keyword, body);
        } catch (Exception e) {
            log.warn("[식약처 가공식품 검색] 호출 실패 - keyword={}, error={}", keyword, e.getMessage());
            return List.of();
        }
    }

    /** 이름으로 검색해 첫 매칭 결과를 영양정보로 변환한다. 매칭 없으면 empty - 호출부는 AI 추정으로 넘어가면 된다. */
    public Optional<NutritionEstimate> search(String name) {
        return searchCandidates(name, 5).stream().findFirst().map(OfficialFoodCandidate::toEstimate);
    }

    /**
     * foodNm 필터 없이 pageNo/numOfRows로 전체 데이터를 페이지 단위로 가져온다. 전체 약 59만 건을 로컬 DB로
     * 복사하는 {@link com.cookgenie.domain.ingredient.OfficialProcessedFoodSyncService}가 순차적으로 호출한다.
     * 호출 실패면 빈 페이지(totalCount=0)를 반환한다 - 호출부가 재시도/중단 여부를 판단.
     */
    public PageResult fetchPage(int pageNo, int numOfRows) {
        try {
            String query = "serviceKey=" + encode(serviceKey)
                    + "&type=json&numOfRows=" + numOfRows + "&pageNo=" + pageNo;
            URI uri = URI.create(BASE_URL + "?" + query);

            String body = restClient.get().uri(uri).retrieve().body(String.class);
            return parsePage(pageNo, body);
        } catch (Exception e) {
            log.warn("[식약처 가공식품 전체 동기화] 페이지 조회 실패 - pageNo={}, error={}", pageNo, e.getMessage());
            return new PageResult(List.of(), 0);
        }
    }

    /** 전체 동기화용 페이지 결과. items는 100g/100ml 기준으로 정규화된 이 페이지의 후보 목록, totalCount는 전체 건수. */
    public record PageResult(List<OfficialFoodCandidate> items, int totalCount) {
    }

    private List<OfficialFoodCandidate> parseCandidates(String keyword, String body) {
        JsonNode root = readRoot(body);
        if (root == null) {
            return List.of();
        }
        JsonNode header = root.path("header");
        String resultCode = header.path("resultCode").asString("");
        if (!"00".equals(resultCode)) {
            logNonZeroResult(header, resultCode, "keyword=" + keyword, body);
            return List.of();
        }
        return extractItems(root).stream()
                .map(this::toCandidate)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    private PageResult parsePage(int pageNo, String body) {
        JsonNode root = readRoot(body);
        if (root == null) {
            return new PageResult(List.of(), 0);
        }
        JsonNode header = root.path("header");
        String resultCode = header.path("resultCode").asString("");
        if (!"00".equals(resultCode)) {
            logNonZeroResult(header, resultCode, "pageNo=" + pageNo, body);
            return new PageResult(List.of(), 0);
        }

        int totalCount = root.path("body").path("totalCount").asInt(0);
        List<OfficialFoodCandidate> candidates = extractItems(root).stream()
                .map(this::toCandidate)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
        return new PageResult(candidates, totalCount);
    }

    private JsonNode readRoot(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        return objectMapper.readTree(body);
    }

    /** 응답 형태는 {"header":{...},"body":{...}}로 평평하다 - response로 한 번 더 감싸져 있지 않다. */
    private List<JsonNode> extractItems(JsonNode root) {
        JsonNode itemsNode = root.path("body").path("items").path("item");
        List<JsonNode> items = new ArrayList<>();
        if (itemsNode.isArray()) {
            itemsNode.forEach(items::add);
        } else if (itemsNode.isObject()) {
            items.add(itemsNode);
        }
        return items;
    }

    private void logNonZeroResult(JsonNode header, String resultCode, String context, String body) {
        // NODATA_ERROR(03)는 검색 결과가 없다는 정상 응답이고, 그 외(서비스키 오류 등)는 원인 파악을 위해
        // 원본 응답 본문을 남긴다 - data.go.kr는 게이트웨이 오류일 때 header가 아예 다른 형태로 오기도 함.
        log.warn("[식약처 가공식품 조회] resultCode={} resultMsg={} {} rawBody={}",
                resultCode, header.path("resultMsg").asString(""), context,
                body.length() > 500 ? body.substring(0, 500) : body);
    }

    /** 기준량을 100g/100ml 기준으로 비례 환산한다. 기준량을 못 읽으면(0 또는 파싱 실패) 신뢰할 수 없어 버린다. */
    private Optional<OfficialFoodCandidate> toCandidate(JsonNode item) {
        if (text(item, "foodCd") == null || text(item, "foodNm") == null) {
            return Optional.empty();
        }
        String[] referenceAmountUnit = parseReference(text(item, "nutConSrtrQua"));
        Integer referenceAmount = parseInt(referenceAmountUnit[0]);
        if (referenceAmount == null || referenceAmount == 0) {
            return Optional.empty();
        }
        BigDecimal ratio = BigDecimal.valueOf(100).divide(BigDecimal.valueOf(referenceAmount), 4, RoundingMode.HALF_UP);

        return Optional.of(new OfficialFoodCandidate(
                text(item, "foodCd"),
                text(item, "foodNm"),
                text(item, "mfrNm"),
                referenceAmountUnit[1],
                scaleInt(parseInt(text(item, "enerc")), ratio),
                scaleDecimal(parseDecimal(text(item, "chocdf")), ratio),
                scaleDecimal(parseDecimal(text(item, "prot")), ratio),
                scaleDecimal(parseDecimal(text(item, "fatce")), ratio),
                scaleDecimal(parseDecimal(text(item, "sugar")), ratio),
                scaleDecimal(parseDecimal(text(item, "nat")), ratio),
                scaleDecimal(parseDecimal(text(item, "fibtg")), ratio)
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

    private BigDecimal scaleDecimal(BigDecimal value, BigDecimal ratio) {
        return value == null ? null : value.multiply(ratio).setScale(1, RoundingMode.HALF_UP);
    }

    private Integer scaleInt(Integer value, BigDecimal ratio) {
        return value == null ? null : BigDecimal.valueOf(value).multiply(ratio).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
