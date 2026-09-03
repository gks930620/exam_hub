package com.test.test.integration;

import com.test.test.exam.domain.ScheduleProvenance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 일정의 <b>출처</b> 규칙.
 *
 * <p>우리 시드에는 회차 패턴으로 <b>추정한 날짜</b>가 50종쯤 섞여 있다. 추정치를 확정처럼
 * 보여주면 사용자는 그 날짜를 믿고 준비하다가 실제 마감을 놓친다 —
 * <b>틀린 날짜는 없는 것보다 나쁘다.</b> 그래서 추정치만 골라 경고할 수 있어야 한다.
 */
class ScheduleProvenanceTest {

    @Test
    @DisplayName("추정치만 '확인 필요'다 — 나머지는 확정")
    void only_approx_is_unconfirmed() {
        assertFalse(ScheduleProvenance.APPROX.isConfirmed(), "추정치가 확정으로 취급된다");
        assertTrue(ScheduleProvenance.API.isConfirmed());
        assertTrue(ScheduleProvenance.SCRAPED.isConfirmed());
        assertTrue(ScheduleProvenance.MANUAL.isConfirmed());
    }

    /** 시드 JSON 의 문자열이 그대로 들어온다. 날짜가 붙은 형태({@code scraped:2026-07-28})도 있다. */
    @Test
    @DisplayName("시드의 provenance 문자열을 읽는다 — ':' 뒤의 날짜는 무시")
    void parses_seed_values() {
        assertEquals(ScheduleProvenance.APPROX, ScheduleProvenance.from("approx"));
        assertEquals(ScheduleProvenance.SCRAPED, ScheduleProvenance.from("scraped"));
        assertEquals(ScheduleProvenance.MANUAL, ScheduleProvenance.from("manual"));
        assertEquals(ScheduleProvenance.API, ScheduleProvenance.from("api"));
        assertEquals(ScheduleProvenance.SCRAPED, ScheduleProvenance.from("scraped:2026-07-28"));
        assertEquals(ScheduleProvenance.APPROX, ScheduleProvenance.from("approx:2026-07-28"),
                "날짜가 붙은 추정치가 확정으로 읽힌다");
        assertEquals(ScheduleProvenance.MANUAL, ScheduleProvenance.from(" Manual : 2026-08-01 "));
    }

    /**
     * 출처를 안 밝히면 <b>추정</b>으로 본다.
     * 어디서 왔는지 모르는 날짜를 확정으로 보여주는 것이 "시행처 확인 필요"가 하나 더 붙는 것보다 위험하다 —
     * 사용자는 확정 표시를 믿고 마감을 놓친다. 시드는 출처를 반드시 적는다.
     */
    @Test
    @DisplayName("모르는 값·빈 값은 추정으로 본다 (출처 불명을 확정으로 보여주지 않는다)")
    void unknown_defaults_to_approx() {
        assertEquals(ScheduleProvenance.APPROX, ScheduleProvenance.from(null));
        assertEquals(ScheduleProvenance.APPROX, ScheduleProvenance.from(""));
        assertEquals(ScheduleProvenance.APPROX, ScheduleProvenance.from("무슨값인지모름"));
    }
}
