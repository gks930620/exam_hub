package com.test.test.exam.admin;

import com.test.test.exam.domain.NotificationChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>알림이 실제로 사람에게 닿고 있는가.</b>
 *
 * <p>이 서비스의 약속은 하나다 — "접수 마감을 놓치지 않게 알려 드립니다". 그런데 그 약속이
 * 지켜지고 있는지 <b>매니저가 볼 방법이 없었다.</b> 발송 결과는 {@code notification_log} 와
 * 서버 로그에만 남았다(수집과 같은 공백).
 *
 * <p>게다가 가장 위험한 실패가 조용하다. 발송 체인의 마지막은 {@code LogNotificationSender} 이고
 * 그건 <b>언제나 SUCCESS 를 돌려준다.</b> 알림톡이 꺼져 있고 이메일이 실패하면 로그만 찍고
 * "발송 성공"으로 기록된다 — <b>아무도 못 받았는데 시스템은 100% 성공이라고 믿는다.</b>
 * 그래서 채널을 갈라 본다: LOG 는 도달이 아니다.
 */
class NotificationHealthTest {

    private static Map<NotificationChannel, Long> channels(long alimtalk, long email, long logOnly) {
        return Map.of(
                NotificationChannel.ALIMTALK, alimtalk,
                NotificationChannel.EMAIL, email,
                NotificationChannel.LOG, logOnly);
    }

    @Test
    @DisplayName("보낼 것도 보낸 것도 없으면 조용하다 — 아직 아무 일도 안 일어난 상태다")
    void nothing_yet_is_not_an_alarm() {
        NotificationHealth h = NotificationHealth.of(0, 0, channels(0, 0, 0), 0, 0);

        assertFalse(h.isNeedsAttention());
        assertEquals(0, h.getDelivered());
    }

    /**
     * <b>가장 위험한 실패.</b> LOG 채널은 서버 로그에 한 줄 찍는 것뿐인데 SUCCESS 로 기록된다.
     * 전부 LOG 로 나갔다면 사용자는 한 명도 못 받았다.
     */
    @Test
    @DisplayName("전부 LOG 채널로 나갔으면 아무도 못 받은 것이다")
    void all_log_channel_means_nobody_got_it() {
        NotificationHealth h = NotificationHealth.of(0, 0, channels(0, 0, 42), 0, 0);

        assertTrue(h.isNeedsAttention(), "아무도 못 받았는데 정상으로 보면 약속이 깨진 걸 영영 모른다");
        assertEquals(0, h.getDelivered(), "LOG 는 도달이 아니다");
        assertEquals(42, h.getLoggedOnly());
        assertTrue(h.getMessage().contains("아무에게도"), h.getMessage());
    }

    @Test
    @DisplayName("이메일로 나간 것이 있으면 도달로 센다")
    void email_counts_as_delivered() {
        NotificationHealth h = NotificationHealth.of(0, 0, channels(0, 30, 0), 0, 0);

        assertFalse(h.isNeedsAttention());
        assertEquals(30, h.getDelivered());
    }

    /**
     * 발송 시각이 지났는데 아직 PENDING 이면 발송이 막힌 것이다 — 배치가 5분마다 재시도만 하고 있다.
     * (SMTP 자격증명 만료가 이렇게 보인다)
     */
    @Test
    @DisplayName("발송 시각이 지났는데 안 나간 예약이 있으면 막힌 것이다")
    void overdue_pending_is_stuck() {
        NotificationHealth h = NotificationHealth.of(7, 20, channels(0, 100, 0), 0, 300);

        assertTrue(h.isNeedsAttention());
        assertEquals(7, h.getOverdue());
        assertTrue(h.getMessage().contains("7"), h.getMessage());
    }

    @Test
    @DisplayName("실패 기록이 있으면 알린다")
    void failures_are_reported() {
        NotificationHealth h = NotificationHealth.of(0, 10, channels(0, 50, 0), 12, 300);

        assertTrue(h.isNeedsAttention());
        assertEquals(12, h.getFailed());
    }

    @Test
    @DisplayName("앞으로 나갈 예약만 있는 정상 상태는 손댈 것이 없다")
    void healthy_pipeline_is_quiet() {
        NotificationHealth h = NotificationHealth.of(0, 143, channels(0, 88, 0), 0, 300);

        assertFalse(h.isNeedsAttention());
        assertEquals(143, h.getUpcoming());
        assertEquals(88, h.getDelivered());
    }

    /** 일부만 LOG 면 "아무도 못 받았다"가 아니다 — 이메일 없는 회원이 섞였을 뿐이다. */
    @Test
    @DisplayName("일부만 LOG 면 경보가 아니라 사실만 남긴다")
    void partial_log_is_not_an_alarm() {
        NotificationHealth h = NotificationHealth.of(0, 0, channels(0, 90, 10), 0, 0);

        assertFalse(h.isNeedsAttention());
        assertEquals(90, h.getDelivered());
        assertEquals(10, h.getLoggedOnly());
    }

    /**
     * <b>수집은 되는데 알림이 안 걸린 상태.</b> {@code CollectService.recalc} 는 예외를 삼키고 로그만
     * 남긴다(수집이 알림 실패로 멈추면 안 되니까). 그래서 파생이 통째로 실패해도 수집은 성공으로
     * 기록되고, 예약 테이블만 비어 있다. 발송할 게 없으니 발송 통계도 조용하다 —
     * 아무 경보 없이 아무 알림도 안 나가는 상태가 된다.
     */
    @Test
    @DisplayName("무장돼야 할 회차가 있는데 예약이 0건이면 파생이 안 돈 것이다")
    void schedules_without_reservations_is_broken() {
        NotificationHealth h = NotificationHealth.of(0, 0, channels(0, 0, 0), 0, 312);

        assertTrue(h.isNeedsAttention(), "보낼 게 있는데 예약이 없으면 알림은 영영 안 나간다");
        assertTrue(h.getMessage().contains("312"), h.getMessage());
    }

    /** 확정 일정이 아예 없으면 예약 0 이 정상이다 — 새 환경에서 거짓 경보를 내지 않는다. */
    @Test
    @DisplayName("무장할 회차 자체가 없으면 예약 0 은 정상이다")
    void no_schedules_no_alarm() {
        NotificationHealth h = NotificationHealth.of(0, 0, channels(0, 0, 0), 0, 0);

        assertFalse(h.isNeedsAttention());
    }
}
