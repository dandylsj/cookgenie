package com.cookgenie.domain.auth.external;

/**
 * 구글 로그인으로 얻은 사용자 식별 정보. 구글은 이메일을 항상 제공하지만, 카카오와 동일한 정책(이메일로
 * 기존 로컬 계정에 자동 연결하지 않음, KakaoAuthClient 참고)을 따르기 위해 이메일은 저장하지 않고
 * 구글 고유 id(sub)와 닉네임만 사용한다.
 */
public record GoogleUserInfo(String id, String nickname) {
}
