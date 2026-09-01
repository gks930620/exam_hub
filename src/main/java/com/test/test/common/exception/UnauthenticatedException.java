package com.test.test.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 로그인이 필요한 곳에 비로그인으로 접근 → 401.
 * 400(요청이 잘못됨)과 구분해야 프런트가 "로그인하세요"를 띄울 수 있다.
 */
public class UnauthenticatedException extends BusinessException {

    public UnauthenticatedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
    }
}
