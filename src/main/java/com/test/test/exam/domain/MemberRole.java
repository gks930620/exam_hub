package com.test.test.exam.domain;

/**
 * 계정 권한.
 * ADMIN 은 수기 일정 입력·PENDING_REVIEW 승인 등 운영 기능을 쓴다(설계 08, 매니저 매뉴얼 참고).
 */
public enum MemberRole {
    USER,
    ADMIN
}
