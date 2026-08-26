package com.cookgenie.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorMessage {

    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "비밀번호가 일치하지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    FRIDGE_NOT_FOUND(HttpStatus.NOT_FOUND, "냉장고를 찾을 수 없습니다."),
    FRIDGE_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "냉장고 재료를 찾을 수 없습니다."),
    INGREDIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "식재료를 찾을 수 없습니다."),

    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 존재하는 이메일입니다."),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 존재하는 아이디입니다."),

    SOCIAL_LOGIN_ACCOUNT(HttpStatus.UNPROCESSABLE_ENTITY, "소셜 로그인으로 가입된 계정입니다.");

    private final HttpStatus status;
    private final String message;
}
