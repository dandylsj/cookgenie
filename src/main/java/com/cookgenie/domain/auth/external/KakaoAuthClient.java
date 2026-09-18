package com.cookgenie.domain.auth.external;

import com.cookgenie.common.exception.CustomException;
import com.cookgenie.common.exception.ErrorMessage;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * 카카오 로그인(인가 코드 방식)으로 인가 코드를 액세스 토큰으로 교환하고, 그 토큰으로 사용자 정보를
 * 조회하는 클라이언트. 이메일은 별도 비즈 심사 없이는 대부분 제공되지 않아서 요청하지 않고, 카카오
 * 고유 id + 닉네임만 받아온다.
 */
@Slf4j
@Component
public class KakaoAuthClient {

    private final RestClient authClient;
    private final RestClient apiClient;
    private final String restApiKey;
    private final String clientSecret;

    public KakaoAuthClient(
            @Value("${kakao.rest-api-key}") String restApiKey,
            @Value("${kakao.client-secret:}") String clientSecret) {
        this.authClient = RestClient.create("https://kauth.kakao.com");
        this.apiClient = RestClient.create("https://kapi.kakao.com");
        this.restApiKey = restApiKey;
        this.clientSecret = clientSecret;
    }

    /** 인가 코드로 액세스 토큰을 발급받고, 그 토큰으로 사용자 정보를 조회한다. 실패하면 KAKAO_LOGIN_FAILED. */
    public KakaoUserInfo getUserInfo(String code, String redirectUri) {
        String accessToken = requestAccessToken(code, redirectUri);
        return requestUserInfo(accessToken);
    }

    private String requestAccessToken(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", restApiKey);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }

        try {
            TokenResponse response = authClient.post()
                    .uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || response.accessToken() == null) {
                throw new CustomException(ErrorMessage.KAKAO_LOGIN_FAILED);
            }
            return response.accessToken();
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[카카오 로그인] 토큰 발급 실패 - error={}", e.getMessage());
            throw new CustomException(ErrorMessage.KAKAO_LOGIN_FAILED);
        }
    }

    private KakaoUserInfo requestUserInfo(String accessToken) {
        try {
            UserMeResponse response = apiClient.get()
                    .uri("/v2/user/me")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(UserMeResponse.class);
            if (response == null || response.id() == null) {
                throw new CustomException(ErrorMessage.KAKAO_LOGIN_FAILED);
            }
            String nickname = response.kakaoAccount() != null && response.kakaoAccount().profile() != null
                    ? response.kakaoAccount().profile().nickname()
                    : null;
            return new KakaoUserInfo(String.valueOf(response.id()), nickname);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[카카오 로그인] 사용자 정보 조회 실패 - error={}", e.getMessage());
            throw new CustomException(ErrorMessage.KAKAO_LOGIN_FAILED);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UserMeResponse(Long id, @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoAccount(Profile profile) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Profile(String nickname) {
    }
}
