package com.test.test.exam.domain;

/**
 * 이 일정을 <b>어디서 얻었는가</b>. 화면에 신뢰도를 표시하기 위한 것.
 *
 * <p><b>왜 필요한가</b>: 우리 시드에는 회차 패턴으로 <b>추정한 날짜</b>가 50종쯤 섞여 있다.
 * 추정치를 확정처럼 보여주면 "마감을 놓치지 않게 해준다"는 약속을 스스로 깬다 —
 * 사용자는 그 날짜를 믿고 준비하다가 실제 마감을 놓친다. <b>틀린 날짜는 없는 것보다 나쁘다.</b>
 * 그래서 추정치에는 "시행처 확인 필요"를 붙인다.
 */
public enum ScheduleProvenance {

    /** 공공 API 에서 받은 값 (큐넷 등) */
    API("공공 API", true),

    /** 시행처 사이트에서 실제로 읽어온 값 */
    SCRAPED("시행처 확인", true),

    /** 매니저가 공고를 보고 직접 넣은 값 */
    MANUAL("매니저 입력", true),

    /** <b>추정치</b> — 회차 패턴으로 계산한 날짜다. 실제와 다를 수 있다 */
    APPROX("추정 — 시행처 확인 필요", false);

    private final String label;
    private final boolean confirmed;

    ScheduleProvenance(String label, boolean confirmed) {
        this.label = label;
        this.confirmed = confirmed;
    }

    public String getLabel() {
        return label;
    }

    /** 시행처에서 확인된 값인가. false 면 화면에 경고를 띄운다. */
    public boolean isConfirmed() {
        return confirmed;
    }

    /**
     * 시드 문자열을 읽는다. {@code scraped:2026-07-28} 처럼 날짜가 붙은 형태는 {@code :} 앞만 본다.
     *
     * <p><b>빈 값·모르는 값은 추정치(APPROX)</b>다. 어디서 왔는지 모르는 날짜를 확정으로 보여주는 것이
     * "시행처 확인 필요"가 하나 더 붙는 것보다 위험하다 — 사용자는 확정 표시를 믿고 마감을 놓친다.
     * 시드는 출처를 반드시 적는다(52종 전부 적혀 있다).
     */
    public static ScheduleProvenance from(String raw) {
        if (raw == null || raw.isBlank()) {
            return APPROX;
        }
        String head = raw.trim().toLowerCase();
        int colon = head.indexOf(':');
        if (colon >= 0) {
            head = head.substring(0, colon).trim();
        }
        return switch (head) {
            case "approx", "estimated" -> APPROX;
            case "manual" -> MANUAL;
            case "api" -> API;
            case "scraped", "scrape", "web" -> SCRAPED;
            default -> APPROX;
        };
    }
}
