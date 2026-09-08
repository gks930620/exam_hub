package com.test.test.exam.collect;

import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.ScheduleStatus;
import com.test.test.exam.domain.Series;
import com.test.test.exam.notification.NotificationScheduleService;
import com.test.test.exam.repository.CertificateRepository;
import com.test.test.exam.repository.CrawlLogRepository;
import com.test.test.exam.repository.ExamScheduleRepository;
import com.test.test.exam.repository.NotificationScheduleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>실데이터가 들어오면 그 시험의 추정치는 치운다.</b>
 *
 * <p>정보보안기사가 그랬다(2026-09-04). 시드가 회차 패턴으로 "2026년 3회"를 지어냈는데
 * 시행처에는 그런 회차가 없다 — 제3회는 특성화고 기능사 전용이다. 스크래퍼가 제1·2·4회를
 * 제대로 물어 온 뒤에도 가짜 3회가 남아 사용자 화면에 "시행처 확인 필요"로 떠 있었고,
 * 그 접수일(8.17~8.21)은 실제 제4회(8.31~9.3)와 달랐다. 없는 접수일을 기다리게 하는 건
 * 일정이 없는 것보다 나쁘다.
 *
 * <p>지우는 건 <b>추정치뿐</b>이고, <b>그 소스가 그 시험의 일정을 실제로 물어 왔을 때만</b>이다.
 * 사이트가 잠깐 비어 0건이 온 날 멀쩡한 일정을 지우면 안 된다.
 */
class ApproxCleanupTest {

    private final ExamScheduleRepository schedules = Mockito.mock(ExamScheduleRepository.class);
    private final NotificationScheduleRepository notifications = Mockito.mock(NotificationScheduleRepository.class);
    private final DiffService diff = Mockito.mock(DiffService.class);

