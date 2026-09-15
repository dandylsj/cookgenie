package com.cookgenie.domain.recipe.external;

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

/** Claude(Anthropic Messages API)에게 보유 재료로 만들 수 있는 레시피를 생성시키는 클라이언트. */
@Slf4j
@Component
public class ClaudeRecipeClient {

    private static final String TOOL_NAME = "record_generated_recipe";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public ClaudeRecipeClient(
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

    /** 보유 재료 목록(과 선택적 요청사항)으로 레시피 하나를 생성한다. 실패하면 empty. */
    public Optional<GeneratedRecipe> generate(List<String> availableIngredients, String note) {
        String prompt = "다음 재료들을 활용해서 만들 수 있는 요리 레시피를 하나 제안해줘. "
                + "보유 재료: " + String.join(", ", availableIngredients) + ". "
                + "레시피에 꼭 저 재료만 써야 하는 건 아니고, 흔히 집에 있는 기본 양념(소금, 후추, 식용유, 간장 등)은 "
                + "추가로 써도 돼. record_generated_recipe 도구를 호출해서 결과를 알려줘."
                + (note != null && !note.isBlank() ? " 추가 요청사항: " + note : "");

        ClaudeMessageRequest request = new ClaudeMessageRequest(
                model,
                1200,
                List.of(new ClaudeMessageRequest.Message("user", prompt)),
                List.of(recipeTool()),
                Map.of("type", "tool", "name", TOOL_NAME)
        );

        try {
            ClaudeMessageResponse response = restClient.post()
                    .body(request)
                    .retrieve()
                    .body(ClaudeMessageResponse.class);

            GeneratedRecipe recipe = extractRecipe(response);
            return Optional.ofNullable(recipe);
        } catch (Exception e) {
            log.warn("[Claude 레시피 생성] 호출 실패 - ingredients={}, error={}", availableIngredients, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 냉장고 재료와 무관하게, 요청사항(note)에만 맞는 레시피를 자유롭게 생성한다. note가 없으면 아무 요리나
     * 추천한다. 실패하면 empty.
     */
    public Optional<GeneratedRecipe> generateFreeform(String note) {
        String prompt = "요청사항에 맞는 요리 레시피를 하나 제안해줘. "
                + (note != null && !note.isBlank()
                        ? "요청사항: " + note + ". "
                        : "특별한 요청사항은 없으니 아무 요리나 자유롭게 추천해줘. ")
                + "record_generated_recipe 도구를 호출해서 결과를 알려줘.";

        ClaudeMessageRequest request = new ClaudeMessageRequest(
                model,
                1200,
                List.of(new ClaudeMessageRequest.Message("user", prompt)),
                List.of(recipeTool()),
                Map.of("type", "tool", "name", TOOL_NAME)
        );

        try {
            ClaudeMessageResponse response = restClient.post()
                    .body(request)
                    .retrieve()
                    .body(ClaudeMessageResponse.class);

            return Optional.ofNullable(extractRecipe(response));
        } catch (Exception e) {
            log.warn("[Claude 자유 레시피 생성] 호출 실패 - note={}, error={}", note, e.getMessage());
            return Optional.empty();
        }
    }

    /** 유튜브 영상 제목/설명에서 레시피 정보를 추출한다. 설명이 부실하면 제목과 일반 요리 지식으로 추정한다. */
    public Optional<GeneratedRecipe> parseFromYoutube(String videoTitle, String videoDescription) {
        String description = videoDescription == null || videoDescription.isBlank()
                ? "(설명 없음)"
                : videoDescription.substring(0, Math.min(videoDescription.length(), 2000));

        String prompt = "다음은 유튜브 요리 영상의 제목과 설명이다. 이 영상의 레시피 정보를 최대한 정확하게 추출해줘. "
                + "설명에 재료나 조리법이 명확히 나와있지 않으면 제목과 일반적인 요리 지식을 바탕으로 추정해도 돼. "
                + "record_generated_recipe 도구를 호출해서 결과를 알려줘.\n\n"
                + "제목: " + videoTitle + "\n\n설명:\n" + description;

        ClaudeMessageRequest request = new ClaudeMessageRequest(
                model,
                1200,
                List.of(new ClaudeMessageRequest.Message("user", prompt)),
                List.of(recipeTool()),
                Map.of("type", "tool", "name", TOOL_NAME)
        );

        try {
            ClaudeMessageResponse response = restClient.post()
                    .body(request)
                    .retrieve()
                    .body(ClaudeMessageResponse.class);

            return Optional.ofNullable(extractRecipe(response));
        } catch (Exception e) {
            log.warn("[Claude 유튜브 레시피 파싱] 호출 실패 - title={}, error={}", videoTitle, e.getMessage());
            return Optional.empty();
        }
    }

    private GeneratedRecipe extractRecipe(ClaudeMessageResponse response) {
        if (response == null || response.content() == null) {
            return null;
        }
        return response.content().stream()
                .filter(block -> "tool_use".equals(block.type()) && TOOL_NAME.equals(block.name()))
                .map(ClaudeMessageResponse.ContentBlock::input)
                .filter(input -> input != null)
                .map(input -> objectMapper.convertValue(input, GeneratedRecipe.class))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> recipeTool() {
        Map<String, Object> ingredientItemSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string", "description", "재료명"),
                        "quantityText", Map.of("type", "string", "description", "사람이 읽는 표시용 분량, 예: \"1큰술\", \"200g\""),
                        "quantityValue", Map.of("type", "number", "description", "계산용 수치 (모르면 생략 가능)"),
                        "unit", Map.of("type", "string", "description", "단위, 예: g, 개, 큰술 (모르면 생략 가능)")
                ),
                "required", List.of("name", "quantityText")
        );

        Map<String, Object> properties = Map.of(
                "title", Map.of("type", "string", "description", "레시피 제목"),
                "cookingType", Map.of("type", "string", "description", "조리 종류, 예: 볶음/찌개/국/조림/구이/무침 등"),
                "servingSize", Map.of("type", "integer", "description", "몇 인분 기준인지"),
                "caloriesPerServing", Map.of("type", "integer", "description", "1인분 기준 열량(kcal)"),
                "carbohydrateG", Map.of("type", "number", "description", "1인분 기준 탄수화물(g)"),
                "proteinG", Map.of("type", "number", "description", "1인분 기준 단백질(g)"),
                "fatG", Map.of("type", "number", "description", "1인분 기준 지방(g)"),
                "ingredients", Map.of("type", "array", "description", "필요한 재료 목록", "items", ingredientItemSchema),
                "tags", Map.of("type", "array", "items", Map.of("type", "string"),
                        "description", "다이어트/고단백/저탄수/혼밥 등 태그 0~4개"),
                "instructions", Map.of("type", "array", "items", Map.of("type", "string"),
                        "description", "조리 순서를 단계별 문장으로")
        );

        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of("title", "cookingType", "servingSize", "caloriesPerServing",
                        "carbohydrateG", "proteinG", "fatG", "ingredients", "instructions")
        );

        return Map.of(
                "name", TOOL_NAME,
                "description", "제안한 레시피의 상세 내용을 기록한다.",
                "input_schema", inputSchema
        );
    }
}
