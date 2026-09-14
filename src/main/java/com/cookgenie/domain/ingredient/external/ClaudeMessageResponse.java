package com.cookgenie.domain.ingredient.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Anthropic Messages API 응답 바디. tool_choice로 강제한 경우 content 중 type="tool_use" 블록에 결과가 담긴다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaudeMessageResponse(List<ContentBlock> content) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentBlock(String type, String name, NutritionEstimate input) {
    }
}
