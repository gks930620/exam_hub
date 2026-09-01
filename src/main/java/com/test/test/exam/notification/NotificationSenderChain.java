package com.test.test.exam.notification;

import com.test.test.exam.domain.Member;
import com.test.test.exam.domain.NotificationChannel;
import com.test.test.exam.domain.NotificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 발송 채널 체인 — <b>알림톡 → 이메일 → 로그</b> 순으로 시도한다.
 *
 * <p><b>왜 체인인가</b>: 알림은 카톡으로 가는 게 맞지만, 카톡만 두면 못 받는 사람이 생긴다.
 * <ul>
 *   <li>휴대폰번호를 아직 안 넣은 사용자</li>
 *   <li>카카오톡을 안 쓰거나 채널을 차단한 사용자</li>
 *   <li>알림톡 잔액 소진·대행사 장애</li>
 * </ul>
 * 이때 <b>아무것도 안 보내는 것보다 이메일이라도 보내는 게 낫다.</b>
 * "접수 마감을 놓치지 않게 한다"가 이 서비스의 약속이라, 채널 하나가 죽었다고 약속을 깨면 안 된다.
 *
 * <p>실제로 어느 채널로 나갔는지는 {@link #lastUsedChannel()} 로 알 수 있고,
 * 발송 로그에 그 값이 남는다.
 */
@Slf4j
@Component
@Primary
public class NotificationSenderChain implements NotificationSender {

    /** 앞에 있을수록 먼저 시도. 알림톡이 1순위다. */
    private static final List<NotificationChannel> PRIORITY =
            List.of(NotificationChannel.ALIMTALK,      // 운영 — 사업자등록 필요
                    NotificationChannel.KAKAO_MEMO,    // 개발·테스트 — 앱 멤버 본인에게만
                    NotificationChannel.EMAIL,
                    NotificationChannel.LOG);

    private final List<NotificationSender> senders;
    /** 직전 발송에 실제로 쓰인 채널 — 로그에 남길 값. 배치가 단일 스레드라 ThreadLocal 로 충분하다. */
    private final ThreadLocal<NotificationChannel> lastUsed =
            ThreadLocal.withInitial(() -> NotificationChannel.LOG);

    public NotificationSenderChain(List<NotificationSender> allSenders) {
        this.senders = allSenders.stream()
                .filter(s -> !(s instanceof NotificationSenderChain))   // 자기 자신 제외(무한 재귀 방지)
                .sorted(Comparator.comparingInt(s -> {
                    int idx = PRIORITY.indexOf(s.channel());
                    return idx < 0 ? Integer.MAX_VALUE : idx;
                }))
                .toList();

        log.info("[알림] 발송 채널 순서: {}",
                this.senders.stream().map(s -> s.channel().name()).toList());
    }

    @Override
    public NotificationChannel channel() {
        return lastUsed.get();
    }

    /** 직전 발송에 실제로 쓰인 채널. */
    public NotificationChannel lastUsedChannel() {
        return lastUsed.get();
    }

    @Override
    public NotificationResult send(Member member, NotificationMessage message) {
        NotificationResult lastResult = NotificationResult.FAILED;

        for (NotificationSender sender : senders) {
            NotificationResult result = sender.send(member, message);
            lastUsed.set(sender.channel());

            if (result == NotificationResult.SUCCESS) {
                return result;
            }
            lastResult = result;
            log.debug("[알림] {} 실패({}) — 다음 채널 시도", sender.channel(), result);
        }

        log.warn("[알림] 모든 채널 실패 member={}", member.getId());
        return lastResult;
    }
}
