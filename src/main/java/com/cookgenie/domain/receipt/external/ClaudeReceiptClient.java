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

/** Claude(Anthropic Messages API, 비전)에게 영수증 사진을 보내 식재료 후보 목록을 추출시키는 클라이언트. */
@Slf4j
@Component
public class ClaudeReceiptClient {

    private static final String TOOL_NAME = "record_receipt_items";
    private static final int MAX_TOKENS = 1500;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public ClaudeReceiptClient(
            @Value("${anthropic.api-key}") String apiKey,
            @Value("${anthropic.model}") String model,
            ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.anthropic.com/v1/messages")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();
        this.model = model;
        this.objectMapper = objectMapper;
    }

    /** 영수증 이미지를 분석해서 식재료로 보이는 항목들을 추출한다. 실패하면 empty. */
    public Optional<ReceiptScanResult> scan(String mediaType, String base64Image) {
        String prompt = "이 이미지는 마트/편의점에서 받은 영수증이다. 영수증에 적힌 구매 품목 중 "
                + "식재료/음식으로 볼 수 있는 것만 골라 이름과 수량을 추출해줘. 영수증에 축약되거나 코드처럼 "
                + "적힌 상품명(예: \"국산돈목심600\")은 사람이 알아보기 쉬운 일반적인 이름(예: \"돼지 목심\")으로 "
                + "풀어서 써줘. 세제/휴지/생활용품처럼 식재료가 아닌 항목은 결과에서 제외해줘. "
                + "record_receipt_items 도구를 호출해서 결과를 알려줘.";

        ClaudeMessageRequest request = new ClaudeMessageRequest(
                model,
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
            log.warn("[영수증 인식] 호출 실패 - error={}", e.getMessage());
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
