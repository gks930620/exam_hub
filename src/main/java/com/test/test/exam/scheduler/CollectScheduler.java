package com.test.test.exam.scheduler;

import com.test.test.exam.collect.CollectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 수집 배치 (설계 05 §4, DR-02). noshow-guard scheduler 패키지 패턴 준용.
 * - 05:00 전체 수집
 * - 17:00 접수 임박(7일 이내) 종목 재확인
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectScheduler {

    private final CollectService collectService;

    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")
    public void collectDaily() {
        log.info("[Scheduler] 일 1회 전체 수집 시작(05:00 KST)");
        collectService.collectAll();
    }

    @Scheduled(cron = "0 0 17 * * *", zone = "Asia/Seoul")
    public void collectImminent() {
        log.info("[Scheduler] 접수 임박 종목 재확인 시작(17:00 KST)");
        collectService.collectImminent();
    }
}
