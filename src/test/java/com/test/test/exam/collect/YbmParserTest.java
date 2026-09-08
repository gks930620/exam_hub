package com.test.test.exam.collect;

import com.test.test.exam.domain.ExamType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * YBM 어학시험 일정 파싱 — TOEIC·TOEIC S&amp;W·TOEIC Bridge·JPT·SJPT·TSC.
 *
 * <p>여섯 페이지가 <b>도메인만 다르고 표는 같다</b>. 다만 표가 두 모양이라 둘 다 시험한다.
 * <ul>
 *   <li>회차 있음 — {@code 회차 | 시험일시 | 성적발표일시 | 접수기간}</li>
 *   <li>회차 없음 — {@code 시험일시 | 성적발표일 | 접수기간}</li>
 * </ul>
 *
 * <p>가장 조심할 곳은 <b>접수기간 칸에 정기접수와 특별추가가 같이 오는 것</b>이다.
 * 특별추가를 접수기간으로 잡으면 사용자가 정규 접수를 놓친다.
 */
class YbmParserTest {

    private static List<CollectedSchedule> parse(String fixture, String targetCode) throws IOException {
        String html = new String(
                new ClassPathResource("fixtures/" + fixture).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        return new YbmScheduleSource().parse(html, YbmScheduleSource.target(targetCode));
    }

    private static CollectedSchedule find(List<CollectedSchedule> list, String name, int round) {
        return list.stream()
                .filter(s -> s.certificateName().equals(name) && s.round() == round)
                .findFirst().orElse(null);
    }

    @Test
    @DisplayName("TSC: 회차·시험일·접수기간·발표일을 읽는다")
    void reads_tsc() throws IOException {
        List<CollectedSchedule> out = parse("ybm_tsc_schedule.html", "TSC");

        assertFalse(out.isEmpty(), "한 건도 못 뽑았다 — 표 구조가 바뀌었나?");
        CollectedSchedule s = find(out, "TSC 중국어 말하기시험", 332);
        assertNotNull(s, "제332회가 없다");
        assertEquals("M0343", s.sourceCode(), "마스터와 다른 코드를 주면 같은 시험이 하나 더 생긴다");
        assertEquals(2026, s.year());
        assertEquals("2026-09-12", s.examStartDate().toString(), "시험일이 틀렸다");
        assertEquals("2026-08-03T10:00", s.regStartAt().toString(), "접수 시작이 틀렸다");
        assertEquals("2026-09-10T23:59", s.regEndAt().toString(), "접수 마감이 틀렸다");
        assertEquals("2026-09-23", s.resultDate().toString(), "발표일이 틀렸다");
        assertEquals(ExamType.WRITTEN, s.examType(), "이 시험들은 필기·실기 구분이 없다");
    }

    /**
     * TOEIC 은 접수 칸에 <b>정기접수와 특별추가가 같이</b> 온다.
     * 특별추가(2026.08.12~08.20)를 접수기간으로 잡으면 사용자가 정규 접수를 놓친다.
     */
    @Test
    @DisplayName("TOEIC: 특별추가가 아니라 정기접수를 쓴다")
    void uses_regular_registration_not_the_late_one() throws IOException {
        List<CollectedSchedule> out = parse("ybm_toeic_schedule.html", "TOEIC");

        CollectedSchedule s = find(out, "TOEIC 토익", 576);
        assertNotNull(s, "제576회가 없다 — 회차 앞의 ★ 때문에 못 읽었나?");
        assertEquals("2026-07-06T10:00", s.regStartAt().toString(), "정기접수 시작이 아니다");
        assertEquals("2026-08-10T10:00", s.regEndAt().toString(), "특별추가 마감을 잡았다");
        assertEquals("2026-08-23", s.examStartDate().toString());
    }

    /** 시행처가 매긴 회차를 그대로 쓴다 — 예전처럼 시험일로 회차를 만들면 화면에 "20261011회"가 뜬다. */
    @Test
    @DisplayName("JPT: 시행처가 매긴 회차를 그대로 쓴다")
    void keeps_the_agency_round_number() throws IOException {
        List<CollectedSchedule> out = parse("ybm_jpt_schedule.html", "JPT");

        CollectedSchedule s = find(out, "JPT 일본어능력시험", 413);
        assertNotNull(s, "제413회가 없다");
        assertEquals("2026-09-13", s.examStartDate().toString());
        assertEquals("2026-07-27T10:00", s.regStartAt().toString());
        assertEquals("2026-09-08T12:00", s.regEndAt().toString());
    }

    /**
     * Bridge 는 시행처가 회차를 안 매긴다. <b>회차를 지어내지 않고</b> 시험일을 멱등 키로 쓴다
     * (화면에는 회차로 안 보인다 — {@code ExamSchedule.roundLabel()}).
     */
    @Test
    @DisplayName("Bridge: 회차가 없는 표도 읽는다")
    void reads_the_table_without_rounds() throws IOException {
        List<CollectedSchedule> out = parse("ybm_bridge_schedule.html", "TOEIC-BRIDGE");

        CollectedSchedule s = find(out, "TOEIC Bridge", 20261018);
        assertNotNull(s, "2026-10-18 회차가 없다");
        assertEquals("M0326", s.sourceCode());
        assertEquals("2026-10-18", s.examStartDate().toString());
        assertEquals("2026-09-07T10:00", s.regStartAt().toString());
        assertEquals("2026-10-05T23:59", s.regEndAt().toString());
    }

    /** S&amp;W 페이지 하나가 스피킹·라이팅 <b>두 종목</b>을 먹인다 — 같은 날 둘 다 치른다. */
    @Test
    @DisplayName("S&W: 한 페이지가 스피킹·라이팅 둘 다 채운다")
    void swt_page_feeds_two_exams() throws IOException {
        List<CollectedSchedule> out = parse("ybm_swt_schedule.html", "TOEIC-SW");

        assertNotNull(find(out, "TOEIC Speaking 토익스피킹", 20260912), "스피킹 2026-09-12 이 없다");
        assertNotNull(find(out, "TOEIC Writing 토익라이팅", 20260912), "라이팅 2026-09-12 이 없다");
    }

    /**
     * S&amp;W 표는 <b>같은 시험일이 여러 줄</b>로 온다(스피킹 전용·라이팅·통합이 각각 한 줄).
     * 그대로 넣으면 같은 날짜가 중복으로 쌓인다.
     */
    @Test
    @DisplayName("S&W: 같은 시험일이 여러 줄이어도 한 번만 넣는다")
    void duplicate_rows_are_collapsed() throws IOException {
        List<CollectedSchedule> out = parse("ybm_swt_schedule.html", "TOEIC-SW");

        long sameDay = out.stream()
                .filter(s -> s.certificateName().startsWith("TOEIC Speaking"))
                .filter(s -> s.examStartDate().toString().equals("2026-09-13"))
                .count();
        assertEquals(1, sameDay, "2026-09-13 이 여러 번 들어왔다");
    }

    @Test
    @DisplayName("접수가 시험보다 늦지 않다")
    void registration_precedes_exam() throws IOException {
        for (String[] pair : new String[][]{
                {"ybm_tsc_schedule.html", "TSC"}, {"ybm_toeic_schedule.html", "TOEIC"},
                {"ybm_jpt_schedule.html", "JPT"}, {"ybm_bridge_schedule.html", "TOEIC-BRIDGE"},
                {"ybm_swt_schedule.html", "TOEIC-SW"}}) {
            parse(pair[0], pair[1]).stream()
                    .filter(s -> s.regStartAt() != null && s.examStartDate() != null)
                    .forEach(s -> assertFalse(s.examStartDate().isBefore(s.regStartAt().toLocalDate()),
                            s.certificateName() + " " + s.round() + " 시험일이 접수 시작보다 앞이다"));
        }
    }

    @Test
    @DisplayName("표가 없으면 예외 없이 0건")
    void no_table_yields_nothing() {
        assertTrue(new YbmScheduleSource().parse("<html>점검 중</html>", YbmScheduleSource.target("TSC")).isEmpty());
    }

    /** 여섯 페이지가 일곱 시험을 맡는다 — 하나라도 빠지면 그 시험은 추정치로 남는다. */
    @Test
    @DisplayName("여섯 페이지로 일곱 시험을 맡는다")
    void covers_seven_exams() {
        assertEquals(6, YbmScheduleSource.targets().size());
        for (String code : new String[]{"TOEIC", "TOEIC-SW", "TOEIC-BRIDGE", "JPT", "SJPT", "TSC"}) {
            assertNotNull(YbmScheduleSource.target(code), code + " 가 빠졌다");
        }
        long exams = YbmScheduleSource.targets().stream().mapToLong(t -> t.exams().size()).sum();
        assertEquals(7, exams, "맡는 시험 수가 달라졌다");
    }
}
