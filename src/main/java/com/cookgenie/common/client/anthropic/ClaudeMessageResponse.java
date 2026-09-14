package com.cookgenie.common.client.anthropic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/** Anthropic Messages API 응답 바디. tool_choice로 강제한 경우 content 중 type="tool_use" 블록의 input에 결과가 담긴다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaudeMessageResponse(List<ContentBlock> content) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentBlock(String type, String name, Map<String, Object> input) {
    }
}
