package com.test.test.exam.notification;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.Member;
import com.test.test.exam.domain.NotificationEventType;
import com.test.test.exam.domain.NotificationLog;
import com.test.test.exam.domain.NotificationResult;
import com.test.test.exam.domain.NotificationSchedule;
import com.test.test.exam.domain.NotificationScheduleStatus;
import com.test.test.exam.domain.ScheduleStatus;
import com.test.test.exam.repository.ExamScheduleRepository;
import com.test.test.exam.repository.NotificationLogRepository;
import com.test.test.exam.repository.NotificationScheduleRepository;
import com.test.test.exam.repository.UserFavoriteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 발송 예약 <b>한 건</b>의 처리 — 항상 새 트랜잭션({@code REQUIRES_NEW}).
 *
 * <p>{@link NotificationDispatchService} 와 빈을 나눈 이유: 같은 빈 안에서 {@code this.dispatchOne()} 을
 * 부르면 프록시를 거치지 않아 트랜잭션 속성이 먹지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatchWorker {

    private final ExamScheduleRepository examScheduleRepository;
    private final NotificationScheduleRepository notificationScheduleRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final UserFavoriteRepository userFavoriteRepository;
    private final NotificationContentFactory contentFactory;
    private final NotificationSender sender; // 체인(@Primary): 알림톡 → 이메일 → 로그

    /**
     * 잠깐 실패한 알림을 다시 시도하는 기간(시간). 이보다 밀리면 포기한다.
     *
     * <p>배치가 5분마다 도니 이 창 안에서 수십 번 다시 걸어 본다. SMTP 가 몇 분 끊기는 건
     * 흔한 일이고, 그 몇 분 때문에 "접수 마감 하루 전" 메일이 통째로 사라지면 안 된다.
     *
     * <p>무한히 두지 않는 이유: 이미 늦은 알림은 보내 봐야 소용이 없고, 밀린 예약이 계속 쌓이면
     * 매니저 화면의 \"밀림\" 숫자가 커진 채로 굳어 <b>진짜 고장을 덮는다.</b>
     */
    static final long RETRY_WINDOW_HOURS = 6;

    /** 반환: 실제 발송 건수. 예약이 그새 사라졌거나 PENDING 이 아니면 0. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int dispatchOne(Long notificationScheduleId) {
        NotificationSchedule ns = notificationScheduleRepository.findById(notificationScheduleId).orElse(null);
        if (ns == null || ns.getStatus() != NotificationScheduleStatus.PENDING) {
            return 0;   // 조회와 처리 사이에 다른 배치가 먼저 처리했다
        }
        ExamSchedule schedule = ns.getExamSchedule();

        // 안전장치: 일정이 더 이상 ACTIVE 가 아니면 시각 알림은 떨어뜨린다.
        // 단 취소된 회차의 "취소·연기 안내"(SCHEDULE_CHANGED)는 바로 그 상황을 알리는 것이라 나가야 한다.
        boolean cancelNotice = schedule.getStatus() == ScheduleStatus.CANCELED
                && ns.getEventType() == NotificationEventType.SCHEDULE_CHANGED;
        if (!schedule.isActive() && !cancelNotice) {
            ns.cancel();
            return 0;
        }

        NotificationEventType.ToggleTarget target = ns.getEventType().getToggleTarget();

        // 문구에 "필기/실기"를 붙일지는 그 시험이 두 종류를 실제로 치를 때만이다 —
        // 토익스피킹에 "필기 접수 마감"이라고 쓰면 틀린 말이다. 여기서는 회차 하나만 보이므로
        // 형제 회차를 한 번 읽어 정한다(배치 상한이 500건이라 감당할 비용이다).
        boolean withExamType = ExamSchedule.splitsByExamTypes(
                examScheduleRepository.findDistinctExamTypes(schedule.getCertificate().getId()));

        NotificationMessage message = contentFactory.build(
                new NotificationContentFactory.NotificationSchedule_Ref(
                        schedule, ns.getEventType(), withExamType));

        List<Member> favoritedUsers = userFavoriteRepository
                .findUsersByCertificateId(schedule.getCertificate().getId());

        int sent = 0;
        int retryable = 0;
        for (Member user : favoritedUsers) {
            if (!user.acceptsEvent(target)) {
                continue; // 유형별 토글 off
            }
            // 멱등: 이미 발송 로그가 있으면 스킵 (FR-27). 다시 시도할 때도 이것이 중복을 막는다 —
            // 못 받은 사람에게만 다시 간다.
            if (notificationLogRepository.existsByMemberIdAndNotificationScheduleId(user.getId(), ns.getId())) {
                continue;
            }
            NotificationResult result = deliver(user, ns, message);
            if (result == NotificationResult.SUCCESS) {
                sent++;
            } else if (result == NotificationResult.FAILED) {
                retryable++;   // 잠깐 못 보낸 것 — 기록을 안 남겼으니 다음 배치가 다시 건다
            }
        }

        // 한 명이라도 잠깐 실패했고 아직 늦지 않았으면 예약을 열어 둔다.
        // 여기서 닫아 버리면 그 사람은 이 알림을 영영 못 받는다 — SMTP 가 몇 분 끊긴 것뿐인데도.
        boolean tooLate = ns.getSendAt() == null
                || ns.getSendAt().plusHours(RETRY_WINDOW_HOURS).isBefore(TimeUtil.now());
        if (retryable > 0 && !tooLate) {
            log.warn("[Dispatch] 예약 {} — {}명에게 못 보냄, 다음 배치에서 다시 시도", ns.getId(), retryable);
            return sent;   // PENDING 유지
        }
        if (retryable > 0) {
            log.error("[Dispatch] 예약 {} — {}명에게 끝내 못 보냈습니다({}시간 경과). 포기합니다.",
                    ns.getId(), retryable, RETRY_WINDOW_HOURS);
        }
        ns.markSent();
        return sent;
    }

    /**
     * 한 사람에게 보내고 결과를 돌려준다.
     *
     * <p><b>잠깐 실패({@code FAILED})면 기록을 남기지 않는다.</b> 멱등키가 (회원, 예약)이라
     * 실패 기록을 남기면 그것이 다음 배치의 재시도까지 막는다 — 실제로 그래서 재시도가
     * 하나도 없었다(2026-09-22). 실패한 사실은 로그와 \"밀림\" 숫자로 드러난다.
     */
    private NotificationResult deliver(Member user, NotificationSchedule ns, NotificationMessage message) {
        NotificationResult result = sender.send(user, message);

        if (result == NotificationResult.FAILED) {
            log.warn("[Dispatch] 발송 실패 user={} ns={} — 다시 시도한다", user.getId(), ns.getId());
            return result;
        }

        try {
            notificationLogRepository.save(NotificationLog.builder()
                    .member(user)
                    .notificationSchedule(ns)
                    .channel(sender.channel())   // 체인이면 실제로 나간 채널이 담긴다
                    .sentAt(TimeUtil.now())
                    .result(result)
                    .errorMessage(result == NotificationResult.SUCCESS ? null : result.name())
                    .build());
        } catch (DataIntegrityViolationException dup) {
            // 동시 배치 실행 경합 — UNIQUE 제약이 중복 발송을 최종 차단 (NFR-03)
            log.debug("[Dispatch] 멱등 충돌 무시 user={} ns={}", user.getId(), ns.getId());
            return NotificationResult.SUBSCRIPTION_EXPIRED;   // 이미 처리됨 — 다시 시도하지 않는다
        }

        // 구독 만료(웹 푸시 410 등) → 로그로 남긴다. 만료 구독 삭제는 채널 확정 후 구독 모델과 함께 붙인다.
        if (result == NotificationResult.SUBSCRIPTION_EXPIRED) {
            // 받을 주소가 없는 것이라 다시 걸어도 결과가 같다 — 기록만 남기고 재시도하지 않는다.
            log.warn("[Dispatch] 받을 주소 없음 user={} — 다시 시도하지 않는다", user.getId());
        }
        return result;
    }
}
