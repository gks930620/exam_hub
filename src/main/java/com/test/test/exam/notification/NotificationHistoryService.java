package com.test.test.exam.notification;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.NotificationChannel;
import com.test.test.exam.domain.NotificationEventType;
import com.test.test.exam.domain.NotificationLog;
import com.test.test.exam.domain.NotificationResult;
import com.test.test.exam.repository.NotificationLogRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <b>나한테 뭘 보냈다는 건지</b> 보여준다.
 *
 * <p>{@code notification_log} 에 발송 기록이 쌓이지만 매니저 집계에만 쓰였다. 사용자는
 * "왔다는데 나는 못 받았다"를 확인할 길이 없었다. 확인 메일 버튼과 짝이다 —
 * 그 버튼은 "지금 보내면 오나", 이 목록은 "그동안 뭘 보냈나"를 답한다. 둘이 어긋나면
 * 사용자가 <b>접수를 놓치기 전에</b> 알아챈다.
 *
 * <p><b>도달과 성공을 구분한다.</b> 발송 체인의 끝 {@link LogNotificationSender} 는 서버 로그에
 * 한 줄 찍고 성공을 돌려준다. 그걸 "보냈습니다"로 보여주면 사용자는 오지도 않은 메일을 기다린다.
 */
@Service
@RequiredArgsConstructor
public class NotificationHistoryService {

    /** 화면에 한 번에 보여줄 수. 이보다 옛날 것을 찾는 사람은 없다(보존도 180일이다). */
    static final int LIMIT = 50;

    private final NotificationLogRepository notificationLogRepository;

    @Transactional(readOnly = true)
    public List<Item> recent(Long memberId) {
        return notificationLogRepository.findRecentByMember(memberId, PageRequest.of(0, LIMIT))
                .stream().map(Item::from).toList();
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String certificateName;
        /** 몇 회 무슨 시험인지 — 화면·메일과 같은 문구를 쓴다 */
        private String round;
        /** 무슨 일로 보냈나 */
        private String eventLabel;
        private String sentAt;
        /** 실제로 쓰인 채널 */
        private String channel;
        /**
         * <b>사람에게 닿았나.</b> LOG 채널은 서버 기록일 뿐이라 false 다 —
         * 성공으로만 세면 아무도 못 받은 날도 100% 로 보인다.
         */
        private boolean delivered;

        public static Item from(NotificationLog l) {
            var schedule = l.getNotificationSchedule().getExamSchedule();
            boolean delivered = l.getResult() == NotificationResult.SUCCESS
                    && l.getChannel() != NotificationChannel.LOG;
            return new Item(
                    schedule.getCertificate().getName(),
                    schedule.roundLabel(),
                    label(l.getNotificationSchedule().getEventType()),
                    TimeUtil.format(l.getSentAt()),
                    l.getChannel().name(),
                    delivered);
        }

        /** 사용자가 읽을 말로. 코드 이름({@code REG_CLOSE_EVE})을 그대로 보여주지 않는다. */
        private static String label(NotificationEventType type) {
            return switch (type) {
                case REG_OPEN_EVE -> "접수 시작 하루 전";
                case REG_OPEN_DAY -> "접수 시작";
                case REG_CLOSE_EVE -> "접수 마감 하루 전";
                case EXAM_D7 -> "시험 7일 전";
                case EXAM_D1 -> "시험 하루 전";
                case SCHEDULE_CHANGED -> "일정 변경";
            };
        }
    }
}
