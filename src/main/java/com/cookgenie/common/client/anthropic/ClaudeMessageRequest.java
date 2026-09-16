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
    /** content는 순수 텍스트 프롬프트면 String, 이미지를 함께 보내면 텍스트+이미지 content block 목록(List)이다. */
    public record Message(String role, Object content) {

        public static Message text(String role, String text) {
            return new Message(role, text);
        }

        /** 텍스트 프롬프트와 base64 인코딩된 이미지 한 장을 함께 보내는 메시지를 만든다(비전 요청용). */
        public static Message withImage(String role, String text, String mediaType, String base64Data) {
            return new Message(role, List.of(
                    Map.of("type", "text", "text", text),
                    Map.of("type", "image", "source", Map.of(
                            "type", "base64",
                            "media_type", mediaType,
                            "data", base64Data
                    ))
            ));
        }
    }
}
