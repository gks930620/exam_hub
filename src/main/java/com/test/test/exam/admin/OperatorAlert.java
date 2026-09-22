package com.test.test.exam.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Optional;

/**
 * 운영자에게 보낼 <b>고장 알림</b> 한 통 — 보낼지 말지, 뭐라고 쓸지.
 *
 * <p>{@code /admin/health} 는 <b>사람이 열어야</b> 보인다. 혼자 운영하는 서비스에서 매일 그 화면을
 * 여는 사람은 없다. 스크래퍼가 조용히 죽으면 DB 에는 옛 일정이 남아 사용자 화면은 멀쩡해 보이고,
 * 그 사이 사용자는 지난 날짜를 믿고 준비한다. 화면을 만든 것만으로는 이 구멍이 안 막힌다.
 *
 * <p><b>고장이 있을 때만 보낸다.</b> 멀쩡한 날에도 보내면 곧 안 읽게 되고, 그러면 없는 것과 같아진다.
 *
 * <p>메일을 실제로 쏘는 일과 분리해 둔다 — 무엇을 알릴지는 시간·네트워크 없이 시험할 수 있어야 한다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OperatorAlert {

    private String subject;
    private String body;

    /**
     * @param sources    소스별 수집 건강
     * @param notify     알림 건강
     * @param baseUrl    공개 주소 — 본문에 상태 화면 링크를 넣는다. 고칠 곳으로 바로 못 가면 그날 안 고친다.
     * @return 손볼 것이 하나도 없으면 비어 있음
     */
    public static Optional<OperatorAlert> of(List<CollectHealth> sources,
                                             NotificationHealth notify,
                                             String baseUrl) {
        List<CollectHealth> broken = sources == null ? List.of()
                : sources.stream().filter(CollectHealth::isNeedsAttention).toList();
        boolean notifyTrouble = notify != null && notify.isNeedsAttention();

        if (broken.isEmpty() && !notifyTrouble) {
            return Optional.empty();
        }

        StringBuilder subject = new StringBuilder("[모든시험한번에보기] ");
        if (!broken.isEmpty()) {
            subject.append("수집 ").append(broken.size()).append("건");
        }
        if (notifyTrouble) {
            subject.append(broken.isEmpty() ? "알림 점검 필요" : " · 알림 점검 필요");
        } else {
            subject.append(" 점검 필요");
        }

        StringBuilder body = new StringBuilder();

        if (notifyTrouble) {
            // 알림이 먼저다 — 수집이 멀쩡해도 알림이 안 나가면 서비스는 아무 일도 안 한 것이다.
            body.append("■ 알림\n").append("  ").append(notify.getMessage()).append("\n\n");
        }

        if (!broken.isEmpty()) {
            body.append("■ 수집 — 손봐야 할 소스 ").append(broken.size()).append("개\n");
            for (CollectHealth h : broken) {
                body.append("  · ").append(h.getSource())
                        .append(" [").append(h.getStateLabel()).append("] ")
                        .append(h.getMessage()).append("\n");
            }
            body.append("\n");
        }

        body.append("상태 화면: ").append(baseUrl == null ? "" : baseUrl).append("/admin/health\n\n")
                .append("이 메일은 고장이 있을 때만 갑니다. 조용한 날은 정상이라는 뜻입니다.\n");

        return Optional.of(new OperatorAlert(subject.toString(), body.toString()));
    }
}
