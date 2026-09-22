package com.test.test.exam.service;

import com.test.test.exam.web.dto.MeDtos;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 내 시험 일정을 <b>내 달력에 넣을 수 있는 파일</b>(.ics)로 만든다.
 *
 * <p>이 서비스는 메일로 알려 주지만, 사람들이 실제로 일정을 보는 곳은 자기 휴대폰 달력이다.
 * 접수일을 거기 넣어 두면 우리 메일이 스팸함에 빠져도 알림이 울린다 —
 * <b>약속을 지키는 두 번째 줄</b>이다. 알림 하나에만 기대지 않는다.
 *
 * <p>iCalendar 는 구글·애플·아웃룩이 다 읽는 표준이라 연동을 따로 붙일 필요가 없다.
 * 대신 형식이 조금만 어긋나도 달력 앱이 <b>파일 전체를 조용히 거부한다</b> — 사용자는 왜 안
 * 들어가는지 모른다. 그래서 규칙을 {@code IcsCalendarTest} 로 못 박아 뒀다.
 */
public final class IcsCalendar {

    private IcsCalendar() {
    }

    /** 표준이 정한 줄 끝. LF 만 쓰면 거부하는 앱이 있다. */
    private static final String CRLF = "\r\n";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 우리가 만든 항목임을 UID 로 밝힌다 — 다시 받아도 두 개가 생기지 않게 */
    private static final String UID_DOMAIN = "@exam-hub";

    public static String render(List<MeDtos.CalendarEvent> events) {
        StringBuilder out = new StringBuilder();
        out.append("BEGIN:VCALENDAR").append(CRLF)
                .append("VERSION:2.0").append(CRLF)
                .append("PRODID:-//exam-hub//KR").append(CRLF)
                .append("CALSCALE:GREGORIAN").append(CRLF)
                .append("METHOD:PUBLISH").append(CRLF)
                .append("X-WR-CALNAME:내 시험 일정").append(CRLF);

        if (events != null) {
            for (MeDtos.CalendarEvent e : events) {
                appendEvent(out, e);
            }
        }
        return out.append("END:VCALENDAR").append(CRLF).toString();
    }

    private static void appendEvent(StringBuilder out, MeDtos.CalendarEvent e) {
        LocalDate day = LocalDate.parse(e.getDate().substring(0, 10));
        String title = (e.getName() + " " + e.getLabel()).trim();

        out.append("BEGIN:VEVENT").append(CRLF)
                // 같은 일정은 늘 같은 UID — 다시 받으면 겹쳐 쓰고 두 개가 안 생긴다.
                // 시각을 섞으면 받을 때마다 새 항목이 되어 달력이 금방 지저분해진다.
                .append("UID:").append(e.getCertificateId()).append('-')
                .append(e.getType()).append('-').append(day.format(DATE)).append(UID_DOMAIN).append(CRLF)
                .append("DTSTART;VALUE=DATE:").append(day.format(DATE)).append(CRLF)
                // 하루짜리는 끝이 다음 날이어야 한다. 같은 날로 두면 구글 캘린더에서 안 보인다.
                .append("DTEND;VALUE=DATE:").append(day.plusDays(1).format(DATE)).append(CRLF)
                .append("SUMMARY:").append(escape(title)).append(CRLF)
                .append("TRANSP:TRANSPARENT").append(CRLF)
                .append("END:VEVENT").append(CRLF);
    }

    /**
     * 쉼표·세미콜론·역슬래시는 iCalendar 의 구분자다.
     *
     * <p>안 막으면 그 항목에서 파일이 깨져 <b>뒤 일정이 통째로 사라진다</b> — 사용자는 일부만
     * 들어간 걸 보고 우리가 일정을 빠뜨렸다고 생각한다. 역슬래시를 <b>먼저</b> 바꾼다.
     */
    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n");
    }
}
