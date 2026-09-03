package com.test.test.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 같은 곳에서 실패를 너무 자주 반복 → 429.
 * 매니저 로그인 대입 공격 차단용 — 401 과 구분해야 "비밀번호가 틀렸다"와 "잠겼다"를 다르게 안내할 수 있다.
 */
public class TooManyAttemptsException extends BusinessException {

    public TooManyAttemptsException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS");
    }
}
