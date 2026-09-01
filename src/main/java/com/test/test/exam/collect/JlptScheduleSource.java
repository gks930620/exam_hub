package com.test.test.exam.collect;

import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.Series;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JLPT 일본어능력시험 일정 수집 (JLPT 서울실시위원회).
 *
 * <p>원본: {@code jlpt.or.kr/html/} 첫 화면. 연 2회(7월·12월)뿐이라 화면에 이번 회차만 실린다.
 *
 * <p>표기 예:
 * <pre>
 * 일반접수 : 2026년 09월 01일 ~ 09월20일
 * 2026년 제2회 JLPT ... 시험일자 12.06
 * </pre>
 *
 * <p><b>이 시험이 서비스의 존재 이유를 가장 잘 보여준다</b> — 연 2회라 접수를 놓치면 반년을 기다린다.
 * 데이터는 1년에 두 줄이라 스크래퍼보다 수기 입력이 싸지만, 페이지가 정직해 파싱이 간단해 자동화해 둔다.
 *
 * <p>성적발표는 "1월 말"처럼 월 단위로만 공지돼 날짜로 확정할 수 없다 → {@code resultDate} 는 비운다.
 */
@Component
@ConditionalOnProperty(name = "scrape.enabled", havingValue = "true")
public class JlptScheduleSource extends AbstractHtmlScheduleSource {

    /**
     * 종목코드는 <b>종목</b>을 가리킨다 — 회차가 아니다.
     *
     * <p>전에는 회차를 붙여("TOEIC-" + round) 회차마다 새 종목코드가 됐다. 수집은 이 코드로
     * 기존 시험을 찾으므로, <b>회차 수만큼 같은 이름의 시험이 생겼다</b>(토익이 10개였다).
     * 회차는 (연도, 회차, 구분)으로 이미 구분되니 코드에 넣을 이유가 없다.
     * 값은 비큐넷 시드(seed/non_qnet_exams.json)와 같아야 그 시험에 일정이 붙는다.
     */
    static final String SOURCE_CODE = "JLPT";

    static final String NAME = "JLPT 일본어능력시험";
    static final String AGENCY = "JEES / 국제교류기금";
    static final String CATEGORY = "어학-일본어";
    static final String URL = "https://www.jlpt.or.kr/html/";

    private static final Pattern ROUND = Pattern.compile("(\\d{4})년\\s*제\\s*(\\d)\\s*회");
    private static final Pattern REG = Pattern.compile(
            "일반접수\\s*:\\s*(\\d{4})년\\s*(\\d{1,2})월\\s*(\\d{1,2})일\\s*~\\s*(\\d{1,2})월\\s*(\\d{1,2})일");
    private static final Pattern EXAM = Pattern.compile("시험일자\\s*(\\d{1,2})\\.(\\d{1,2})");

    @Override
    public String sourceId() {
        return "JLPT_WEB";
    }

    @Override
    protected String pageUrl() {
        return URL;
    }

    @Override
    public List<CollectedSchedule> parse(String html) {
        String text = toText(html);

        Matcher round = ROUND.matcher(text);
        Matcher reg = REG.matcher(text);
        Matcher exam = EXAM.matcher(text);
        if (!round.find() || !reg.find() || !exam.find()) {
            return List.of();
        }

        int year = Integer.parseInt(round.group(1));
        int no = Integer.parseInt(round.group(2));

        int regYear = Integer.parseInt(reg.group(1));
        LocalDate regStart = LocalDate.of(regYear, Integer.parseInt(reg.group(2)), Integer.parseInt(reg.group(3)));
        LocalDate regEnd = LocalDate.of(regYear, Integer.parseInt(reg.group(4)), Integer.parseInt(reg.group(5)));
        // 접수가 연말에 시작해 해를 넘기는 경우 방어
        if (regEnd.isBefore(regStart)) {
            regEnd = regEnd.plusYears(1);
        }

        LocalDate examDate = LocalDate.of(year, Integer.parseInt(exam.group(1)), Integer.parseInt(exam.group(2)));

        return List.of(new CollectedSchedule(
                SOURCE_CODE, NAME, Series.ETC, AGENCY, CATEGORY,
                year, no, ExamType.WRITTEN,
                regStart.atTime(LocalTime.of(0, 0)),
                regEnd.atTime(LocalTime.of(23, 59)),
                examDate, examDate,
                null,   // 성적발표는 "1월 말"처럼 월 단위 공지라 날짜로 못 박지 않는다
                URL));
    }
}
