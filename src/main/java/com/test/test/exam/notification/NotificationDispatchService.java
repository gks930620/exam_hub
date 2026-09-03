package com.test.test.exam.notification;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.NotificationScheduleStatus;
import com.test.test.exam.repository.NotificationScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 발송 배치 (설계 04 §3-4). 도래한 PENDING 예약 → 관심 사용자 × 토글 필터 → 발송 →
 * notification_log UNIQUE 제약으로 멱등 처리 → 예약 SENT.
 *
 * <p><b>배치는 트랜잭션이 아니다.</b> 조회만 하고, 건별 처리는 {@link NotificationDispatchWorker} 가
 * 각각 새 트랜잭션으로 한다. 배치 전체를 한 트랜잭션에 넣으면 ① 한 건의 예외가 앞서 남긴 발송 로그까지
 * 되돌려 다음 배치에서 <b>같은 알림이 또 나가고</b> ② 수백 건을 보내는 동안 DB 커넥션을 붙잡는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchService {

    /** 한 배치에서 처리할 상한. 5분마다 도니 밀린 게 있어도 다음 배치가 이어 받는다. */
    static final int BATCH_LIMIT = 500;

    private final NotificationScheduleRepository notificationScheduleRepository;
    private final NotificationDispatchWorker worker;

    /** 도래한 예약을 처리. 반환: 실제 발송(신규 로그) 건수. */
    public int dispatchDue() {
        List<Long> due = notificationScheduleRepository.findDueIds(
                NotificationScheduleStatus.PENDING, TimeUtil.now(), PageRequest.of(0, BATCH_LIMIT));

        int sentCount = 0;
        int failed = 0;
        for (Long id : due) {
            try {
                sentCount += worker.dispatchOne(id);
            } catch (Exception e) {
                // 한 건이 죽어도 나머지는 나가야 한다 — 이 건은 PENDING 으로 남아 다음 배치가 다시 시도한다
                failed++;
                log.error("[Dispatch] 예약 {} 처리 실패 — 다음 배치에서 재시도: {}", id, e.toString());
            }
        }
        if (!due.isEmpty()) {
            log.info("[Dispatch] 도래 예약 {}건 처리, 신규 발송 {}건, 실패 {}건", due.size(), sentCount, failed);
        }
        return sentCount;
    }
}
