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

import com.test.test.exam.common.TimeUtil;

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

    /**
     * <b>날짜는 전부 오늘 기준 상대값이다</b>(컨벤션 §6 "시간 의존 테스트 금지").
     *
     * <p>고정 날짜를 쓰면 그 날이 지나는 순간 테스트가 깨진다. 실제로 깨졌다(2026-09-21):
     * 수집값 정리는 <b>오늘부터 이번에 본 가장 먼 시험일까지</b>만 지우는데, 픽스처의 2026-09-19 가
     * 과거가 되면서 "지워야 한다"던 행이 안 지워졌다. 코드가 아니라 달력이 바꾼 실패다.
     */
    private static final LocalDate TODAY = TimeUtil.today();
    private static final int YEAR = TODAY.getYear();
    /** 이번에 읽어 온 회차의 시험일 — 오늘과 UPSERT_IN 사이라야 "수집 기간 안"이 된다 */
    private static final LocalDate EXAM_DAY = TODAY.plusDays(25);
    /** upsert 가 돌려주는 행의 시험일 */
    private static final LocalDate UPSERT_DAY = TODAY.plusDays(30);

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
                .certificate(c).year(YEAR).round(round).examType(type)
                .examStartDate(UPSERT_DAY)
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
                YEAR, round, type, null, null, EXAM_DAY, null, null,
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

    /** 매니저 입력은 어떤 경우에도 지우지 않는다 — 사람이 공고를 보고 넣은 값이다. */
    @Test
    @DisplayName("매니저 입력은 지우지 않는다")
    void never_removes_manual_rows() {
        Certificate c = cert(1L);
        ExamSchedule manual = row(c, 2, ExamType.WRITTEN, ScheduleProvenance.MANUAL);
        CollectedSchedule produced = record(4, ExamType.WRITTEN);

        CollectService service = serviceThatCollects(c, List.of(manual),
                List.of(produced), List.of(row(c, 4, ExamType.WRITTEN, ScheduleProvenance.SCRAPED)));

        service.collectAll();

        Mockito.verify(schedules, Mockito.never()).delete(Mockito.any());
    }

    /**
     * <b>시행처가 이번에 안 실은 수집값도 치운다 — 단 이번에 읽어 온 기간 안에서만.</b>
     *
     * <p>토익이 그랬다(2026-09-08). 회차가 없는 줄 알고 시험일을 회차 자리에 넣어 뒀는데,
     * 시행처가 매긴 "제580회"로 바꾸니 같은 날짜가 두 줄이 됐다. 사용자에겐 같은 시험이 두 번 보인다.
     */
    @Test
    @DisplayName("시행처가 이번에 안 실은 수집값은 지운다 — 수집 기간 안이면")
    void removes_scraped_rounds_the_source_no_longer_lists() {
        Certificate c = cert(1L);
        ExamSchedule renumbered = ExamSchedule.builder()
                .certificate(c).year(YEAR).round(20260914).examType(ExamType.WRITTEN)
                .examStartDate(EXAM_DAY)
                .provenance(ScheduleProvenance.SCRAPED).status(ScheduleStatus.ACTIVE)
                .build();
        CollectedSchedule produced = record(4, ExamType.WRITTEN);   // 같은 시험일 — 회차 번호만 바뀌었다

        CollectService service = serviceThatCollects(c, List.of(renumbered),
                List.of(produced), List.of(row(c, 4, ExamType.WRITTEN, ScheduleProvenance.SCRAPED)));

        service.collectAll();

        ArgumentCaptor<ExamSchedule> deleted = ArgumentCaptor.forClass(ExamSchedule.class);
        Mockito.verify(schedules).delete(deleted.capture());
        assertEquals(20260914, deleted.getValue().getRound(), "회차 번호가 바뀌기 전의 옛 행이 남았다");
    }

    /**
     * <b>회차 번호만 다르고 시험일이 같은 행은 같은 시행이다.</b> 토익이 그랬다 — 시험일을 회차 자리에
     * 넣던 옛 행과 시행처가 매긴 회차가 같은 날짜로 나란히 남아, 사용자에게 같은 시험이 두 번 보였다.
     * 이건 <b>지난 회차라도</b> 지운다. 지난 목록에 같은 날이 두 줄인 것도 틀린 화면이다.
     */
    @Test
    @DisplayName("같은 시험일의 중복 회차는 지난 것이라도 지운다")
    void duplicate_sittings_are_removed_even_in_the_past() {
        Certificate c = cert(1L);
        LocalDate past = TODAY.minusDays(60);
        ExamSchedule oldKey = ExamSchedule.builder()
                .certificate(c).year(YEAR).round(20260110).examType(ExamType.WRITTEN)
                .examStartDate(past)
                .provenance(ScheduleProvenance.SCRAPED).status(ScheduleStatus.ACTIVE)
                .build();
        ExamSchedule real = ExamSchedule.builder()
                .certificate(c).year(YEAR).round(576).examType(ExamType.WRITTEN)
                .examStartDate(past)
                .provenance(ScheduleProvenance.SCRAPED).status(ScheduleStatus.ACTIVE)
                .build();
        CollectedSchedule produced = new CollectedSchedule("KCA-SEC", "정보보안기사", Series.ETC,
                "한국방송통신전파진흥원", "IT-보안", YEAR, 576, ExamType.WRITTEN,
                null, null, past, null, null, "https://www.cq.or.kr/", ScheduleProvenance.SCRAPED);

        CollectService service = serviceThatCollects(c, List.of(oldKey, real),
                List.of(produced), List.of(real));

        service.collectAll();

        ArgumentCaptor<ExamSchedule> deleted = ArgumentCaptor.forClass(ExamSchedule.class);
        Mockito.verify(schedules).delete(deleted.capture());
        assertEquals(20260110, deleted.getValue().getRound(), "같은 날짜의 옛 행이 남았다");
    }

    /**
     * 시행처가 반년치만 싣는데 그 뒤 일정까지 지우면 멀쩡한 값이 날아간다.
     * <b>이번에 본 가장 먼 시험일 너머는 손대지 않는다.</b>
     */
    @Test
    @DisplayName("이번에 읽어 온 기간 너머의 수집값은 건드리지 않는다")
    void keeps_scraped_rounds_beyond_this_run() {
        Certificate c = cert(1L);
        ExamSchedule nextYear = ExamSchedule.builder()
                .certificate(c).year(YEAR + 1).round(1).examType(ExamType.WRITTEN)
                .examStartDate(TODAY.plusDays(400))
                .provenance(ScheduleProvenance.SCRAPED).status(ScheduleStatus.ACTIVE)
                .build();
        CollectedSchedule produced = record(4, ExamType.WRITTEN);   // 이번엔 EXAM_DAY 까지만 봤다

        CollectService service = serviceThatCollects(c, List.of(nextYear),
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
            @Override public String sourceId() { return "YBM_WEB"; }
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

    /**
     * <b>매니저 값은 지키되, 시행처가 다른 말을 하면 그 사실은 남긴다.</b>
     *
     * <p>수집이 MANUAL 행을 안 덮는 건 맞다 — 사람이 공고를 보고 넣은 값이 이긴다. 그런데 조용히 버리면
     * 시행처가 접수일을 2주 당겨도 매니저가 알 방법이 없어 옛 날짜로 D-day 와 알림이 계속 나간다(2026-09-08).
     */
    @Test
    @DisplayName("수기 행은 안 덮지만 수집값이 다르면 표시를 남긴다")
    void manual_row_keeps_value_but_records_the_conflict() {
        Certificate c = cert(1L);
        ExamSchedule manual = ExamSchedule.builder()
                .certificate(c).year(2026).round(1).examType(ExamType.WRITTEN)
                .examStartDate(LocalDate.of(2026, 9, 19))
                .provenance(ScheduleProvenance.MANUAL).status(ScheduleStatus.ACTIVE)
                .build();
        ExamScheduleRepository repo = Mockito.mock(ExamScheduleRepository.class);
        Mockito.when(repo.findByCertificateAndYearAndRoundAndExamType(
                        Mockito.any(), Mockito.anyInt(), Mockito.anyInt(), Mockito.any()))
                .thenReturn(java.util.Optional.of(manual));
        CertificateRepository certs = Mockito.mock(CertificateRepository.class);
        Mockito.when(certs.findBySourceCode(Mockito.any())).thenReturn(java.util.Optional.of(c));
        DiffService diffService = new DiffService(certs, repo);

        CollectedSchedule fromSite = new CollectedSchedule("KCA-SEC", "정보보안기사", Series.ETC,
                "한국방송통신전파진흥원", "IT-보안", 2026, 1, ExamType.WRITTEN,
                null, null, LocalDate.of(2026, 9, 5), null, null,
                "https://www.cq.or.kr/", ScheduleProvenance.SCRAPED);

        DiffService.Outcome outcome = diffService.upsert(fromSite);

        assertEquals(DiffService.DiffType.UNCHANGED, outcome.type(), "매니저 값을 덮었다");
        assertEquals(LocalDate.of(2026, 9, 19), manual.getExamStartDate(), "매니저가 넣은 날짜가 바뀌었다");
        assertTrue(manual.getSourceConflict() != null && manual.getSourceConflict().contains("2026-09-05"),
                "시행처가 뭐라고 하는지 안 남겼다 — 매니저가 바뀐 사실을 알 길이 없다");
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
