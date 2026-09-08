package com.test.test.exam.collect;

import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.Series;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 매니저의 "다시 받아오기"가 <b>정말로 다시 받아오는지</b>.
 *
 * <p>이 경로에서 두 번 조용히 실패했다.
 * <ol>
 *   <li>2026-09-04 — 기본 구현이 예외를 던져 스크래퍼가 죽었다. 매니저는 눌렀는데 아무 일도 안 일어났다.</li>
 *   <li>2026-09-08 — 예외 대신 <b>종목코드로 걸렀더니</b> 0건이 됐다. 우리 시험의 코드와 스크래퍼가
 *       내놓는 코드가 다를 수 있기 때문이다(토익은 시험이 {@code TOEIC}, 스크래퍼가 {@code M0323}).
 *       "받아왔다"는 응답만 돌아오고 일정은 그대로였다.</li>
 * </ol>
 *
 * <p>그래서 <b>거르지 않는다.</b> 스크래퍼는 어차피 페이지를 통째로 읽으므로 거른다고 아낄 것이 없고,
 * 코드가 어긋나면 잃을 것만 있다. 어느 시험에 붙일지는 {@code DiffService} 가 이름까지 보고 정한다.
 */
class ScheduleSourceDefaultsTest {

    private static CollectedSchedule row(String code, String name) {
        return new CollectedSchedule(code, name, Series.ETC, "YBM", "어학-영어",
                2026, 576, ExamType.WRITTEN, null, null,
                LocalDate.of(2026, 8, 23), null, null,
                "https://exam.toeic.co.kr/", ScheduleProvenance.SCRAPED);
    }

    /** 페이지 하나를 통째로 읽는 소스 — 부분 조회를 지원하지 않는다. */
    private static ScheduleSource wholePageSource() {
        return new ScheduleSource() {
            @Override public String sourceId() { return "YBM_WEB"; }
            @Override public Set<String> coveredAgencies() { return Set.of("YBM"); }
            @Override public List<CollectedSchedule> fetchAll() {
                return List.of(row("M0323", "TOEIC 토익"), row("M0343", "TSC 중국어 말하기시험"));
            }
        };
    }

    /**
     * <b>우리 시험에 붙은 코드와 스크래퍼가 내놓는 코드가 달라도 받아온다.</b>
     * 매니저는 시험에 붙은 코드로 재수집을 부르는데, 그 코드로 거르면 한 건도 안 남는다.
     */
    @Test
    @DisplayName("우리 코드와 시행처 코드가 달라도 재수집이 빈손으로 끝나지 않는다")
    void re_collect_is_not_silently_empty_when_codes_differ() {
        List<CollectedSchedule> out = wholePageSource().fetchByCertificateCodes(List.of("TOEIC"));

        assertEquals(2, out.size(), "코드가 안 맞는다고 0건이 되면 매니저가 눌러도 아무 일이 안 일어난다");
    }

    @Test
    @DisplayName("코드가 맞는 경우에도 그대로 다 받아온다")
    void re_collect_returns_everything_the_page_had() {
        List<CollectedSchedule> out = wholePageSource().fetchByCertificateCodes(List.of("M0323"));

        assertEquals(2, out.size(), "페이지를 이미 읽었으니 걸러서 아낄 것이 없다");
    }
}
