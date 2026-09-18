package com.cookgenie.domain.auth.external;

/**
 * 카카오 로그인으로 얻은 사용자 식별 정보. 이메일은 별도 비즈 심사를 받지 않으면 대부분 제공되지 않아서
 * 요청하지 않고, 카카오 고유 id(providerId)와 닉네임만 사용한다.
 */
public record KakaoUserInfo(String id, String nickname) {
}
