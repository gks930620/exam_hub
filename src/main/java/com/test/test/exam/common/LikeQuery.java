package com.test.test.exam.common;

/**
 * 사용자가 친 검색어를 <b>글자 그대로</b> 찾도록 다듬는다.
 *
 * <p>SQL 의 {@code LIKE} 에서 {@code %} 는 아무 글자들, {@code _} 는 한 글자를 뜻한다.
 * 그대로 넘기면 사용자가 친 {@code %} 하나가 <b>전체 목록</b>을 부르고,
 * {@code 정_처리} 가 {@code 정보처리기사} 를 집어 온다. 걸렀다고 믿는데 안 걸러진 목록을
 * 보는 것이고, 그게 제일 나쁜 종류의 거짓말이다 — 모르는 상태값을 400 으로 막아 둔 것과 같은 이유다.
 *
 * <p>2026-09-23 QA 실측: 시험 찾기와 커뮤니티 검색 둘 다 새고 있었다.
 *
 * <p>이스케이프 문자는 역슬래시이고, 질의에 {@code ESCAPE '\'} 를 같이 적어야 먹는다.
 */
public final class LikeQuery {

    private LikeQuery() {
    }

    /** {@code %}·{@code _}·역슬래시를 글자 그대로 찾도록 막는다. */
    public static String escape(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
