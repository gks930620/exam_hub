package com.test.test.exam.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    private final HealthService healthService;

    @GetMapping
    public ResponseEntity<NotificationHealthResponse> health() {
        return ResponseEntity.ok(new NotificationHealthResponse(
                healthService.notificationHealth(),
                healthService.liveChannels(),
                HealthService.NOTIFY_LOOKBACK_DAYS));
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
