package com.test.test.exam.collect;

import com.test.test.exam.domain.CrawlLog;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.notification.NotificationScheduleService;
import com.test.test.exam.repository.CertificateRepository;
import com.test.test.exam.repository.CrawlLogRepository;
import com.test.test.exam.repository.ExamScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

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
     * 종목 지정 조회가 되는 소스(큐넷 API)만 부른다 — 스크래퍼를 전량 긁어 종목 하나를 고르는 건 낭비다.
     */
    public void collectByCodes(List<String> sourceCodes) {
        for (ScheduleSource source : batchSources(ScheduleSource::supportsPartialFetch)) {
            try {
                runSource(source, source.fetchByCertificateCodes(sourceCodes));
            } catch (Exception e) {
                log.error("[Collect] source={} 재수집 실패 — 다른 소스는 계속합니다: {}",
                        source.sourceId(), e.toString());
            }
        }
    }

    /** 접수 임박 종목 재확인 (17:00 배치). 7일 이내 접수 시작 종목만. 종목 지정 조회가 되는 소스만. */
    public void collectImminent() {
        LocalDateTime now = TimeUtil.now();
        List<Long> certIds = examScheduleRepository.findCertificateIdsWithImminentRegistration(
                now, now.plusDays(7));
        if (certIds.isEmpty()) {
            log.info("[Collect] 임박 종목 없음 — 재확인 배치 스킵");
            return;
        }
        List<String> sourceCodes = certificateRepository.findAllById(certIds).stream()
                .map(Certificate::getSourceCode)
                .filter(java.util.Objects::nonNull)
                .distinct().toList();

        for (ScheduleSource source : batchSources(ScheduleSource::supportsPartialFetch)) {
            try {
                runSource(source, source.fetchByCertificateCodes(sourceCodes));
            } catch (Exception e) {
                log.error("[Collect] source={} 임박 재확인 실패 — 다른 소스는 계속합니다: {}",
                        source.sourceId(), e.toString());
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
        try {
            for (CollectedSchedule rec : records) {
                DiffService.Outcome outcome = diffService.upsert(rec);
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
            crawlLog.finishSuccess(records.size(), neu, updated, skipped, pending);
            crawlLogRepository.save(crawlLog);
            log.info("[Collect] source={} fetched={} new={} updated={} skipped={} pendingReview={}",
                    source.sourceId(), records.size(), neu, updated, skipped, pending);
        } catch (Exception e) {
            handleFailure(source, crawlLog, e);
        }
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
