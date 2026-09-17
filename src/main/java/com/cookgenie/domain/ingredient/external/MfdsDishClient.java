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
 * 식품의약품안전처 공공데이터포털 "전국통합식품영양성분정보(음식)표준데이터" API로 조회한다. 이 데이터셋은
 * 국민건강영양조사 음식별 식품재료량 자료집 기반으로 산출된 "조리된 메뉴"(짜장면, 김치찌개 등) 기준
 * 영양정보라, 배달/외식 음식을 {@link com.cookgenie.domain.meallog.MealLogService} 직접 입력에 등록할 때
 * {@link ClaudeNutritionClient}의 AI 추정보다 먼저 시도하기 좋다. 가공식품용 {@link MfdsProcessedFoodClient}와
 * 거의 같은 구조이지만 완전히 별도의 API/데이터셋이라 클라이언트를 분리해서 둔다.
 *
 * <p>이 API의 {@code foodCd}는 가공식품 API의 foodCd와 달리 "대표식품코드"가 별도 필드(foodLv4Cd)로
 * 분리되어 있어서 진짜 개별 항목 식별자일 가능성이 높지만, 가공식품에서 이 가정이 틀렸던 적이 있어서
 * (실측으로 확인됨) 안전하게 (foodCd, foodNm, restNm) 조합으로만 중복을 판단한다
 * ({@link com.cookgenie.domain.ingredient.OfficialDishSyncService} 참고).
 */
@Slf4j
@Component
public class MfdsDishClient {

    private static final String BASE_URL = "https://api.data.go.kr/openapi/tn_pubr_public_nutri_food_info_api";
    private static final Pattern REFERENCE_PATTERN = Pattern.compile("(\\d+)\\s*([a-zA-Z가-힣]*)");

    private final String serviceKey;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public MfdsDishClient(
            @Value("${mfds.dish-api-key}") String serviceKey,
            ObjectMapper objectMapper) {
        this.serviceKey = serviceKey;
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
    }

    /** 이름(완전 일치)으로 음식을 검색해 최대 limit개의 후보를 반환한다. 호출 실패(키 미설정/네트워크 오류 등)면 빈 리스트. */
    public List<OfficialDishCandidate> searchCandidates(String keyword, int limit) {
        try {
            String query = "serviceKey=" + encode(serviceKey)
                    + "&type=json&numOfRows=" + Math.max(1, limit) + "&pageNo=1&foodNm=" + encode(keyword);
            URI uri = URI.create(BASE_URL + "?" + query);

            String body = restClient.get().uri(uri).retrieve().body(String.class);
            return parseCandidates(keyword, body);
        } catch (Exception e) {
            log.warn("[음식 공공데이터 검색] 호출 실패 - keyword={}, error={}", keyword, e.getMessage());
            return List.of();
        }
    }

    /** 이름으로 검색해 첫 매칭 결과를 영양정보로 변환한다. 매칭 없으면 empty - 호출부는 AI 추정으로 넘어가면 된다. */
    public Optional<NutritionEstimate> search(String name) {
        return searchCandidates(name, 5).stream().findFirst().map(OfficialDishCandidate::toEstimate);
    }

    /**
     * foodNm 필터 없이 pageNo/numOfRows로 전체 데이터를 페이지 단위로 가져온다. 전체를 로컬 DB로 복사하는
     * {@link com.cookgenie.domain.ingredient.OfficialDishSyncService}가 순차적으로 호출한다.
     */
    public PageResult fetchPage(int pageNo, int numOfRows) {
        try {
            String query = "serviceKey=" + encode(serviceKey)
                    + "&type=json&numOfRows=" + numOfRows + "&pageNo=" + pageNo;
            URI uri = URI.create(BASE_URL + "?" + query);

            String body = restClient.get().uri(uri).retrieve().body(String.class);
            return parsePage(pageNo, body);
        } catch (Exception e) {
            log.warn("[음식 전체 동기화] 페이지 조회 실패 - pageNo={}, error={}", pageNo, e.getMessage());
            return new PageResult(List.of(), 0);
        }
    }

    /** 전체 동기화용 페이지 결과. items는 100g/100ml 기준으로 정규화된 이 페이지의 후보 목록, totalCount는 전체 건수. */
    public record PageResult(List<OfficialDishCandidate> items, int totalCount) {
    }

    private List<OfficialDishCandidate> parseCandidates(String keyword, String body) {
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
        List<OfficialDishCandidate> candidates = extractItems(root).stream()
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
        log.warn("[음식 공공데이터 조회] resultCode={} resultMsg={} {} rawBody={}",
                resultCode, header.path("resultMsg").asString(""), context,
                body.length() > 500 ? body.substring(0, 500) : body);
    }

    /** 기준량을 100g/100ml 기준으로 비례 환산한다. 기준량을 못 읽으면(0 또는 파싱 실패) 신뢰할 수 없어 버린다. */
    private Optional<OfficialDishCandidate> toCandidate(JsonNode item) {
        if (text(item, "foodCd") == null || text(item, "foodNm") == null) {
            return Optional.empty();
        }
        String[] referenceAmountUnit = parseReference(text(item, "nutConSrtrQua"));
        Integer referenceAmount = parseInt(referenceAmountUnit[0]);
        if (referenceAmount == null || referenceAmount == 0) {
            return Optional.empty();
        }
        BigDecimal ratio = BigDecimal.valueOf(100).divide(BigDecimal.valueOf(referenceAmount), 4, RoundingMode.HALF_UP);

        return Optional.of(new OfficialDishCandidate(
                text(item, "foodCd"),
                text(item, "foodNm"),
                text(item, "restNm"),
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
