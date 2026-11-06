package com.campin.common;

// 찾는 데이터가 없거나 남의 데이터일 때 서비스에서 던진다 → GlobalExceptionHandler 가 404 로 바꾼다
// (남의 목록을 403 으로 막으면 "그 id 가 있다"는 걸 알려 주게 되므로 없는 것처럼 404 로 답한다)
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
