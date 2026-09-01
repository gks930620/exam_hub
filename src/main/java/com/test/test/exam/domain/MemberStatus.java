package com.test.test.exam.domain;

/** 계정 상태. 탈퇴해도 행을 지우지 않는다 — 글·댓글의 작성자 참조가 끊기면 안 되기 때문. */
public enum MemberStatus {
    ACTIVE,
    WITHDRAWN
}
