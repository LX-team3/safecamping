package com.campin.common;

// 모든 API 응답을 같은 모양으로 감싼다 → 프론트는 success 만 보고 성공·실패를 나눌 수 있다
public record ApiResponse<T>(boolean success, T data, String message) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
