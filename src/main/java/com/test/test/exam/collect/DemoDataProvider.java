package com.test.test.exam.collect;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.Series;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 데모 데이터 단일 소스 (설계 04 §4, {@code @Profile("!prod")}).
 * MockScheduleSource(수집 입력)와 ExamDataInitializer(마스터/관심 시드)가 모두 이 목록을 공유한다.
 * 날짜는 <b>오늘 기준 상대 오프셋</b>으로 생성 — 실행일과 무관하게 접수중/접수예정/시험예정/완료가 골고루 보인다.
 */
@Component
@Profile("!prod")
public class DemoDataProvider {

    private static final String QNET = "한국산업인력공단";
    private static final String KDATA = "한국데이터산업진흥원";
    private static final String KCCI = "대한상공회의소";
    private static final String QNET_URL = "https://www.q-net.or.kr/";

    /** 데모 자격증 정의 (마스터용). schedules 는 CollectedSchedule 로 평면화되어 수집 소스로 쓰인다. */
    public record DemoCert(String name, String slug, Series series, String agency, String category, String sourceCode) {
    }

    private static final String CAT_QNET = "국가기술자격";

    /** 마스터 시드용 자격증 10종 (설계 04 §4). */
    public List<DemoCert> demoCertificates() {
        return List.of(
                new DemoCert("정보처리기사", "정보처리기사", Series.TECHNICIAN, QNET, CAT_QNET, "1320"),
                new DemoCert("전기기사", "전기기사", Series.TECHNICIAN, QNET, CAT_QNET, "1230"),
                new DemoCert("산업안전기사", "산업안전기사", Series.TECHNICIAN, QNET, CAT_QNET, "2290"),
                new DemoCert("소방설비기사(전기분야)", "소방설비기사-전기", Series.TECHNICIAN, QNET, CAT_QNET, "7761"),
                new DemoCert("건축기사", "건축기사", Series.TECHNICIAN, QNET, CAT_QNET, "1450"),
                new DemoCert("정보처리산업기사", "정보처리산업기사", Series.INDUSTRIAL, QNET, CAT_QNET, "2320"),
                new DemoCert("지게차운전기능사", "지게차운전기능사", Series.CRAFTSMAN, QNET, CAT_QNET, "5836"),
                new DemoCert("한식조리기능사", "한식조리기능사", Series.CRAFTSMAN, QNET, CAT_QNET, "7910"),
                new DemoCert("SQL개발자(SQLD)", "SQLD", Series.SERVICE, KDATA, "IT-데이터", "S001"),
                new DemoCert("컴퓨터활용능력1급", "컴퓨터활용능력1급", Series.SERVICE, KCCI, "사무-IT", "C011")
        );
    }

    /**
     * 전 종목의 수집 레코드. 오늘 기준 오프셋으로 상태를 다양화한다.
     * cert #1~#3 은 홈 데모(관심 3종)에서 접수중 / 접수예정 / 시험예정이 각각 잡히도록 배치.
     */
    public List<CollectedSchedule> collectedSchedules() {
        LocalDate base = TimeUtil.today();
        List<CollectedSchedule> out = new ArrayList<>();
        List<DemoCert> certs = demoCertificates();

        // #1 정보처리기사: 필기 접수중, 실기 접수예정, 지난 회차 완료
        add(out, certs.get(0), base, 2, ExamType.WRITTEN, -3, 2, 25, 25, 45);
        add(out, certs.get(0), base, 2, ExamType.PRACTICAL, 55, 58, 90, 90, 115);
        add(out, certs.get(0), base, 1, ExamType.WRITTEN, -120, -115, -95, -95, -75);

        // #2 전기기사: 접수 예정
        add(out, certs.get(1), base, 2, ExamType.WRITTEN, 10, 15, 45, 45, 70);
        add(out, certs.get(1), base, 2, ExamType.PRACTICAL, 80, 84, 120, 122, 150);

        // #3 산업안전기사: 접수 마감 후 시험 예정
        add(out, certs.get(2), base, 1, ExamType.WRITTEN, -60, -55, 12, 12, 32);
        add(out, certs.get(2), base, 1, ExamType.PRACTICAL, 40, 44, 75, 76, 100);

        // #4 소방설비기사(전기): 접수중(마감 임박)
        add(out, certs.get(3), base, 2, ExamType.WRITTEN, -5, 1, 20, 20, 40);

        // #5 건축기사: 시험 예정
        add(out, certs.get(4), base, 1, ExamType.WRITTEN, -70, -65, 5, 5, 25);

        // #6 정보처리산업기사: 접수 예정
        add(out, certs.get(5), base, 2, ExamType.WRITTEN, 20, 25, 55, 55, 80);

        // #7 지게차운전기능사: 상시 종목 — 접수중
        add(out, certs.get(6), base, 3, ExamType.PRACTICAL, -2, 5, 18, 18, 30);

        // #8 한식조리기능사: 접수 예정
        add(out, certs.get(7), base, 4, ExamType.PRACTICAL, 7, 12, 40, 40, 55);

        // #9 SQLD: 접수 예정
        add(out, certs.get(8), base, 2, ExamType.WRITTEN, 14, 18, 48, 48, 68);

        // #10 컴퓨터활용능력1급: 필기 접수중, 실기 예정
        add(out, certs.get(9), base, 3, ExamType.WRITTEN, -1, 4, 22, 22, 35);
        add(out, certs.get(9), base, 3, ExamType.PRACTICAL, 45, 49, 78, 78, 95);

        return out;
    }

    private void add(List<CollectedSchedule> out, DemoCert c, LocalDate base,
                     int round, ExamType type,
                     int regStartOff, int regEndOff, int examStartOff, int examEndOff, int resultOff) {
        LocalDateTime regStart = base.plusDays(regStartOff).atTime(10, 0);
        LocalDateTime regEnd = base.plusDays(regEndOff).atTime(18, 0);
        LocalDate examStart = base.plusDays(examStartOff);
        LocalDate examEnd = base.plusDays(examEndOff);
        LocalDate result = base.plusDays(resultOff);
        int year = examStart.getYear();
        out.add(new CollectedSchedule(
                c.sourceCode(), c.name(), c.series(), c.agency(), c.category(),
                year, round, type,
                regStart, regEnd, examStart, examEnd, result, QNET_URL));
    }
}
