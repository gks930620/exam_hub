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

    public static ScheduleProvenance from(String raw) {
        if (raw == null || raw.isBlank()) {
            return SCRAPED;
        }
        return switch (raw.trim().toLowerCase()) {
            case "approx", "estimated" -> APPROX;
            case "manual" -> MANUAL;
            case "api" -> API;
            default -> SCRAPED;
        };
    }
}
