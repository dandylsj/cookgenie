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

    /**
     * 식재료 이름으로 영양정보를 추정한다. 브랜드+상품명이 있는 가공식품이면 web_search로 실제 제품 정보를
     * 먼저 찾아보고, 못 찾으면 같은 종류 음식의 일반적인 영양성분으로 추정한다. 실패하거나 실제 식재료가
     * 아니라고 판단되면 empty.
     */
    public Optional<NutritionEstimate> estimate(String ingredientName) {
        ClaudeMessageRequest request = new ClaudeMessageRequest(
                model,
                1500,
                List.of(new ClaudeMessageRequest.Message(
                        "user",
                        "\"" + ingredientName + "\"의 100g(액체류면 100ml) 기준 영양성분을 알아내야 해. "
                                + "이름에 브랜드명+상품명이 붙어있어서 구체적인 제품(예: \"하림 통살 유린기\", "
                                + "\"하림 안심 꿔바로우\", \"오뚜기 진라면\")으로 보이면, web_search 도구로 그 제품의 "
                                + "실제 영양정보(제품 포장지/제조사 페이지에 적힌 값)를 먼저 검색해봐. 검색으로 정확한 "
                                + "값을 못 찾으면, 이름에서 유추한 음식 종류(예: 튀긴 닭가슴살 요리, 탕수육 계열 튀김, "
                                + "라면, 만두)의 일반적인/평균적인 영양성분으로 추정하면 돼 — 정확하지 않아도 되니 "
                                + "대략적인 추정치면 충분해. "
                                + "양파/계란처럼 검색이 필요 없는 순수 원재료면 바로 추정해도 돼. "
                                + "무엇을 하든 마지막에는 반드시 record_nutrition_estimate 도구를 호출해서 결과를 "
                                + "기록해. isValidFood는 이름 안에 음식과 관련된 단서가 하나도 없어서 추정 자체가 "
                                + "완전히 불가능할 때만 false로 하고, 조금이라도 어떤 음식인지 짐작할 수 있다면 "
                                + "반드시 true로 하고 최선의 추정치를 내놔."
                )),
                List.of(webSearchTool(), nutritionTool()),
                Map.of("type", "auto")
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
     * Anthropic 서버사이드 웹 검색 도구. Haiku는 동적 필터링이 붙은 최신 버전(web_search_20260209)을
     * 지원하지 않아서 기본형(web_search_20250305)을 쓴다. 검색 결과는 같은 응답 안에서 모델에게 바로
     * 주어지고, 모델이 이어서 record_nutrition_estimate를 호출하는 흐름이라 별도 라운드트립이 필요 없다.
     */
    private Map<String, Object> webSearchTool() {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "web_search_20250305");
        tool.put("name", "web_search");
        tool.put("max_uses", 3);
        return tool;
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
