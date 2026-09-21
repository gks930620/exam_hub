package com.test.test.exam.admin;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.NotificationChannel;
import com.test.test.exam.domain.NotificationResult;
import com.test.test.exam.domain.NotificationScheduleStatus;
import com.test.test.exam.notification.NotificationSender;
import com.test.test.exam.notification.NotificationSenderChain;
import com.test.test.exam.repository.ExamScheduleRepository;
import com.test.test.exam.repository.NotificationLogRepository;
import com.test.test.exam.repository.NotificationScheduleRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 매니저용 <b>알림 건강</b> — "약속이 지켜지고 있나".
 *
 * <p>이 서비스의 약속은 하나다: 접수 마감을 놓치지 않게 알려 준다. 그 약속이 지켜지는지 볼 화면이
 * 없었다 — 수집 쪽에 있던 것과 같은 공백이다({@link AdminCollectHealthController}).
 *
 * <p>여기서 보는 것은 성공률이 아니라 <b>도달</b>이다. 발송 체인의 마지막
 * {@code LogNotificationSender} 가 언제나 SUCCESS 를 돌려주기 때문에, 성공률만 보면
 * <b>아무도 못 받은 날도 100%</b> 로 보인다. 그래서 채널로 가른다 — LOG 는 도달이 아니다.
 *
 * <p>{@code /api/admin/**} 이라 ADMIN 만 볼 수 있다.
 */
@RestController
@RequestMapping("/api/admin/notification-health")
@RequiredArgsConstructor
public class AdminNotificationHealthController {

    /** 판정에 쓰는 발송 기록 범위. 알림은 매일 나가는 게 아니라 회차 근처에 몰려서, 짧게 보면 늘 0 이다. */
    private static final int LOOKBACK_DAYS = 30;

    private final NotificationScheduleRepository notificationScheduleRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final ExamScheduleRepository examScheduleRepository;
    private final List<NotificationSender> senders;

    @GetMapping
    public ResponseEntity<NotificationHealthResponse> health() {
        LocalDateTime now = TimeUtil.now();
        LocalDateTime since = now.minusDays(LOOKBACK_DAYS);

        long overdue = notificationScheduleRepository
                .countByStatusAndSendAtLessThanEqual(NotificationScheduleStatus.PENDING, now);
        long upcoming = notificationScheduleRepository
                .countByStatusAndSendAtGreaterThan(NotificationScheduleStatus.PENDING, now);

        Map<NotificationChannel, Long> byChannel = new EnumMap<>(NotificationChannel.class);
        for (Object[] row : notificationLogRepository.countByChannelSince(since, NotificationResult.SUCCESS)) {
            byChannel.put((NotificationChannel) row[0], (Long) row[1]);
        }
        long failed = notificationLogRepository.countFailedSince(since);

        // 예약이 0 일 때 그게 정상인지 고장인지는 "보낼 게 있었나"로만 갈린다.
        long armable = examScheduleRepository.countArmable(now, now.toLocalDate());

        NotificationHealth health = NotificationHealth.of(overdue, upcoming, byChannel, failed, armable);

        return ResponseEntity.ok(new NotificationHealthResponse(
                health, liveChannels(), LOOKBACK_DAYS));
    }

    /**
     * 지금 실제로 떠 있는 발송 채널. 빈이 뜨는 조건이 곧 설정이라
     * ({@code notification.*.enabled}) 이 목록이 "무엇이 켜져 있나"의 정답이다.
     *
     * <p>LOG 하나뿐이면 아직 아무 데도 못 보내는 상태다 — 화면이 그걸 먼저 말해 준다.
     */
    private List<String> liveChannels() {
        return senders.stream()
                .filter(s -> !(s instanceof NotificationSenderChain))
                .map(s -> s.channel().name())
                .distinct()
                .sorted()
                .toList();
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationHealthResponse {
        private NotificationHealth health;
        /** 지금 떠 있는 발송 채널 — LOG 뿐이면 실제 발송 수단이 없다 */
        private List<String> liveChannels;
        /** 발송 통계를 센 기간(일) — 화면이 "최근 30일" 이라고 쓸 수 있게 */
        private int lookbackDays;
    }
}
