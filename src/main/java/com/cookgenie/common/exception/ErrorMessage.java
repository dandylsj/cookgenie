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
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    FRIDGE_NOT_FOUND(HttpStatus.NOT_FOUND, "냉장고를 찾을 수 없습니다."),
    FRIDGE_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "냉장고 재료를 찾을 수 없습니다."),
    INGREDIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "식재료를 찾을 수 없습니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다."),
    INGREDIENT_IN_USE(HttpStatus.CONFLICT, "냉장고에 등록되어 있는 식재료는 삭제할 수 없습니다."),
    NUTRITION_ESTIMATION_FAILED(HttpStatus.BAD_GATEWAY, "영양정보 추정에 실패했습니다. 잠시 후 다시 시도해주세요."),
    RECIPE_NOT_FOUND(HttpStatus.NOT_FOUND, "레시피를 찾을 수 없습니다."),

    INVALID_INVITE_CODE(HttpStatus.BAD_REQUEST, "유효하지 않거나 만료된 초대코드입니다."),
    ALREADY_FRIDGE_MEMBER(HttpStatus.CONFLICT, "이미 참여하고 있는 냉장고입니다."),
    SHOPPING_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "장보기 항목을 찾을 수 없습니다."),
    FRIDGE_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "냉장고 멤버를 찾을 수 없습니다."),
    CANNOT_KICK_SELF(HttpStatus.BAD_REQUEST, "자기 자신은 강퇴할 수 없습니다."),
    CANNOT_LEAVE_AS_OWNER(HttpStatus.BAD_REQUEST, "소유자는 냉장고를 탈퇴할 수 없습니다. 냉장고를 삭제하거나 다른 멤버에게 소유권을 넘겨주세요."),

    RECEIPT_IMAGE_REQUIRED(HttpStatus.BAD_REQUEST, "영수증 이미지가 필요합니다."),
    RECEIPT_IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "이미지 용량이 너무 큽니다(최대 10MB)."),
    UNSUPPORTED_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 이미지 형식입니다(JPEG/PNG/WEBP만 가능)."),
    RECEIPT_SCAN_FAILED(HttpStatus.BAD_GATEWAY, "영수증 인식에 실패했습니다. 잠시 후 다시 시도해주세요."),
    IMAGE_REQUIRED(HttpStatus.BAD_REQUEST, "이미지가 필요합니다."),
    IMAGE_RECOGNITION_FAILED(HttpStatus.BAD_GATEWAY, "이미지 인식에 실패했습니다. 잠시 후 다시 시도해주세요."),

    FRIDGE_HAS_NO_ITEMS(HttpStatus.BAD_REQUEST, "냉장고에 재료가 없습니다."),
    RECIPE_NOTE_REQUIRED(HttpStatus.BAD_REQUEST, "냉장고 재료를 사용하지 않을 경우 원하는 레시피 내용을 입력해야 합니다."),
    RECIPE_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "레시피 생성에 실패했습니다. 잠시 후 다시 시도해주세요."),
    YOUTUBE_VIDEO_NOT_FOUND(HttpStatus.NOT_FOUND, "유튜브 영상을 찾을 수 없습니다."),

    MEAL_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "식단 기록을 찾을 수 없습니다."),
    MEAL_LOG_RECIPE_REQUIRED(HttpStatus.BAD_REQUEST, "레시피로 기록하려면 recipeId가 필요합니다."),
    MEAL_LOG_ITEMS_REQUIRED(HttpStatus.BAD_REQUEST, "직접 입력으로 기록하려면 재료 목록이 최소 1개 필요합니다."),

    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 존재하는 이메일입니다."),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 존재하는 아이디입니다."),
    WITHDRAWN_USER(HttpStatus.CONFLICT, "탈퇴한 계정입니다."),

    SOCIAL_LOGIN_ACCOUNT(HttpStatus.UNPROCESSABLE_ENTITY, "소셜 로그인으로 가입된 계정입니다."),
    NOT_GUEST_ACCOUNT(HttpStatus.BAD_REQUEST, "게스트 계정이 아닙니다.");

    private final HttpStatus status;
    private final String message;
}
