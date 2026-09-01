package com.test.test.exam.collect;

import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.Series;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TOEIC 정기시험 일정 수집 (YBM).
 *
 * <p>원본: {@code m.exam.toeic.co.kr/receipt/examSchList.php} — 일정이 서버렌더로 실려 있어 그대로 읽힌다.
 * (PC 페이지는 스크립트로 그리지만 모바일 페이지는 HTML 에 값이 있다)
 *
 * <p>한 회차의 표기 예:
 * <pre>
 * 2026.08.09 (일) 09:20
 *   정기접수 : 26.06.22 (월) 10:00~26.07.27 (월) 10:00
 *   특별추가 : 26.07.29 (수) 10:00~26.08.06 (목) 13:00
 *   성적발표 : 2026.08.18 (화) 12:00
 * </pre>
 * <b>정기접수만</b> 담는다. 특별추가는 같은 회차의 보조 창구라 별도 회차로 잡으면 중복이 된다.
 *
 * <p>회차 번호가 페이지에 없어 {@code round} 는 시험일 기준 {@code YYYYMMDD} 로 만든다 — 수집 멱등 키로 충분하다.
 */
@Component
@ConditionalOnProperty(name = "scrape.enabled", havingValue = "true")
public class ToeicScheduleSource extends AbstractHtmlScheduleSource {

    /**
     * 종목코드는 <b>종목</b>을 가리킨다 — 회차가 아니다.
     *
     * <p>전에는 회차를 붙여("TOEIC-" + round) 회차마다 새 종목코드가 됐다. 수집은 이 코드로
     * 기존 시험을 찾으므로, <b>회차 수만큼 같은 이름의 시험이 생겼다</b>(토익이 10개였다).
     * 회차는 (연도, 회차, 구분)으로 이미 구분되니 코드에 넣을 이유가 없다.
     * 값은 비큐넷 시드(seed/non_qnet_exams.json)와 같아야 그 시험에 일정이 붙는다.
     */
    static final String SOURCE_CODE = "TOEIC";

    static final String NAME = "TOEIC 토익";
    static final String AGENCY = "YBM";
    static final String CATEGORY = "어학-영어";
    static final String URL = "https://m.exam.toeic.co.kr/receipt/examSchList.php";

    private static final String DOW = "[일월화수목금토]";
    private static final Pattern ROW = Pattern.compile(
            "(\\d{4})\\.(\\d{2})\\.(\\d{2})\\s*\\(" + DOW + "\\)\\s*(\\d{2}):(\\d{2})"
                    + "[\\s\\S]{0,60}?정기접수\\s*:\\s*(\\d{2})\\.(\\d{2})\\.(\\d{2})\\s*\\(" + DOW + "\\)\\s*(\\d{2}):(\\d{2})"
                    + "\\s*~\\s*(\\d{2})\\.(\\d{2})\\.(\\d{2})\\s*\\(" + DOW + "\\)\\s*(\\d{2}):(\\d{2})"
                    + "(?:[\\s\\S]{0,200}?성적발표\\s*:\\s*(\\d{4})\\.(\\d{2})\\.(\\d{2}))?");

    @Override
    public String sourceId() {
        return "TOEIC_WEB";
    }

    @Override
    protected String pageUrl() {
        return URL;
    }

    @Override
    public List<CollectedSchedule> parse(String html) {
        List<CollectedSchedule> out = new ArrayList<>();
        Matcher m = ROW.matcher(toText(html));
        while (m.find()) {
            LocalDate examDate = LocalDate.of(
                    Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));

            LocalDateTime regStart = LocalDateTime.of(
                    2000 + Integer.parseInt(m.group(6)), Integer.parseInt(m.group(7)), Integer.parseInt(m.group(8)),
                    Integer.parseInt(m.group(9)), Integer.parseInt(m.group(10)));
            LocalDateTime regEnd = LocalDateTime.of(
                    2000 + Integer.parseInt(m.group(11)), Integer.parseInt(m.group(12)), Integer.parseInt(m.group(13)),
                    Integer.parseInt(m.group(14)), Integer.parseInt(m.group(15)));

            LocalDate result = m.group(16) == null ? null : LocalDate.of(
                    Integer.parseInt(m.group(16)), Integer.parseInt(m.group(17)), Integer.parseInt(m.group(18)));

            int round = examDate.getYear() * 10000 + examDate.getMonthValue() * 100 + examDate.getDayOfMonth();

            out.add(new CollectedSchedule(
                    SOURCE_CODE, NAME, Series.ETC, AGENCY, CATEGORY,
                    examDate.getYear(), round, ExamType.WRITTEN,
                    regStart, regEnd, examDate, examDate, result, URL));
        }
        return out;
    }
}
