package com.cookgenie.domain.ingredient.external;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Claude(Anthropic Messages API)에게 식재료 100g 기준 평균 영양정보를 추정해서 물어보는 클라이언트. */
@Slf4j
@Component
public class ClaudeNutritionClient {

    private static final String TOOL_NAME = "record_nutrition_estimate";

    private final RestClient restClient;
    private final String model;

    public ClaudeNutritionClient(
            @Value("${anthropic.api-key}") String apiKey,
            @Value("${anthropic.model}") String model) {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.anthropic.com/v1/messages")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();
        this.model = model;
    }

    /** 식재료 이름으로 영양정보를 추정한다. 실패하거나 실제 식재료가 아니라고 판단되면 empty. */
    public Optional<NutritionEstimate> estimate(String ingredientName) {
        ClaudeMessageRequest request = new ClaudeMessageRequest(
                model,
                300,
                List.of(new ClaudeMessageRequest.Message(
                        "user",
                        "식재료 \"" + ingredientName + "\"의 100g(액체류면 100ml) 기준 평균적인 영양성분을 "
                                + "record_nutrition_estimate 도구를 호출해서 알려줘. "
                                + "실제로 존재하는 식재료가 아니거나 판단할 수 없으면 isValidFood를 false로 해줘."
                )),
                List.of(nutritionTool()),
                Map.of("type", "tool", "name", TOOL_NAME)
        );

        try {
            ClaudeMessageResponse response = restClient.post()
                    .body(request)
                    .retrieve()
                    .body(ClaudeMessageResponse.class);

            NutritionEstimate estimate = extractEstimate(response);
            if (estimate == null || !estimate.isValidFood()) {
                log.info("[Claude 영양정보 추정] 유효하지 않은 식재료로 판단됨 - name={}", ingredientName);
                return Optional.empty();
            }
            return Optional.of(estimate);
        } catch (Exception e) {
            log.warn("[Claude 영양정보 추정] 호출 실패 - name={}, error={}", ingredientName, e.getMessage());
            return Optional.empty();
        }
    }

    private NutritionEstimate extractEstimate(ClaudeMessageResponse response) {
        if (response == null || response.content() == null) {
            return null;
        }
        return response.content().stream()
                .filter(block -> "tool_use".equals(block.type()) && TOOL_NAME.equals(block.name()))
                .map(ClaudeMessageResponse.ContentBlock::input)
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> nutritionTool() {
        Map<String, Object> properties = Map.of(
                "isValidFood", Map.of("type", "boolean", "description", "실제로 존재하는 식재료/식품명이 맞는지"),
                "referenceUnit", Map.of("type", "string", "enum", List.of("g", "ml"), "description", "기준 단위, 고체는 g 액체는 ml"),
                "calories", Map.of("type", "number", "description", "기준량당 열량(kcal)"),
                "carbohydrateG", Map.of("type", "number", "description", "기준량당 탄수화물(g)"),
                "proteinG", Map.of("type", "number", "description", "기준량당 단백질(g)"),
                "fatG", Map.of("type", "number", "description", "기준량당 지방(g)"),
                "sugarG", Map.of("type", "number", "description", "기준량당 당류(g)"),
                "sodiumMg", Map.of("type", "number", "description", "기준량당 나트륨(mg)"),
                "fiberG", Map.of("type", "number", "description", "기준량당 식이섬유(g)")
        );

        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of("isValidFood", "referenceUnit", "calories", "carbohydrateG", "proteinG", "fatG")
        );

        return Map.of(
                "name", TOOL_NAME,
                "description", "식재료의 100g 또는 100ml 기준 평균적인 영양성분 추정치를 기록한다.",
                "input_schema", inputSchema
        );
    }
}
