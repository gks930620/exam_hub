package com.test.test.exam.notification;

import com.test.test.common.exception.BusinessRuleException;
import com.test.test.exam.domain.Member;
import com.test.test.exam.domain.NotificationChannel;
import com.test.test.exam.domain.NotificationResult;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * <b>"내 주소로 진짜 오나"를 지금 확인해 보는 한 통.</b>
 *
 * <p>이 서비스가 하는 일은 하나다: 접수 마감 전에 메일을 보낸다. 그런데 주소에 오타가 하나 있으면
 * ({@code gmail} 을 {@code gmial} 로) 형식 검증은 통과하고, 발송도 성공으로 기록되고,
 * <b>메일만 조용히 사라진다.</b> 사용자는 시험 접수를 놓친 뒤에야 안다 — 그때는 이미 늦다.
 *
 * <p>그래서 스스로 눌러 볼 수 있게 한다. 지금 안 오면 지금 고칠 수 있다.
 *
 * <p><b>정직하게 답하는 것이 핵심이다.</b> 발송 체인의 끝 {@link LogNotificationSender} 는 서버
 * 로그에 한 줄 찍고 언제나 성공을 돌려준다. 그 길로 빠졌는데 "보냈습니다"라고 하면 사용자는
 * 오지도 않을 메일을 기다린다. 그래서 <b>실제로 쓰인 채널</b>을 보고 도달 여부를 가른다.
 *
 * <p>회차 알림의 발송 이력({@code notification_log})에는 남기지 않는다 — 그건 (회원, 예약)
 * 멱등키를 가진 표라 확인용 한 통이 끼면 진짜 알림을 막는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestNotificationService {

    private final NotificationSender sender;   // 체인(@Primary)

    /**
     * @throws BusinessRuleException 받을 주소가 없을 때. 조용히 성공으로 답하면
     *                               사용자는 설정이 끝났다고 믿는다.
     */
    public Result send(Member member) {
        if (!member.canReceiveEmail()) {
            throw new BusinessRuleException(
                    "받을 이메일 주소가 없습니다. 내 정보에서 주소를 먼저 넣어 주세요.");
        }

        NotificationMessage message = new NotificationMessage(
                "[모든시험한번에보기] 알림이 잘 도착하는지 확인하는 메일입니다",
                "이 메일이 보였다면 접수 시작·마감 알림도 같은 주소로 갑니다.\n"
                        + "안 왔다면 스팸함을 확인하시고, 그래도 없으면 내 정보에서 주소를 고쳐 주세요.",
                Map.of("type", "TEST"));

        NotificationResult result = sender.send(member, message);
        NotificationChannel channel = sender.channel();

        // LOG 는 서버 로그에 적기만 한다 — 사람에게 간 것이 아니다.
        boolean delivered = result == NotificationResult.SUCCESS
                && channel != NotificationChannel.LOG;

        log.info("[확인메일] member={} channel={} 도달={}", member.getId(), channel, delivered);
        return new Result(channel.name(), delivered, describe(channel, result, member.getEmail()));
    }

    private String describe(NotificationChannel channel, NotificationResult result, String email) {
        if (channel == NotificationChannel.LOG) {
            return "지금은 실제로 보낼 수단이 켜져 있지 않습니다(서버 기록만 남았습니다). "
                    + "운영자에게 알려 주세요.";
        }
        if (result != NotificationResult.SUCCESS) {
            return "보내지 못했습니다. 주소를 확인하고 잠시 뒤 다시 눌러 주세요.";
        }
        return email + " 로 한 통 보냈습니다. 몇 분 안에 안 오면 스팸함을 확인해 주세요.";
    }

    /** 무엇이 일어났는지 그대로 — 화면이 이 값으로 말을 고른다. */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result {
        /** 실제로 쓰인 채널. LOG 면 사람에게 안 갔다. */
        private String channel;
        /** 사람에게 닿았는가. LOG 는 false. */
        private boolean delivered;
        private String message;
    }
}
