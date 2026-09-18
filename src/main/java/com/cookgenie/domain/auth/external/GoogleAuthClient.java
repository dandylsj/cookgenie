package com.cookgenie.domain.auth.external;

import com.cookgenie.common.exception.CustomException;
import com.cookgenie.common.exception.ErrorMessage;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * 구글 로그인(인가 코드 방식)으로 인가 코드를 액세스 토큰으로 교환하고, 그 토큰으로 사용자 정보를
 * 조회하는 클라이언트. 구글은 카카오와 달리 client_secret이 항상 필요하다(웹 애플리케이션 클라이언트
 * 타입 기준).
 */
@Slf4j
@Component
public class GoogleAuthClient {

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 8000;

    private final RestClient tokenClient;
    private final RestClient userInfoClient;
    private final String clientId;
    private final String clientSecret;

    public GoogleAuthClient(
            @Value("${google.client-id}") String clientId,
            @Value("${google.client-secret}") String clientSecret) {
        ClientHttpRequestFactory requestFactory = timeoutRequestFactory();
        this.tokenClient = RestClient.builder().baseUrl("https://oauth2.googleapis.com").requestFactory(requestFactory).build();
        this.userInfoClient = RestClient.builder().baseUrl("https://www.googleapis.com").requestFactory(requestFactory).build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /** 인가 코드로 액세스 토큰을 발급받고, 그 토큰으로 사용자 정보를 조회한다. 실패하면 GOOGLE_LOGIN_FAILED. */
    public GoogleUserInfo getUserInfo(String code, String redirectUri) {
        String accessToken = requestAccessToken(code, redirectUri);
        return requestUserInfo(accessToken);
    }

    private String requestAccessToken(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);

        try {
            TokenResponse response = tokenClient.post()
                    .uri("/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || response.accessToken() == null) {
                throw new CustomException(ErrorMessage.GOOGLE_LOGIN_FAILED);
            }
            return response.accessToken();
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[구글 로그인] 토큰 발급 실패 - error={}", e.getMessage());
            throw new CustomException(ErrorMessage.GOOGLE_LOGIN_FAILED);
        }
    }

    private GoogleUserInfo requestUserInfo(String accessToken) {
        try {
            UserInfoResponse response = userInfoClient.get()
                    .uri("/oauth2/v3/userinfo")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(UserInfoResponse.class);
            if (response == null || response.sub() == null) {
                throw new CustomException(ErrorMessage.GOOGLE_LOGIN_FAILED);
            }
            return new GoogleUserInfo(response.sub(), response.name());
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[구글 로그인] 사용자 정보 조회 실패 - error={}", e.getMessage());
            throw new CustomException(ErrorMessage.GOOGLE_LOGIN_FAILED);
        }
    }

    /** 응답이 늦어질 때 앞단 프록시가 먼저 연결을 끊어버려 원인 파악이 어려운 에러로 보이는 문제를 막기 위해
     * 짧은 타임아웃을 명시적으로 건다(KakaoAuthClient와 동일한 이유). */
    private static ClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return factory;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UserInfoResponse(String sub, String name, String email) {
    }
}
