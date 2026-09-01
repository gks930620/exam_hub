package com.test.test.exam.auth;

import java.lang.annotation.*;

/**
 * 컨트롤러 파라미터에 로그인한 {@link com.test.test.exam.domain.Member} 를 주입한다.
 * 비로그인 요청이면 {@code 401 UNAUTHENTICATED} 로 끊는다 — 구 {@code @CurrentDevice} 를 대체.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentMember {
    /** false 면 비로그인도 통과시키고 null 을 넣는다(공개 API 에서 "로그인했으면 개인화" 용). */
    boolean required() default true;
}
