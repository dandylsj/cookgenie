package com.cookgenie.common.client.anthropic;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/** Anthropic Messages API 요청 바디. https://docs.anthropic.com/en/api/messages */
public record ClaudeMessageRequest(
        String model,
        @JsonProperty("max_tokens") int maxTokens,
        List<Message> messages,
        List<Map<String, Object>> tools,
        @JsonProperty("tool_choice") Map<String, Object> toolChoice
) {
    public record Message(String role, String content) {
    }
}
