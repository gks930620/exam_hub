package com.test.test.exam.admin;

import com.test.test.exam.domain.NotificationChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>고장이 나면 열지 않아도 알 수 있어야 한다.</b>
 *
 * <p>{@code /admin/health} 를 만들었지만 그건 <b>사람이 열어야</b> 보인다. 혼자 운영하는 서비스에서
 * 매일 그 화면을 여는 사람은 없다. 스크래퍼가 조용히 죽으면 옛 일정이 그대로 서비스되고,
 * 사용자는 지난 날짜를 믿고 준비한다 — 그 사이 아무 신호가 없다.
 *
 * <p>그래서 하루 한 번, <b>고장이 있을 때만</b> 메일을 보낸다. 멀쩡한 날에도 보내면 곧 안 읽게 되고,
 * 그러면 없는 것과 같아진다.
 *
 * <p>순수 판단이라 컨벤션 §6 의 예외로 둔다 — 메일을 실제로 쏘는 부분과 분리해 둔 이유이기도 하다.
 */
class OperatorAlertTest {

    private static CollectHealth ok(String source) {
        return new CollectHealth(source, CollectHealth.State.OK, "정상",
                "112건을 가져왔습니다.", "2026-09-22T05:00", 112, 0, false);
    }

    private static CollectHealth broken(String source, CollectHealth.State state, String message) {
        return new CollectHealth(source, state, state.getLabel(), message,
                "2026-09-22T05:00", 0, 2, true);
    }

    private static NotificationHealth healthyNotifications() {
        return NotificationHealth.of(0, 143, Map.of(NotificationChannel.EMAIL, 88L), 0, 300);
    }

    @Test
    @DisplayName("다 멀쩡하면 메일을 보내지 않는다 — 안 읽히는 메일은 없는 것과 같다")
    void quiet_when_everything_works() {
        Optional<OperatorAlert> alert = OperatorAlert.of(
                List.of(ok("QNET_API"), ok("YBM_WEB")), healthyNotifications(), "https://exam.example.com");

        assertThat(alert).isEmpty();
    }

    @Test
    @DisplayName("고장난 소스를 제목에서 바로 알 수 있다")
    void subject_says_how_many_are_broken() {
        Optional<OperatorAlert> alert = OperatorAlert.of(
                List.of(ok("QNET_API"),
                        broken("TOPIK_WEB", CollectHealth.State.EMPTY, "오류 없이 0건을 가져왔습니다."),
                        broken("KCA_WEB", CollectHealth.State.FAILED, "2회 연속 실패 — 연결 시간 초과")),
                healthyNotifications(), "https://exam.example.com");

        assertThat(alert).isPresent();
        assertThat(alert.get().getSubject()).contains("2");
        assertThat(alert.get().getBody()).contains("TOPIK_WEB").contains("KCA_WEB");
        assertThat(alert.get().getBody()).doesNotContain("QNET_API");
    }

    /** 고쳐야 할 곳으로 바로 갈 수 있어야 한다 — 주소를 찾아 헤매면 그날 안 고친다. */
    @Test
    @DisplayName("본문에 상태 화면 주소가 들어간다")
    void body_links_to_the_health_screen() {
        Optional<OperatorAlert> alert = OperatorAlert.of(
                List.of(broken("KCA_WEB", CollectHealth.State.FAILED, "연결 시간 초과")),
                healthyNotifications(), "https://exam.example.com");

        assertThat(alert.get().getBody()).contains("https://exam.example.com/admin/health");
    }

    /**
     * 수집이 멀쩡해도 알림이 안 나가면 서비스는 아무 일도 안 한 것이다.
     * 이쪽이 오히려 더 급하다 — 약속 그 자체가 깨진 상태다.
     */
    @Test
    @DisplayName("수집이 멀쩡해도 알림이 막혔으면 알린다")
    void notification_trouble_alone_is_enough() {
        NotificationHealth stuck = NotificationHealth.of(
                7, 20, Map.of(NotificationChannel.EMAIL, 100L), 0, 300);

        Optional<OperatorAlert> alert = OperatorAlert.of(
                List.of(ok("QNET_API")), stuck, "https://exam.example.com");

        assertThat(alert).isPresent();
        assertThat(alert.get().getBody()).contains("발송이 막혔을 수 있습니다");
    }

    @Test
    @DisplayName("둘 다 고장이면 둘 다 적는다 — 하나만 보고 다 고쳤다고 생각하면 안 된다")
    void both_kinds_are_reported() {
        NotificationHealth stuck = NotificationHealth.of(
                5, 20, Map.of(NotificationChannel.LOG, 40L), 0, 300);

        Optional<OperatorAlert> alert = OperatorAlert.of(
                List.of(broken("KCA_WEB", CollectHealth.State.FAILED, "연결 시간 초과")),
                stuck, "https://exam.example.com");

        assertThat(alert.get().getBody()).contains("KCA_WEB");
        assertThat(alert.get().getBody()).contains("아무에게도");
    }

    /** 기록이 아직 없는 첫날 — 고장이 아니라 아직 안 돈 것이다. 그래도 알려는 준다. */
    @Test
    @DisplayName("한 번도 안 돈 소스도 손봐야 할 것으로 적는다")
    void never_ran_is_worth_saying() {
        Optional<OperatorAlert> alert = OperatorAlert.of(
                List.of(new CollectHealth("HSK_WEB", CollectHealth.State.NEVER_RAN, "기록 없음",
                        "아직 한 번도 돌지 않았습니다.", null, 0, 0, true)),
                healthyNotifications(), "https://exam.example.com");

        assertThat(alert).isPresent();
        assertThat(alert.get().getBody()).contains("HSK_WEB");
    }
}
