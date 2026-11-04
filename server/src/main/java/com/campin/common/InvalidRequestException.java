package com.campin.common;

// 사용자 입력이 규칙에 맞지 않을 때 서비스에서 던진다 → GlobalExceptionHandler 가 400 으로 바꾼다
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
