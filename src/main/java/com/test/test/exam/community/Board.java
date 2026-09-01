package com.test.test.exam.community;

/**
 * 게시판 — 고정 목록이라 테이블이 아니라 enum 이다.
 * 운영자가 게시판을 추가할 일이 생기면 그때 테이블로 승격한다(지금 만들면 관리 화면까지 따라온다).
 */
public enum Board {
    FREE("자유게시판", "무슨 이야기든"),
    QNA("질문게시판", "시험·공부 관련 질문"),
    REVIEW("시험후기", "응시 후기와 꿀팁");

    private final String name;
    private final String description;

    Board(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getBoardName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public static Board from(String code) {
        try {
            return valueOf(code.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new com.test.test.common.exception.BusinessRuleException("없는 게시판입니다: " + code);
        }
    }
}
