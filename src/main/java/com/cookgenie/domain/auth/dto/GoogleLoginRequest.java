package com.cookgenie.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GoogleLoginRequest {

    @NotBlank(message = "인가 코드가 필요합니다.")
    private String code;

    /** 인가 코드를 받을 때 사용한 redirect_uri와 정확히 같아야 한다(구글 토큰 발급 요건). */
    @NotBlank(message = "redirectUri가 필요합니다.")
    private String redirectUri;
}
