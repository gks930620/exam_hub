package com.test.test.exam.scheduler;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.notification.NotificationDispatchService;
import com.test.test.exam.repository.CrawlLogRepository;
import com.test.test.exam.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발송 배치 + 보존 정책 배치 (설계 04 §3-4/§3-5).
 * noshow-guard notification 패키지 패턴 준용.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final NotificationDispatchService dispatchService;
    private final NotificationLogRepository notificationLogRepository;
    private final CrawlLogRepository crawlLogRepository;

    /** 매 5분 발송 배치 (NFR-03: 예약 시각 ±5분). */
    @Scheduled(fixedDelay = 300_000L, initialDelay = 30_000L)
    public void dispatch() {
        dispatchService.dispatchDue();
    }

    /** 월 1회 로그 정리 (notification_log 180일 / crawl_log 90일). */
    @Scheduled(cron = "0 0 4 1 * *", zone = "Asia/Seoul")
    @Transactional
    public void cleanupOldLogs() {
        int notif = notificationLogRepository.deleteOlderThan(TimeUtil.now().minusDays(180));
        int crawl = crawlLogRepository.deleteOlderThan(TimeUtil.now().minusDays(90));
        log.info("[Scheduler] 로그 정리 — notification_log {}건, crawl_log {}건 삭제", notif, crawl);
    }
}
