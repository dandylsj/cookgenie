package com.cookgenie.domain.ingredient.external;

import com.cookgenie.common.client.anthropic.ClaudeMessageRequest;
import com.cookgenie.common.client.anthropic.ClaudeMessageResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/** Claude(Anthropic Messages API)에게 식재료 100g 기준 평균 영양정보를 추정해서 물어보는 클라이언트. */
@Slf4j
@Component
public class ClaudeNutritionClient {

    private static final String TOOL_NAME = "record_nutrition_estimate";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public ClaudeNutritionClient(
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

    /** 식재료 이름으로 영양정보를 추정한다. 실패하거나 실제 식재료가 아니라고 판단되면 empty. */
    public Optional<NutritionEstimate> estimate(String ingredientName) {
        ClaudeMessageRequest request = new ClaudeMessageRequest(
                model,
                300,
                List.of(new ClaudeMessageRequest.Message(
                        "user",
                        "\"" + ingredientName + "\"이 어떤 음식/식재료인지 먼저 유추해봐. "
                                + "양파/계란 같은 순수 원재료뿐 아니라, 브랜드명+상품명이 붙은 가공식품이나 냉동식품 "
                                + "(예: \"하림 통살 유린기\" → 튀긴 닭가슴살 요리, \"하림 안심 꿔바로우\" → 탕수육 계열의 "
                                + "튀긴 돼지고기 요리, \"오뚜기 진라면\" → 라면, \"비비고 왕교자\" → 만두)도 이름 속 단어들로 "
                                + "충분히 추측 가능해. 브랜드나 정확한 제품 스펙을 모르더라도, 이름에서 유추한 음식 종류의 "
                                + "일반적인/평균적인 100g(액체류면 100ml) 기준 영양성분을 record_nutrition_estimate 도구로 "
                                + "알려줘. 정확하지 않아도 되니 대략적인 추정치면 충분해. "
                                + "isValidFood는 이름 안에 음식과 관련된 단서가 하나도 없어서 추정 자체가 완전히 불가능할 "
                                + "때만 false로 하고, 조금이라도 어떤 음식인지 짐작할 수 있다면 반드시 true로 하고 "
                                + "최선의 추정치를 내놔."
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
                .filter(input -> input != null)
                .map(input -> objectMapper.convertValue(input, NutritionEstimate.class))
                .findFirst()
                .orElse(null);
    }

    /**
     * 속성 순서를 일부러 숫자 추정값 → isValidFood 순으로 배치했다(LinkedHashMap으로 순서 고정).
     * 모델이 "이 이름이 어떤 음식인지" 추정치를 먼저 채우게 유도한 뒤, 그 추정이 실제로 가능했는지를
     * 마지막에 판단하게 해서 브랜드/상품명이 섞인 이름을 성급하게 무효 처리하는 걸 줄이기 위함.
     */
    private Map<String, Object> nutritionTool() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("referenceUnit", Map.of("type", "string", "enum", List.of("g", "ml"), "description", "기준 단위, 고체는 g 액체는 ml"));
        properties.put("calories", Map.of("type", "number", "description", "기준량당 열량(kcal)"));
        properties.put("carbohydrateG", Map.of("type", "number", "description", "기준량당 탄수화물(g)"));
        properties.put("proteinG", Map.of("type", "number", "description", "기준량당 단백질(g)"));
        properties.put("fatG", Map.of("type", "number", "description", "기준량당 지방(g)"));
        properties.put("sugarG", Map.of("type", "number", "description", "기준량당 당류(g)"));
        properties.put("sodiumMg", Map.of("type", "number", "description", "기준량당 나트륨(mg)"));
        properties.put("fiberG", Map.of("type", "number", "description", "기준량당 식이섬유(g)"));
        properties.put("isValidFood", Map.of(
                "type", "boolean",
                "description", "위에서 영양성분을 추정할 수 있었는지. 이름에서 음식 종류를 조금이라도 짐작할 수 있었다면 "
                        + "true, 음식과 관련된 단서가 전혀 없어서 추정 자체가 불가능했을 때만 false"
        ));

        Map<String, Object> inputSchema = new LinkedHashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        inputSchema.put("required", List.of("referenceUnit", "calories", "carbohydrateG", "proteinG", "fatG", "isValidFood"));

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", TOOL_NAME);
        tool.put("description", "이름에서 유추한 음식/식재료의 100g 또는 100ml 기준 평균적인 영양성분 추정치를 기록한다.");
        tool.put("input_schema", inputSchema);
        return tool;
    }
}
