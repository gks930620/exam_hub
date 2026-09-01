package com.test.test.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 엔티티를 찾을 수 없을 때 발생하는 예외
 * HTTP 404 Not Found
 */
public class EntityNotFoundException extends BusinessException {

    public EntityNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    public static EntityNotFoundException of(String entityName, Long id) {
        return new EntityNotFoundException(entityName + "을(를) 찾을 수 없습니다: " + id);
    }

    public static EntityNotFoundException of(String entityName, String identifier) {
        return new EntityNotFoundException(entityName + "을(를) 찾을 수 없습니다: " + identifier);
    }
}

