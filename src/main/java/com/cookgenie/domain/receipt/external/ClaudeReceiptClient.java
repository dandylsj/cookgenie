package com.cookgenie.domain.receipt.external;

import com.cookgenie.common.client.anthropic.ClaudeMessageRequest;
import com.cookgenie.common.client.anthropic.ClaudeMessageResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Claude(Anthropic Messages API, 비전)에게 영수증 사진 또는 온라인 쇼핑몰 주문내역 캡처를 보내 식재료 후보
 * 목록을 추출시키는 클라이언트. 두 입력 형태(영수증/주문내역)는 추출 스키마({@link ReceiptScanResult})가
 * 동일해서 tool 정의는 공유하고 프롬프트만 다르게 준다({@link #scan}/{@link #scanOrderHistory}).
 *
 * <p>다른 클라이언트들(영양정보 추정/레시피 생성)은 비용 때문에 {@code anthropic.model}(Haiku)을 쓰지만,
 * 이 클라이언트는 작은 글씨의 한글 상품명을 정밀하게 읽어야 해서 별도의 {@code anthropic.vision-model}
 * (Sonnet)을 쓴다 - 실측으로 Haiku가 잘리거나 흐린 글자를 그럴듯한 다른 글자로 지어내는 경우가 확인됨.
 */
@Slf4j
@Component
public class ClaudeReceiptClient {

    private static final String TOOL_NAME = "record_receipt_items";
    private static final int MAX_TOKENS = 1500;

    /**
     * 글자가 작거나 잘려서 잘 안 보일 때 모델이 그럴듯한 글자를 지어내 버리는(예: "생연어"를 "생영어"로,
     * 잘린 뒷부분을 전혀 다른 단어로) 문제가 실측으로 확인돼서, 모든 인식 프롬프트 끝에 공통으로 붙여
     * 추측성 복원보다 정확도를 우선하도록 못박는다.
     */
    private static final String ACCURACY_INSTRUCTION = " 글자가 작거나 흐리거나 잘려서 확실하게 읽을 수 없으면 "
            + "절대로 비슷한 글자를 지어내지 말고, 분명하게 읽히는 부분까지만 적거나 그 항목을 제외해줘. "
            + "정확하지 않은 추측보다 누락이 낫다.";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String visionModel;

    public ClaudeReceiptClient(
            @Value("${anthropic.api-key}") String apiKey,
            @Value("${anthropic.vision-model}") String visionModel,
            ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.anthropic.com/v1/messages")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();
        this.visionModel = visionModel;
        this.objectMapper = objectMapper;
    }

    /** 영수증 이미지를 분석해서 식재료로 보이는 항목들을 추출한다. 실패하면 empty. */
    public Optional<ReceiptScanResult> scan(String mediaType, String base64Image) {
        String prompt = "이 이미지는 마트/편의점에서 받은 영수증이다. 영수증에 적힌 구매 품목 중 "
                + "식재료/음식으로 볼 수 있는 것만 골라 이름과 수량을 추출해줘. 영수증에 축약되거나 코드처럼 "
                + "적힌 상품명(예: \"국산돈목심600\")은 사람이 알아보기 쉬운 일반적인 이름(예: \"돼지 목심\")으로 "
                + "풀어서 써줘. 세제/휴지/생활용품처럼 식재료가 아닌 항목은 결과에서 제외해줘."
                + ACCURACY_INSTRUCTION
                + " record_receipt_items 도구를 호출해서 결과를 알려줘.";
        return call(prompt, mediaType, base64Image);
    }

    /**
     * 온라인 쇼핑몰(쿠팡/마켓컬리/네이버쇼핑 등) 주문내역(구매내역) 화면 캡처를 분석해서 식재료로 보이는
     * 항목들을 추출한다. 영수증과 추출 스키마는 동일하지만 화면 형태(썸네일+상품명+수량+가격이 나열된 목록)가
     * 달라서 프롬프트만 다르게 준다. 실패하면 empty.
     */
    public Optional<ReceiptScanResult> scanOrderHistory(String mediaType, String base64Image) {
        String prompt = "이 이미지는 쿠팡/마켓컬리/네이버쇼핑 같은 온라인 쇼핑몰의 주문내역(구매내역) 화면을 "
                + "캡처한 것이다. 화면에 나열된 상품 중 식재료/음식으로 볼 수 있는 것만 골라 이름과 수량을 "
                + "추출해줘. 상품명에 용량/옵션이 같이 적혀 있으면(예: \"국산 돼지 목살 600g\") 그대로 이름에 "
                + "포함해도 된다. 주방용품/생활용품처럼 식재료가 아닌 항목은 결과에서 제외해줘."
                + ACCURACY_INSTRUCTION
                + " record_receipt_items 도구를 호출해서 결과를 알려줘.";
        return call(prompt, mediaType, base64Image);
    }

    private Optional<ReceiptScanResult> call(String prompt, String mediaType, String base64Image) {
        ClaudeMessageRequest request = new ClaudeMessageRequest(
                visionModel,
                MAX_TOKENS,
                List.of(ClaudeMessageRequest.Message.withImage("user", prompt, mediaType, base64Image)),
                List.of(receiptTool()),
                Map.of("type", "tool", "name", TOOL_NAME)
        );

        try {
            ClaudeMessageResponse response = restClient.post()
                    .body(request)
                    .retrieve()
                    .body(ClaudeMessageResponse.class);

            return Optional.ofNullable(extractResult(response));
        } catch (Exception e) {
            log.warn("[영수증/주문내역 인식] 호출 실패 - error={}", e.getMessage());
            return Optional.empty();
        }
    }

    private ReceiptScanResult extractResult(ClaudeMessageResponse response) {
        if (response == null || response.content() == null) {
            return null;
        }
        return response.content().stream()
                .filter(block -> "tool_use".equals(block.type()) && TOOL_NAME.equals(block.name()))
                .map(ClaudeMessageResponse.ContentBlock::input)
                .filter(input -> input != null)
                .map(input -> objectMapper.convertValue(input, ReceiptScanResult.class))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> receiptTool() {
        Map<String, Object> itemSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string", "description", "일반적으로 통용되는 식재료/음식 이름"),
                        "quantityText", Map.of("type", "string", "description", "표시용 수량, 예: \"1개\", \"600g\""),
                        "quantityValue", Map.of("type", "number", "description", "계산용 수치 (모르면 생략 가능)"),
                        "unit", Map.of("type", "string", "description", "단위, 예: g, 개, 봉 (모르면 생략 가능)"),
                        "categoryNameGuess", Map.of("type", "string", "description", "카테고리 추정, 예: 채소, 육류, 유제품 등")
                ),
                "required", List.of("name")
        );

        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "items", Map.of("type", "array", "description", "영수증에서 인식한 식재료 항목 목록", "items", itemSchema)
                ),
                "required", List.of("items")
        );

        return Map.of(
                "name", TOOL_NAME,
                "description", "영수증에서 인식한 식재료 항목들을 기록한다.",
                "input_schema", inputSchema
        );
    }
}
