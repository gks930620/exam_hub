package com.test.test.exam.service;

import com.test.test.exam.web.dto.MeDtos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>내 시험 일정을 내 달력에 넣는다.</b>
 *
 * <p>이 서비스는 메일로 알려 주지만, 사람들이 실제로 일정을 보는 곳은 자기 휴대폰 달력이다.
 * 접수일을 거기 넣어 두면 우리 메일이 스팸함에 빠져도 알림이 울린다 — <b>약속을 지키는 두 번째 줄</b>이다.
 *
 * <p>iCalendar(.ics)는 구글 캘린더·애플 캘린더·아웃룩이 다 읽는 표준이라 연동을 따로 붙일 필요가 없다.
 *
 * <p>형식이 조금만 어긋나도 달력 앱이 <b>파일 전체를 조용히 거부한다</b> — 사용자는 왜 안 들어가는지
 * 모른다. 그래서 규칙을 테스트로 못 박는다. 순수 생성이라 컨벤션 §6 의 예외다.
 */
class IcsCalendarTest {

    private static MeDtos.CalendarEvent event(String date, String type, String name, String label) {
        return new MeDtos.CalendarEvent(date, type, 1L, name, label);
    }

    private static final List<MeDtos.CalendarEvent> TWO = List.of(
            event("2026-10-19", "REG_END", "정보처리기사", "3회 실기 접수 마감"),
            event("2026-10-24", "EXAM", "정보처리기사", "3회 실기 시험"));

    @Test
    @DisplayName("달력 앱이 읽는 뼈대를 갖춘다")
    void has_the_skeleton_calendars_require() {
        String ics = IcsCalendar.render(TWO);

        assertThat(ics).startsWith("BEGIN:VCALENDAR");
        assertThat(ics).contains("VERSION:2.0");
        assertThat(ics).endsWith("END:VCALENDAR\r\n");
    }

    /** 줄 끝이 CRLF 가 아니면 거부하는 달력 앱이 있다 — 표준이 그렇게 정해져 있다. */
    @Test
    @DisplayName("줄 끝은 CRLF 다")
    void lines_end_with_crlf() {
        String ics = IcsCalendar.render(TWO);

        assertThat(ics.replace("\r\n", "")).doesNotContain("\n");
    }

    @Test
    @DisplayName("일정 하나에 하루짜리 항목 하나")
    void one_event_per_day() {
        String ics = IcsCalendar.render(TWO);

        assertThat(ics.split("BEGIN:VEVENT", -1)).hasSize(3);   // 앞 조각 + 2건
        assertThat(ics).contains("DTSTART;VALUE=DATE:20261019");
        assertThat(ics).contains("DTSTART;VALUE=DATE:20261024");
    }

    /**
     * 하루짜리 일정은 끝 날짜가 <b>다음 날</b>이어야 한다. 같은 날로 두면 구글 캘린더에서
     * 항목이 아예 안 보이거나 0분짜리로 들어간다.
     */
    @Test
    @DisplayName("하루 일정의 끝은 다음 날이다")
    void all_day_events_end_the_next_day() {
        String ics = IcsCalendar.render(List.of(event("2026-10-31", "EXAM", "시험", "시험")));

        assertThat(ics).contains("DTSTART;VALUE=DATE:20261031");
        assertThat(ics).contains("DTEND;VALUE=DATE:20261101");
    }

    @Test
    @DisplayName("제목에 시험 이름과 무슨 날인지 같이 넣는다")
    void title_says_which_exam_and_what_day() {
        String ics = IcsCalendar.render(TWO);

        assertThat(ics).contains("SUMMARY:정보처리기사 3회 실기 접수 마감");
    }

    /**
     * 쉼표·세미콜론·역슬래시는 iCalendar 의 구분자라 반드시 이스케이프해야 한다.
     * 안 하면 그 항목부터 파일이 깨져 <b>뒤 일정이 통째로 사라진다.</b>
     */
    @Test
    @DisplayName("구분자가 든 이름을 이스케이프한다 — 안 하면 뒤 일정이 통째로 사라진다")
    void separators_are_escaped() {
        String ics = IcsCalendar.render(List.of(
                event("2026-10-19", "EXAM", "정보처리기사, 산업기사; 실기\\특별", "1회 시험")));

        assertThat(ics).contains("정보처리기사\\, 산업기사\\; 실기\\\\특별");
    }

    /** 같은 일정을 두 번 받아도 달력에 두 개가 생기면 안 된다 — UID 가 같아야 겹쳐 쓴다. */
    @Test
    @DisplayName("같은 일정은 같은 UID 를 갖는다 — 다시 받아도 두 개가 안 생긴다")
    void same_event_keeps_the_same_uid() {
        String first = IcsCalendar.render(TWO);
        String again = IcsCalendar.render(TWO);

        assertThat(uids(first)).isEqualTo(uids(again));
        assertThat(uids(first)).hasSize(2).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("일정이 없어도 빈 달력을 준다 — 깨진 파일을 주지 않는다")
    void empty_calendar_is_still_valid() {
        String ics = IcsCalendar.render(List.of());

        assertThat(ics).startsWith("BEGIN:VCALENDAR").endsWith("END:VCALENDAR\r\n");
        assertThat(ics).doesNotContain("BEGIN:VEVENT");
    }

    private static List<String> uids(String ics) {
        return ics.lines().filter(l -> l.startsWith("UID:")).toList();
    }
}
