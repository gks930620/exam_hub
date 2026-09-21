package com.test.test.exam.admin;

import com.test.test.exam.domain.NotificationChannel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 알림 발송의 <b>건강 상태</b> — "약속이 지켜지고 있나"에 대한 답.
 *
 * <p>이 서비스의 약속은 하나다: <b>접수 마감을 놓치지 않게 알려 준다.</b> 그런데 그 약속이
 * 지켜지는지 매니저가 볼 화면이 없었다. 수집 쪽에 있던 공백과 같은 종류다
 * ({@link CollectHealth}) — 결과가 서버 로그에만 남으면 아무도 안 본다.
 *
 * <p><b>여기서 가장 위험한 실패</b>는 발송 체인의 꼬리에 있다. {@code NotificationSenderChain} 의
 * 마지막은 {@code LogNotificationSender} 이고, 그건 서버 로그에 한 줄 찍고 <b>언제나 SUCCESS 를
 * 돌려준다.</b> 알림톡이 꺼져 있고(사업자등록 전) 이메일이 꺼져 있거나 실패하면, 발송은 LOG 까지
 * 흘러가 {@code notification_log} 에 <b>channel=LOG, result=SUCCESS</b> 로 적힌다.
 * 통계는 100% 성공인데 <b>받은 사람은 한 명도 없다.</b>
 *
 * <p>그래서 이 클래스는 성공/실패로 세지 않고 <b>채널로 가른다</b>. LOG 는 도달이 아니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationHealth {

    /** 발송 시각이 지났는데 아직 PENDING — 배치가 5분마다 재시도만 하고 있다는 뜻이다 */
    private long overdue;
    /** 앞으로 나갈 대기 예약 */
    private long upcoming;
    /** 실제로 사람에게 닿은 건수(LOG 제외) */
    private long delivered;
    /** 서버 로그에만 찍힌 건수 — 성공으로 기록됐지만 아무도 못 받았다 */
    private long loggedOnly;
    /** 실패로 기록된 건수 */
    private long failed;
    /** 알림이 걸려 있어야 할 회차 수(확정·진행 중·미래) — 예약 0 이 정상인지 아닌지를 가른다 */
    private long armable;
    /** 채널별 발송 건수 — 어디로 나가고 있는지 매니저가 직접 본다 */
    private Map<String, Long> byChannel;
    /** 매니저가 읽을 한 줄 */
    private String message;
    /** 손봐야 하는가 */
    private boolean needsAttention;

    /**
     * @param overdue   sendAt 이 지났는데 아직 PENDING 인 예약 수
     * @param upcoming  아직 발송 시각이 안 된 PENDING 예약 수
     * @param byChannel 최근 기간의 채널별 발송 건수. 없는 채널은 빠져 있어도 된다.
     * @param failed    최근 기간의 실패 건수
     * @param armable   알림이 걸려 있어야 할 회차 수 — 확정(추정 아님)·ACTIVE·아직 안 지난 것.
     *                  이게 있는데 예약이 0 이면 파생이 안 돈 것이고, 이게 0 이면 예약 0 이 정상이다.
     */
    public static NotificationHealth of(long overdue, long upcoming,
                                        Map<NotificationChannel, Long> byChannel, long failed,
                                        long armable) {
        Map<NotificationChannel, Long> counts = byChannel == null ? Map.of() : byChannel;

        long loggedOnly = counts.getOrDefault(NotificationChannel.LOG, 0L);
        long delivered = counts.entrySet().stream()
                .filter(e -> e.getKey() != NotificationChannel.LOG)
                .mapToLong(e -> e.getValue() == null ? 0L : e.getValue())
                .sum();

        // 화면에 보일 채널별 건수 — 0 인 채널도 자리를 지켜야 "알림톡이 안 켜졌구나"가 보인다
        Map<String, Long> shown = new LinkedHashMap<>();
        for (NotificationChannel c : NotificationChannel.values()) {
            Long v = counts.get(c);
            shown.put(c.name(), v == null ? 0L : v);
        }

        List<String> problems = new ArrayList<>();

        // 1. 막힘이 가장 급하다 — 지금 이 순간 못 나가고 있는 알림이다.
        if (overdue > 0) {
            problems.add("발송 시각이 지난 예약이 " + overdue + "건 남아 있습니다. "
                    + "발송이 막혔을 수 있습니다 — 메일 설정(MAIL_ENABLED·자격증명)을 확인하세요.");
        }

        // 2. 보낼 게 있는데 예약이 하나도 없다 — 파생이 안 돌았다.
        //    CollectService.recalc 는 예외를 삼키고 로그만 남긴다(수집이 알림 실패로 멈추면 안 되니까).
        //    그래서 파생이 통째로 실패해도 수집은 성공으로 기록되고, 발송할 게 없으니 발송 통계도 조용하다.
        //    아무 경보 없이 아무 알림도 안 나가는 상태가 되는데, 그게 정확히 이 서비스가 하는 일이다.
        if (armable > 0 && upcoming == 0) {
            problems.add("알림이 걸려 있어야 할 회차가 " + armable + "건인데 예약이 하나도 없습니다 — "
                    + "알림 파생이 실패했을 수 있습니다. 수집 로그에서 알림 재계산 실패를 확인하세요.");
        }

        // 3. 아무도 못 받은 상태 — 통계는 성공인데 도달이 0 이다. 가장 조용한 실패다.
        if (loggedOnly > 0 && delivered == 0) {
            problems.add("최근 발송 " + loggedOnly + "건이 전부 서버 로그로만 나갔습니다 — "
                    + "실제로는 아무에게도 가지 않았습니다. 알림톡·이메일 중 최소 하나를 켜야 합니다.");
        }

        // 4. 실패 기록.
        if (failed > 0) {
            problems.add("최근 발송 중 " + failed + "건이 실패했습니다.");
        }

        if (problems.isEmpty()) {
            String ok = delivered > 0
                    ? "최근 " + delivered + "건이 정상 발송됐습니다."
                    : "아직 나간 알림이 없습니다. 대기 중인 예약은 " + upcoming + "건입니다.";
            return new NotificationHealth(overdue, upcoming, delivered, loggedOnly, failed,
                    armable, shown, ok, false);
        }
        return new NotificationHealth(overdue, upcoming, delivered, loggedOnly, failed,
                armable, shown, String.join(" ", problems), true);
    }
}
