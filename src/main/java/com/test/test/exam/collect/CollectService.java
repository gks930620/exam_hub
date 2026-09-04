package com.test.test.exam.collect;

import com.test.test.exam.domain.CrawlLog;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.ScheduleStatus;
import com.test.test.exam.admin.AgencyMatcher;
import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.notification.NotificationScheduleService;
import com.test.test.exam.repository.CertificateRepository;
import com.test.test.exam.repository.CrawlLogRepository;
import com.test.test.exam.repository.ExamScheduleRepository;
import com.test.test.exam.repository.NotificationScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 수집 파이프라인 오케스트레이션 (설계 05 §4, 06 §1-1).
 * fetch → diff·검증 upsert → notification_schedule 재계산 → crawl_log.
 * 각 레코드 upsert 는 독립 트랜잭션(DiffService)이라 한 건 실패가 전체를 롤백하지 않는다 (NFR-04).
 *
 * <p><b>소스 실행 순서는 {@link ScheduleSource#priority()}</b> — 시드·스냅샷(0) → 스크래퍼(50) → 큐넷 API(100).
 * 같은 키를 여러 소스가 주면 나중에 쓴 쪽이 이기므로, 확정도가 높은 소스가 마지막이어야 한다.
 * 스프링이 빈을 어떤 순서로 주입하든 여기서 정렬한다. 기동 전용 소스(스냅샷)는 배치에서 돌지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectService {

    private static final Comparator<ScheduleSource> BY_PRIORITY = Comparator.comparingInt(ScheduleSource::priority);

    private final List<ScheduleSource> sources; // 활성 소스 전부 (시드·Mock·스크래퍼·큐넷)
    private final DiffService diffService;
    private final NotificationScheduleService notificationScheduleService;
    private final CrawlLogRepository crawlLogRepository;
    private final ExamScheduleRepository examScheduleRepository;
    private final CertificateRepository certificateRepository;
    private final NotificationScheduleRepository notificationScheduleRepository;

    /**
     * 전체 수집 (05:00 배치 / {@code collect.on-startup=true} 일 때 기동 시 1회).
     *
     * <p><b>소스 하나가 죽어도 나머지는 돈다.</b> 시행처 사이트는 개편·점검으로 수시로 깨지는데,
     * 하나 때문에 그날 수집이 통째로 멈추면 멀쩡한 소스의 새 일정까지 못 받는다.
     */
    public void collectAll() {
        for (ScheduleSource source : batchSources(s -> true)) {
            try {
                runSource(source, source.fetchAll());
            } catch (Exception e) {
                log.error("[Collect] source={} 수집 실패 — 다른 소스는 계속합니다: {}",
                        source.sourceId(), e.toString());
            }
        }
    }

    /**
     * 파일 기반 소스만 수집 (기동할 때마다). 기동 전용 스냅샷도 여기서 돈다.
     *
     * <p>파일은 돈이 안 드니 늘 읽는다. 스냅샷은 DB 가 파일보다 새로우면 스스로 건너뛴다
     * ({@link SnapshotScheduleSource}) — 재기동마다 2,600건을 다시 쓰지 않는다.
     */
    public void collectWithoutNetwork() {
        List<ScheduleSource> offline = sources.stream()
                .filter(s -> !s.usesNetwork())
                .sorted(BY_PRIORITY)
                .toList();
        if (offline.isEmpty()) {
            return;
        }
        log.info("[Collect] 파일 시드만 적재 — 네트워크 호출 없음 (소스 {}개)", offline.size());
        for (ScheduleSource source : offline) {
            try {
                runSource(source, source.fetchAll());
            } catch (Exception e) {
                log.error("[Collect] source={} 시드 적재 실패 — 다른 소스는 계속합니다: {}",
                        source.sourceId(), e.toString());
            }
        }
    }

    /**
     * 지정한 종목만 다시 수집 — 매니저의 "다시 받아오기"(AdminCollectController).
     * 전량 수집 중 일시 오류로 빈 종목을 그 수만큼의 호출로 채운다.
     * 부를 소스는 {@link #runForCodes} 가 고른다 — 큐넷은 종목만, 스크래퍼는 담당 기관이 걸릴 때만.
     */
    public void collectByCodes(List<String> sourceCodes) {
        Set<String> agencies = certificateRepository.findBySourceCodeIn(sourceCodes).stream()
                .map(Certificate::getAgency)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        runForCodes(sourceCodes, agencies, "재수집");
    }

    /** 접수 임박 종목 재확인 (17:00 배치). 7일 이내 접수 시작 종목만, 그 종목을 담당하는 소스만. */
    public void collectImminent() {
        LocalDateTime now = TimeUtil.now();
        List<Long> certIds = examScheduleRepository.findCertificateIdsWithImminentRegistration(
                now, now.plusDays(7));
        if (certIds.isEmpty()) {
            log.info("[Collect] 임박 종목 없음 — 재확인 배치 스킵");
            return;
        }
        List<Certificate> certs = certificateRepository.findAllById(certIds);
        List<String> sourceCodes = certs.stream()
                .map(Certificate::getSourceCode)
                .filter(java.util.Objects::nonNull)
                .distinct().toList();
        Set<String> agencies = certs.stream()
                .map(Certificate::getAgency)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        runForCodes(sourceCodes, agencies, "임박 재확인");
    }

    /**
     * 종목을 지정해 다시 받아온다.
     *
     * <p>부분 조회가 되는 소스(큐넷 API)는 그 종목만 부른다. <b>스크래퍼는 부분 조회가 안 되지만,
     * 담당 기관이 걸리면 불러야 한다</b> — 사이트를 한 번 읽고 걸러 주기 때문이다. 이 조건이 없으면
     * 매니저가 KCA 종목에 "다시 받아오기"를 눌러도 큐넷만 돌고 아무 일도 안 일어난다(2026-09-04 실측).
     * 반대로 조건이 없으면 큐넷 종목 세 개를 다시 받자고 시행처 8곳을 전부 두드린다.
     */
    private void runForCodes(List<String> sourceCodes, Set<String> agencies, String what) {
        for (ScheduleSource source : batchSources(s ->
                s.supportsPartialFetch() || AgencyMatcher.matchesAny(agencies, s.coveredAgencies()))) {
            try {
                runSource(source, source.fetchByCertificateCodes(sourceCodes));
            } catch (Exception e) {
                log.error("[Collect] source={} {} 실패 — 다른 소스는 계속합니다: {}",
                        source.sourceId(), what, e.toString());
            }
        }
    }

    /** 배치에서 돌 소스 — 기동 전용은 빼고 priority 순. */
    private List<ScheduleSource> batchSources(Predicate<ScheduleSource> filter) {
        return sources.stream()
                .filter(s -> !s.startupOnly())
                .filter(filter)
                .sorted(BY_PRIORITY)
                .toList();
    }

    private void runSource(ScheduleSource source, List<CollectedSchedule> records) {
        CrawlLog crawlLog = CrawlLog.start(source.sourceId());
        int neu = 0, updated = 0, skipped = 0, pending = 0;
        Set<Long> touched = new java.util.LinkedHashSet<>();
        Set<String> confirmed = new java.util.HashSet<>();
        try {
            for (CollectedSchedule rec : records) {
                DiffService.Outcome outcome = diffService.upsert(rec);
                if (outcome.schedule() != null && outcome.schedule().getCertificate() != null) {
                    touched.add(outcome.schedule().getCertificate().getId());
                    confirmed.add(roundKey(outcome.schedule()));
                }
                switch (outcome.type()) {
                    case NEW -> {
                        neu++;
                        recalc(outcome.schedule(), false);
                    }
                    case UPDATED -> {
                        updated++;
                        recalc(outcome.schedule(), outcome.changed());
                    }
                    case PENDING_REVIEW -> {
                        pending++;
                        recalc(outcome.schedule(), false); // 보류 → 대기 알림 취소
                    }
                    case SKIPPED -> skipped++;
                    case UNCHANGED -> {
                        // no-op
                    }
                }
            }
            int dropped = dropUnconfirmedApprox(source, touched, confirmed);
            crawlLog.finishSuccess(records.size(), neu, updated, skipped, pending);
            crawlLogRepository.save(crawlLog);
            log.info("[Collect] source={} fetched={} new={} updated={} skipped={} pendingReview={} 추정치정리={}",
                    source.sourceId(), records.size(), neu, updated, skipped, pending, dropped);
        } catch (Exception e) {
            handleFailure(source, crawlLog, e);
        }
    }

    /**
     * 실데이터가 들어온 시험에서 <b>소스가 확인해 주지 않은 추정치 회차를 지운다.</b>
     *
     * <p>정보보안기사가 그랬다(2026-09-04): 시드가 회차 패턴으로 "2026년 3회"를 지어냈는데
     * 시행처에 그런 회차가 없다(제3회는 특성화고 기능사 전용). 스크래퍼가 제1·2·4회를 제대로
     * 물어 온 뒤에도 가짜 3회가 남아 "시행처 확인 필요"로 떠 있었고, 그 접수일은 실제와 달랐다.
     * <b>없는 접수일을 기다리게 하는 건 일정이 없는 것보다 나쁘다.</b>
     *
     * <p>안전장치 둘: ① 지우는 건 <b>추정치(APPROX)뿐</b>이다 — 확정값이 소스에서 사라진 건
     * 사람이 판단할 일이다. ② <b>그 소스가 그 시험의 일정을 실제로 물어 온 경우만</b> 본다 —
     * 사이트가 잠깐 비어 0건이 온 날 멀쩡한 일정을 지우면 안 된다.
     *
     * @return 지운 건수
     */
    private int dropUnconfirmedApprox(ScheduleSource source, Set<Long> touched, Set<String> confirmed) {
        if (touched.isEmpty() || source.coveredAgencies().isEmpty()) {
            return 0;   // 파일 시드는 아무 기관도 맡지 않는다 — 자기가 만든 추정치를 스스로 지우면 안 된다
        }
        List<ExamSchedule> stale = examScheduleRepository
                .findByCertificateIdInAndStatus(List.copyOf(touched), ScheduleStatus.ACTIVE).stream()
                .filter(s -> s.getProvenance() == ScheduleProvenance.APPROX)
                .filter(s -> !confirmed.contains(roundKey(s)))
                .toList();
        for (ExamSchedule s : stale) {
            // 시험 이름은 안 찍는다 — 트랜잭션 밖이라 지연 로딩 프록시를 건드리면 터진다(실측 2026-09-04).
            // 식별자는 프록시를 깨우지 않는다.
            long certId = s.getCertificate().getId();
            notificationScheduleRepository.deleteAll(notificationScheduleRepository.findByExamSchedule(s));
            examScheduleRepository.delete(s);
            log.info("[Collect] source={} 추정치 정리 — cert={} {}년 {}회 {} (시행처가 확인해 주지 않은 회차)",
                    source.sourceId(), certId, s.getYear(), s.getRound(), s.getExamType());
        }
        return stale.size();
    }

    /** 같은 회차인지 보는 키 — (시험, 연도, 회차, 구분). */
    private String roundKey(ExamSchedule s) {
        return s.getCertificate().getId() + "/" + s.getYear() + "/" + s.getRound() + "/" + s.getExamType();
    }

    private void recalc(ExamSchedule schedule, boolean changed) {
        if (schedule == null) {
            return;
        }
        try {
            notificationScheduleService.recalc(schedule, changed);
        } catch (Exception e) {
            log.error("[Collect] 알림 재계산 실패 scheduleId={}", schedule.getId(), e);
        }
    }

    private void handleFailure(ScheduleSource source, CrawlLog crawlLog, Exception e) {
        log.error("[Collect] 수집 실패 source={}", source.sourceId(), e);

        // 직전 실행도 실패였는지 → 2회 연속 실패 시 관리자 알림 (DR-06)
        boolean prevFailed = crawlLogRepository
                .findBySourceOrderByStartedAtDesc(source.sourceId(), PageRequest.of(0, 1))
                .stream().findFirst()
                .map(prev -> !prev.isSuccess())
                .orElse(false);

        crawlLog.finishFailure(e.getMessage());
        crawlLogRepository.save(crawlLog);

        if (prevFailed) {
            // TODO(v2): 관리자 이메일/알림톡. 현재는 로그 경보(운영 주 1회 crawl_log 점검으로 커버).
            log.error("[ADMIN-ALERT] 수집 2회 연속 실패 source={} — 소스 점검 필요. 기존 데이터로 서비스는 지속됩니다.",
                    source.sourceId());
        }
    }
}