    private Certificate cert(long id) {
        Certificate c = Certificate.builder().name("정보보안기사").slug("sec").series(Series.ETC)
                .agency("한국방송통신전파진흥원").category("IT-보안").sourceCode("KCA-SEC").build();
        org.springframework.test.util.ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private ExamSchedule row(Certificate c, int round, ExamType type, ScheduleProvenance provenance) {
        return ExamSchedule.builder()
                .certificate(c).year(2026).round(round).examType(type)
                .examStartDate(LocalDate.of(2026, 9, 19))
                .provenance(provenance).status(ScheduleStatus.ACTIVE)
                .build();
    }

    /** 소스가 준 회차(1·2·4회)와 DB 의 가짜 3회를 만들어 수집 한 번을 돌린다. */
    private CollectService serviceThatCollects(Certificate c, List<ExamSchedule> inDb,
                                               List<CollectedSchedule> produced, List<ExamSchedule> upserted) {
        Mockito.when(schedules.findByCertificateIdInAndStatus(Mockito.anyList(), Mockito.any()))
                .thenReturn(inDb);
        Mockito.when(notifications.findByExamSchedule(Mockito.any())).thenReturn(List.of());
        for (int i = 0; i < produced.size(); i++) {
            Mockito.when(diff.upsert(produced.get(i)))
                    .thenReturn(new DiffService.Outcome(DiffService.DiffType.NEW, upserted.get(i), false));
        }
        ScheduleSource source = new ScheduleSource() {
            @Override public String sourceId() { return "KCA_WEB"; }
            @Override public int priority() { return 50; }
            @Override public java.util.Set<String> coveredAgencies() { return java.util.Set.of("한국방송통신전파진흥원"); }
            @Override public List<CollectedSchedule> fetchAll() { return produced; }
        };
        return new CollectService(List.of(source), diff,
                Mockito.mock(NotificationScheduleService.class), Mockito.mock(CrawlLogRepository.class),
                schedules, Mockito.mock(CertificateRepository.class), notifications);
    }

    private CollectedSchedule record(int round, ExamType type) {
        return new CollectedSchedule("KCA-SEC", "정보보안기사", Series.ETC, "한국방송통신전파진흥원", "IT-보안",
                2026, round, type, null, null, LocalDate.of(2026, 9, 14), null, null,
                "https://www.cq.or.kr/", ScheduleProvenance.SCRAPED);
    }

    @Test
    @DisplayName("소스가 안 준 추정치 회차는 지운다 — 없는 회차를 기다리게 하지 않는다")
    void removes_approx_rounds_the_source_did_not_confirm() {
        Certificate c = cert(1L);
        ExamSchedule fakeThird = row(c, 3, ExamType.WRITTEN, ScheduleProvenance.APPROX);
        ExamSchedule realFourth = row(c, 4, ExamType.WRITTEN, ScheduleProvenance.SCRAPED);
        CollectedSchedule produced = record(4, ExamType.WRITTEN);

        CollectService service = serviceThatCollects(c, List.of(fakeThird, realFourth),
                List.of(produced), List.of(realFourth));

        service.collectAll();

        ArgumentCaptor<ExamSchedule> deleted = ArgumentCaptor.forClass(ExamSchedule.class);
        Mockito.verify(schedules).delete(deleted.capture());
        assertEquals(3, deleted.getValue().getRound(), "지워야 할 건 지어낸 3회다");
    }

    @Test
    @DisplayName("소스가 준 회차와 같으면 그대로 둔다(덮어쓰기는 upsert 의 몫)")
    void keeps_approx_rounds_the_source_also_returned() {
        Certificate c = cert(1L);
        ExamSchedule approxFourth = row(c, 4, ExamType.WRITTEN, ScheduleProvenance.APPROX);
        CollectedSchedule produced = record(4, ExamType.WRITTEN);

        CollectService service = serviceThatCollects(c, List.of(approxFourth),
                List.of(produced), List.of(approxFourth));

        service.collectAll();

        Mockito.verify(schedules, Mockito.never()).delete(Mockito.any());
    }

    /** 확정값(스크래핑·API·매니저 입력)은 소스가 안 줬다고 지우지 않는다 — 그건 별개의 판단이다. */
    @Test
    @DisplayName("확정된 회차는 지우지 않는다")
    void never_removes_confirmed_rounds() {
        Certificate c = cert(1L);
        ExamSchedule oldScraped = row(c, 1, ExamType.WRITTEN, ScheduleProvenance.SCRAPED);
        ExamSchedule manual = row(c, 2, ExamType.WRITTEN, ScheduleProvenance.MANUAL);
        CollectedSchedule produced = record(4, ExamType.WRITTEN);

        CollectService service = serviceThatCollects(c, List.of(oldScraped, manual),
                List.of(produced), List.of(row(c, 4, ExamType.WRITTEN, ScheduleProvenance.SCRAPED)));

        service.collectAll();

        Mockito.verify(schedules, Mockito.never()).delete(Mockito.any());
    }

    /**
     * <b>파일 시드는 살아 있는 스크래퍼가 맡은 기관에 추정치를 넣지 않는다.</b>
     *
     * <p>안 그러면 정리와 시드가 매일 싸운다. 실제로 그랬다(2026-09-04): 재수집이 TOEIC·TEPS 의
     * 가짜 회차를 지웠는데, 다음 기동에 시드가 그대로 다시 넣어 "시행처 확인" 40 → 42 로 되돌아갔다.
     * 시드는 <b>아무도 안 긁는 시험</b>의 임시 데이터여야 한다.
     */
    @Test
    @DisplayName("파일 시드의 추정치는 스크래퍼가 맡은 기관이면 안 넣는다")
    void file_seed_skips_agencies_a_live_source_covers() {
        CollectedSchedule ybmApprox = new CollectedSchedule("TOEIC", "TOEIC 토익", Series.ETC, "YBM", "어학-영어",
                2026, 202610, ExamType.WRITTEN, null, null, LocalDate.of(2026, 10, 25), null, null,
                "https://exam.toeic.co.kr/", ScheduleProvenance.APPROX);
        CollectedSchedule ownApprox = new CollectedSchedule("GOSI-N9", "국가직 9급", Series.ETC, "인사혁신처", "공무원",
                2026, 1, ExamType.WRITTEN, null, null, LocalDate.of(2026, 4, 5), null, null,
                "https://www.gosi.kr/", ScheduleProvenance.APPROX);

        ScheduleSource seed = new ScheduleSource() {
            @Override public String sourceId() { return "SEED_NONQNET"; }
            @Override public int priority() { return 0; }
            @Override public boolean usesNetwork() { return false; }
            @Override public List<CollectedSchedule> fetchAll() { return List.of(ybmApprox, ownApprox); }
        };
        ScheduleSource toeic = new ScheduleSource() {
            @Override public String sourceId() { return "TOEIC_WEB"; }
            @Override public int priority() { return 50; }
            @Override public java.util.Set<String> coveredAgencies() { return java.util.Set.of("YBM"); }
            @Override public List<CollectedSchedule> fetchAll() { return List.of(); }
        };
        Mockito.when(schedules.findByCertificateIdInAndStatus(Mockito.anyList(), Mockito.any())).thenReturn(List.of());
        CollectService service = new CollectService(List.of(seed, toeic), diff,
                Mockito.mock(NotificationScheduleService.class), Mockito.mock(CrawlLogRepository.class),
                schedules, Mockito.mock(CertificateRepository.class), notifications);

        service.collectWithoutNetwork();

        Mockito.verify(diff, Mockito.never()).upsert(ybmApprox);
        Mockito.verify(diff).upsert(ownApprox);
    }

    /** 그 시험의 일정을 하나도 못 물어 온 소스는 아무것도 못 지운다 — 사이트가 빈 날의 사고 방지. */
    @Test
    @DisplayName("소스가 그 시험에 대해 0건이면 추정치를 건드리지 않는다")
    void empty_result_touches_nothing() {
        Certificate c = cert(1L);
        ExamSchedule approx = row(c, 3, ExamType.WRITTEN, ScheduleProvenance.APPROX);

        CollectService service = serviceThatCollects(c, List.of(approx), List.of(), List.of());

        service.collectAll();

        Mockito.verify(schedules, Mockito.never()).delete(Mockito.any());
        assertTrue(true);
    }
}
