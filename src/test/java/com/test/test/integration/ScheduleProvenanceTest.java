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

    /** 시드 JSON 의 문자열이 그대로 들어온다. 오타나 새 값이 와도 추정으로 잘못 분류되면 안 된다. */
    @Test
    @DisplayName("시드의 provenance 문자열을 읽는다")
    void parses_seed_values() {
        assertEquals(ScheduleProvenance.APPROX, ScheduleProvenance.from("approx"));
        assertEquals(ScheduleProvenance.SCRAPED, ScheduleProvenance.from("scraped"));
        assertEquals(ScheduleProvenance.MANUAL, ScheduleProvenance.from("manual"));
    }

    /**
     * 출처를 안 밝히면 <b>확정</b>으로 본다.
     * 기본값이 추정이면 멀쩡한 일정에까지 경고가 붙어, 경고 자체가 무시된다.
     */
    @Test
    @DisplayName("모르는 값·빈 값은 확정으로 본다 (경고 남발 방지)")
    void unknown_defaults_to_confirmed() {
        assertEquals(ScheduleProvenance.SCRAPED, ScheduleProvenance.from(null));
        assertEquals(ScheduleProvenance.SCRAPED, ScheduleProvenance.from(""));
        assertEquals(ScheduleProvenance.SCRAPED, ScheduleProvenance.from("무슨값인지모름"));
    }
}
