package com.test.test.exam.admin;

import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.Series;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 기관명 매칭 규칙 — 매니저 판정(자동인가)과 일정 없음 이유(크롤링 예정인가)가 <b>같은 규칙</b>을 쓴다.
 *
 * <p>시드마다 기관명 표기가 다르다: "한국방송통신전파진흥원(KCA)" / "한국방송통신전파진흥원",
 * "한국보건의료인국가시험원(국시원)" / "한국보건의료인국가시험원". 괄호 접미사를 떼고 양방향 포함으로 본다.
 */
class AgencyMatcherTest {

    private static Certificate certWithAgency(String agency) {
        return Certificate.builder().name("x").slug("x").series(Series.ETC).agency(agency).build();
    }

    @Test
    @DisplayName("괄호 접미사는 무시한다")
    void parentheses_are_ignored() {
        assertTrue(AgencyMatcher.matches("한국방송통신전파진흥원(KCA)", Set.of("한국방송통신전파진흥원")));
        assertTrue(AgencyMatcher.matches("한국보건의료인국가시험원", Set.of("한국보건의료인국가시험원(국시원)")));
        assertTrue(AgencyMatcher.matches("한국산업인력공단(큐넷)", Set.of("한국산업인력공단")));
    }

    @Test
    @DisplayName("한쪽이 다른 쪽을 포함하면 같은 기관으로 본다 (양방향)")
    void bidirectional_contains() {
        assertTrue(AgencyMatcher.matches("금융투자협회 / 한국금융연수원", Set.of("한국금융연수원")));
        assertTrue(AgencyMatcher.matches("YBM", Set.of("G-TELP KOREA / YBM")));
        assertTrue(AgencyMatcher.matches("서울대학교 TEPS관리위원회", Set.of("서울대학교 teps관리위원회")));
    }

    @Test
    @DisplayName("다른 기관은 섞이지 않는다")
    void different_agencies_do_not_match() {
        assertFalse(AgencyMatcher.matches("국립국어원", Set.of("국립국제교육원")));
        assertFalse(AgencyMatcher.matches(null, Set.of("YBM")));
        assertFalse(AgencyMatcher.matches("", Set.of("YBM")));
        assertFalse(AgencyMatcher.matches("YBM", Set.of()));
        assertFalse(AgencyMatcher.matches("YBM", Set.of("")));
    }

    @Test
    @DisplayName("일정 없음 이유 판정도 같은 규칙이다 — 괄호가 있든 없든 크롤링 예정")
    void no_schedule_reason_uses_same_rule() {
        // 예시는 KAIT 로 둔다 — ICQA 는 정기시험 일정을 구글 캘린더로만 안내해 크롤링 예정에서 뺐다(2026-09-09)
        assertEquals(NoScheduleReason.CRAWL_PLANNED, NoScheduleReason.of(certWithAgency("한국정보통신진흥협회(KAIT)")));
        assertEquals(NoScheduleReason.CRAWL_PLANNED, NoScheduleReason.of(certWithAgency("한국정보통신진흥협회")));
        assertEquals(NoScheduleReason.CRAWL_PLANNED, NoScheduleReason.of(certWithAgency("한국방송통신전파진흥원")));
        assertEquals(NoScheduleReason.CRAWL_PLANNED, NoScheduleReason.of(certWithAgency("한국방송통신전파진흥원(KCA)")));
        assertEquals(NoScheduleReason.MANUAL, NoScheduleReason.of(certWithAgency("국사편찬위원회")));
    }
}
