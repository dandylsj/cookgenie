package com.cookgenie.domain.product.external;

import com.cookgenie.common.client.anthropic.ClaudeMessageRequest;
import com.cookgenie.common.client.anthropic.ClaudeMessageResponse;
import com.cookgenie.domain.receipt.external.ReceiptScanResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Claude(Anthropic Messages API, 비전)에게 실물 식품 상품(포장/라벨) 사진을 보내 상품명을 읽어 식재료 후보
 * 목록을 추출시키는 클라이언트. 추출 스키마가 영수증/주문내역 인식({@link ReceiptScanResult})과 완전히
 * 동일해서 그 타입을 그대로 재사용한다 - 입력 방식만 다를 뿐 "이름/수량/카테고리 후보를 뽑는다"는 목적이 같음.
 *
 * <p>{@link com.cookgenie.domain.receipt.external.ClaudeReceiptClient}와 같은 이유로 별도의
 * {@code anthropic.vision-model}(Sonnet)을 쓴다 - 포장 글씨가 작아서 정밀한 글자 인식이 필요함.
 */
@Slf4j
@Component
public class ClaudeProductClient {

    private static final String TOOL_NAME = "record_product_items";
    private static final int MAX_TOKENS = 1000;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String visionModel;

    public ClaudeProductClient(
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

    /** 실물 상품 사진을 분석해서 식재료로 보이는 상품들을 추출한다. 실패하면 empty. */
    public Optional<ReceiptScanResult> scan(String mediaType, String base64Image) {
        String prompt = "이 사진은 실제 식품/식재료 상품(포장지, 라벨, 용기 등)을 찍은 사진이다. 사진에 보이는 "
                + "상품명을 브랜드명과 함께 읽어서 알아볼 수 있는 이름으로 추출해줘(예: \"오뚜기 진라면 매운맛\"). "
                + "이름에는 브랜드명+상품명만 넣고, 중량/용량(예: \"500g\")은 이름에 넣지 말고 quantityText/"
                + "quantityValue/unit 필드로만 알려줘. 한 사진에 여러 상품이 보이면 "
                + "각각 별도 항목으로 추출해줘. 글자를 읽을 수 없거나 식품이 아닌 경우는 결과에서 제외해줘. "
                + "글자가 작거나 흐리거나 잘려서 확실하게 읽을 수 없으면 절대로 비슷한 글자를 지어내지 말고, "
                + "분명하게 읽히는 부분까지만 적거나 그 항목을 제외해줘. 정확하지 않은 추측보다 누락이 낫다. "
                + "record_product_items 도구를 호출해서 결과를 알려줘.";

        ClaudeMessageRequest request = new ClaudeMessageRequest(
                visionModel,
                MAX_TOKENS,
                List.of(ClaudeMessageRequest.Message.withImage("user", prompt, mediaType, base64Image)),
                List.of(productTool()),
                Map.of("type", "tool", "name", TOOL_NAME)
        );

        try {
            ClaudeMessageResponse response = restClient.post()
                    .body(request)
                    .retrieve()
                    .body(ClaudeMessageResponse.class);

            return Optional.ofNullable(extractResult(response));
        } catch (Exception e) {
            log.warn("[실물 상품 인식] 호출 실패 - error={}", e.getMessage());
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

    private Map<String, Object> productTool() {
        Map<String, Object> itemSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string", "description", "브랜드명을 포함한 상품 이름, 예: \"오뚜기 진라면 매운맛\""),
                        "quantityText", Map.of("type", "string", "description", "포장에 적힌 중량/용량 표시, 예: \"500g\", \"1개\""),
                        "quantityValue", Map.of("type", "number", "description", "계산용 수치 (모르면 생략 가능)"),
                        "unit", Map.of("type", "string", "description", "단위, 예: g, 개, 봉 (모르면 생략 가능)"),
                        "categoryNameGuess", Map.of("type", "string", "description", "카테고리 추정, 예: 가공식품, 면류, 채소 등")
                ),
                "required", List.of("name")
        );

        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "items", Map.of("type", "array", "description", "사진에서 인식한 상품 항목 목록", "items", itemSchema)
                ),
                "required", List.of("items")
        );

        return Map.of(
                "name", TOOL_NAME,
                "description", "실물 상품 사진에서 인식한 식재료/상품 항목들을 기록한다.",
                "input_schema", inputSchema
        );
    }
}
