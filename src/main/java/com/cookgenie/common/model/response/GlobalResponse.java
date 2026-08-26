package com.cookgenie.common.model.response;

import lombok.Getter;

@Getter
public class GlobalResponse<T> {

    private final boolean success;
    private final T data;
    private final String message;

    private GlobalResponse(boolean success, T data, String message) {
        this.success = success;
        this.data = data;
        this.message = message;
    }

    public static <T> GlobalResponse<T> success(T data) {
        return new GlobalResponse<>(true, data, null);
    }

    public static <T> GlobalResponse<T> fail(String message) {
        return new GlobalResponse<>(false, null, message);
    }
}
