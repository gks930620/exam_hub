package com.test.test.exam.admin;

import com.test.test.exam.collect.ScheduleSource;
import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.CrawlLog;
import com.test.test.exam.domain.NotificationChannel;
import com.test.test.exam.domain.NotificationResult;
import com.test.test.exam.domain.NotificationScheduleStatus;
import com.test.test.exam.notification.NotificationSender;
import com.test.test.exam.notification.NotificationSenderChain;
import com.test.test.exam.repository.CrawlLogRepository;
import com.test.test.exam.repository.ExamScheduleRepository;
import com.test.test.exam.repository.NotificationLogRepository;
import com.test.test.exam.repository.NotificationScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * "지금 서비스가 제대로 돌고 있나"를 재는 <b>한 곳</b>.
 *
 * <p>매니저 화면과 고장 경보 메일이 같은 답을 내야 한다. 각자 세면 어느 날 조용히 갈라지고,
 * 그때는 둘 중 어느 쪽을 믿어야 할지 알 수 없다 — 이 프로젝트가 화면끼리 셈법을 통일해 온 이유와 같다.
 */
@Service
@RequiredArgsConstructor
public class HealthService {

    /** 수집 판정에 쓰는 기록 범위. 연속 실패를 세려면 며칠치는 있어야 한다. */
    static final int COLLECT_LOOKBACK_DAYS = 30;
    /** 발송 통계 범위. 알림은 매일 나가는 게 아니라 회차 근처에 몰려서, 짧게 보면 늘 0 이다. */
    static final int NOTIFY_LOOKBACK_DAYS = 30;

    private final CrawlLogRepository crawlLogRepository;
    private final List<ScheduleSource> sources;
    private final NotificationScheduleRepository notificationScheduleRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final ExamScheduleRepository examScheduleRepository;
    private final List<NotificationSender> senders;

    /**
     * 소스별 수집 건강. <b>살아 있는 소스를 기준으로</b> 돈다 — 기록만 훑으면 한 번도 안 돈 소스가
     * 목록에서 통째로 빠져 조용히 넘어간다.
     */
    @Transactional(readOnly = true)
    public List<CollectHealth> collectHealth() {
        LocalDateTime now = TimeUtil.now();
        Map<String, List<CrawlLog>> bySource = crawlLogRepository
                .findByStartedAtAfterOrderByStartedAtDesc(now.minusDays(COLLECT_LOOKBACK_DAYS))
                .stream().collect(Collectors.groupingBy(CrawlLog::getSource));

        return sources.stream()
                .filter(ScheduleSource::usesNetwork)
                .map(s -> CollectHealth.of(s.sourceId(), bySource.get(s.sourceId()), now, s.siteUrl()))
                .sorted(Comparator.comparing((CollectHealth h) -> !h.isNeedsAttention())
                        .thenComparing(CollectHealth::getSource))
                .toList();
    }

    /** 알림 건강 — 성공률이 아니라 <b>도달</b>을 센다. LOG 채널은 도달이 아니다. */
    @Transactional(readOnly = true)
    public NotificationHealth notificationHealth() {
        LocalDateTime now = TimeUtil.now();
        LocalDateTime since = now.minusDays(NOTIFY_LOOKBACK_DAYS);

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

        return NotificationHealth.of(overdue, upcoming, byChannel, failed, armable);
    }

    /**
     * 지금 실제로 떠 있는 발송 채널. 빈이 뜨는 조건이 곧 설정이라
     * ({@code notification.*.enabled}) 이 목록이 "무엇이 켜져 있나"의 정답이다.
     *
     * <p>LOG 하나뿐이면 아직 아무 데도 못 보내는 상태다.
     */
    public List<String> liveChannels() {
        return senders.stream()
                .filter(s -> !(s instanceof NotificationSenderChain))
                .map(s -> s.channel().name())
                .distinct()
                .sorted()
                .toList();
    }
}
