package com.test.test.exam.collect;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.ScheduleProvenance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HSK 한국사무국 중국어능력시험(필기) 1종.
 *
 * <p>이 표의 함정은 <b>연도가 없다는 것</b>이다. "3월 22일(일)" 처럼 월·일만 준다.
 * 그래서 세 가지를 스스로 정해야 하고, 셋 다 틀리면 사용자가 엉뚱한 날을 기다린다.
 * <ol>
 *   <li>시험일의 연도 — 표가 올해 것인지 다음 해 것인지</li>
 *   <li>접수 시작의 연도 — 1월 시험의 접수는 <b>전해</b> 11월이다</li>
 *   <li>발표일의 연도 — 12월 시험의 발표는 <b>다음 해</b> 1월일 수 있다</li>
 * </ol>
 *
 * <p>회차도 없다. 시험일을 {@code YYYYMMDD} 로 넣어 멱등 키로 쓴다 — 화면에는 회차로 안 보인다.
 */
class HskParserTest {

    private final HskScheduleSource source = new HskScheduleSource();

    private static String fixture() throws IOException {
        return new String(new ClassPathResource("fixtures/hsk_schedule.html").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    private CollectedSchedule byExamMonth(List<CollectedSchedule> list, int month) {
        return list.stream()
                .filter(s -> s.examStartDate() != null && s.examStartDate().getMonthValue() == month)
                .findFirst().orElse(null);
    }

    @Test
    @DisplayName("연간 12회차를 전부 읽는다")
    void reads_all_twelve_sittings() throws IOException {
        List<CollectedSchedule> rows = source.parse(fixture());

        assertEquals(12, rows.size(), "회차 수가 달라졌다 — 표 구조를 다시 본다");
        assertTrue(rows.stream().allMatch(s -> s.examType() == ExamType.WRITTEN));
        assertTrue(rows.stream().allMatch(s -> s.provenance() == ScheduleProvenance.SCRAPED));
        assertTrue(rows.stream().allMatch(s -> s.certificateName().equals("HSK 중국어능력시험(필기)")));
    }

    /**
     * <b>접수는 시험보다 앞이다.</b> 1월 시험의 접수가 같은 해 11월로 들어가면
     * 이미 지난 접수를 "아직 안 열렸다"고 보여주게 된다.
     */
    @Test
    @DisplayName("1월 시험의 접수는 전해다 — 접수가 시험보다 뒤로 가지 않는다")
    void registration_never_lands_after_the_exam() throws IOException {
        List<CollectedSchedule> rows = source.parse(fixture());

        for (CollectedSchedule s : rows) {
            assertNotNull(s.regStartAt(), s.examStartDate() + " 회차의 접수 시작이 없다");
            assertTrue(!s.regStartAt().toLocalDate().isAfter(s.examStartDate()),
                    s.examStartDate() + " 회차: 접수 시작(" + s.regStartAt() + ")이 시험일보다 뒤다");
            assertTrue(!s.regEndAt().toLocalDate().isAfter(s.examStartDate()),
                    s.examStartDate() + " 회차: 접수 마감(" + s.regEndAt() + ")이 시험일보다 뒤다");
            assertTrue(s.regStartAt().isBefore(s.regEndAt()), "접수 시작이 마감보다 뒤다");
        }
    }

    @Test
    @DisplayName("발표일은 시험 뒤다")
    void result_comes_after_the_exam() throws IOException {
        for (CollectedSchedule s : source.parse(fixture())) {
            if (s.resultDate() == null) {
                continue;
            }
            assertTrue(!s.resultDate().isBefore(s.examStartDate()),
                    s.examStartDate() + " 회차: 발표일(" + s.resultDate() + ")이 시험보다 앞이다");
        }
    }

    /** 1월 10일 시험은 표에 "2025년 11월 26일 ~ 2025년 12월 31일" 로 연도가 박혀 있다 — 그게 이긴다. */
    @Test
    @DisplayName("표에 연도가 박혀 있으면 그 값을 쓴다")
    void explicit_year_wins() throws IOException {
        CollectedSchedule january = byExamMonth(source.parse(fixture()), 1);

        assertNotNull(january, "1월 회차를 못 읽었다");
        assertEquals(11, january.regStartAt().getMonthValue());
        assertEquals(january.examStartDate().getYear() - 1, january.regStartAt().getYear(),
                "1월 시험의 접수가 전해로 안 갔다");
    }

    /**
     * 시행처가 회차를 안 매긴다 — 시험일을 키로 넣는다. 그 숫자가 화면에 "20260322회" 로 새면 안 되므로
     * {@code ExamSchedule.roundLabel()} 이 거르는 범위(만 이상)에 있어야 한다.
     */
    @Test
    @DisplayName("회차 자리에는 시험일 키가 들어가고, 화면에 회차로 안 보이는 범위다")
    void round_is_an_idempotent_key() throws IOException {
        List<CollectedSchedule> rows = source.parse(fixture());

        for (CollectedSchedule s : rows) {
            LocalDate d = s.examStartDate();
            assertEquals(d.getYear() * 10000 + d.getMonthValue() * 100 + d.getDayOfMonth(), s.round());
            assertTrue(s.round() >= 10_000, "회차로 보이는 숫자다 — 화면에 그대로 샌다");
        }
    }

    @Test
    @DisplayName("같은 시험일이 두 번 나오지 않는다 — 나오면 같은 시행이 두 줄이 된다")
    void no_duplicate_sittings() throws IOException {
        List<CollectedSchedule> rows = source.parse(fixture());

        assertEquals(rows.size(), rows.stream().map(CollectedSchedule::examStartDate).distinct().count());
    }

    @Test
    @DisplayName("빈 문서·깨진 문서에는 아무것도 만들지 않는다")
    void empty_input_yields_nothing() {
        assertTrue(source.parse(null).isEmpty());
        assertTrue(source.parse("").isEmpty());
        assertTrue(source.parse("<html><body>점검 중입니다</body></html>").isEmpty());
    }

    /** 맡는 기관·종목코드를 안 밝히면 매니저 화면이 "수기로 넣으세요"라고 안내한다. */
    @Test
    @DisplayName("맡는 기관과 종목코드를 밝힌다 — HSKK 는 안 맡는다")
    void declares_its_coverage() {
        assertTrue(source.coveredAgencies().contains("HSK한국사무국"));
        assertTrue(source.coveredExamCodes().contains("M0341"));
        assertFalse(source.coveredExamCodes().contains("M0342"),
                "HSKK 는 이 표에 없다 — 맡는다고 하면 매니저에게 거짓 안내가 간다");
    }

    /** 오늘이 언제든 같은 답이어야 한다(컨벤션 §6). 표는 한 해치라 시험일이 전부 같은 해다. */
    @Test
    @DisplayName("한 표의 시험일은 모두 같은 해다")
    void all_sittings_share_one_year() throws IOException {
        List<CollectedSchedule> rows = source.parse(fixture());

        assertEquals(1, rows.stream().map(s -> s.examStartDate().getYear()).distinct().count());
        int year = rows.get(0).examStartDate().getYear();
        assertTrue(year >= TimeUtil.today().getYear(), "지난 해 표로 읽었다");
    }
}
