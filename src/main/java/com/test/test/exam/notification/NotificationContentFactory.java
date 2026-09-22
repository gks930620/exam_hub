package com.test.test.exam.notification;

import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.NotificationEventType;
import com.test.test.exam.domain.ScheduleStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 이벤트 유형별 알림 문안 생성 (설계 05 페이로드 예시 준용).
 */
@Component
public class NotificationContentFactory {

    private static final DateTimeFormatter MD_DOW =
            DateTimeFormatter.ofPattern("MM.dd(E)", Locale.KOREAN);
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    public NotificationMessage build(NotificationSchedule_Ref ref) {
        ExamSchedule s = ref.getSchedule();
        Certificate c = s.getCertificate();
        NotificationEventType type = ref.getEventType();
        String cert = c.getName();
        // 화면과 같은 함수를 쓴다. 직접 붙이면 회차 자리에 넣은 내부 멱등 키(날짜·0)가
        // 그대로 메일에 나간다 — 화면은 2026-09-08 에 감췄는데 여기만 남아 있었다.
        // 받는 사람은 "20261011회"를 보고 우리가 잘못 읽었다고 생각하고, 같이 적힌 날짜도 의심한다.
        // 구분이 비어 있어도 roundLabel() 은 죽지 않는다(직접 getLabel() 하면 NPE 로 그 건이 막힌다).
        String roundType = s.roundLabel();
        // 회차도 구분도 없는 시험(토플 등)은 roundType 이 빈 문자열이다 — 앞 공백을 남기지 않는다.
        String prefix = roundType.isBlank() ? "" : roundType + " ";

        String title;
        String body;
        switch (type) {
            case REG_OPEN_EVE -> {
                title = cert + " 접수가 내일 시작돼요";
                body = regBody(s);
            }
            case REG_OPEN_DAY -> {
                title = cert + " 접수가 오늘 시작돼요";
                body = regBody(s);
            }
            case REG_CLOSE_EVE -> {
                title = cert + " 접수 마감 하루 전이에요";
                body = prefix + "접수 마감 " + dt(s.getRegEndAt());
            }
            case EXAM_D7 -> {
                title = cert + " 시험 D-7";
                body = prefix + "시험 " + d(s.getExamStartDate());
            }
            case EXAM_D1 -> {
                title = cert + " 시험이 내일이에요";
                body = prefix + "시험 " + d(s.getExamStartDate());
            }
            case SCHEDULE_CHANGED -> {
                // 같은 이벤트라도 회차가 취소됐으면 "변경"이 아니라 "취소"라고 말해야 한다 —
                // 접수해 둔 사람이 "업데이트됐다"는 문구를 보고 그대로 시험장에 가면 안 된다.
                if (s.getStatus() == ScheduleStatus.CANCELED) {
                    title = cert + " 일정이 취소·연기됐어요";
                    body = prefix + "일정이 취소 또는 연기됐어요. 시행처 공지를 확인하세요.";
                } else {
                    title = cert + " 일정이 변경됐어요";
                    body = prefix + "일정이 업데이트됐어요. 사이트에서 확인하세요.";
                }
            }
            default -> {
                title = cert + " 알림";
                body = roundType;
            }
        }

        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", type.name());
        data.put("certificateId", String.valueOf(c.getId()));
        data.put("examScheduleId", String.valueOf(s.getId()));
        data.put("route", "/cert/" + c.getId());   // React 상세 라우트

        return new NotificationMessage(title, body, data);
    }

    private String regBody(ExamSchedule s) {
        String start = s.getRegStartAt() == null ? "-" : dt(s.getRegStartAt());
        String end = s.getRegEndAt() == null ? "-" : dt(s.getRegEndAt());
        return "접수 " + start + " · 마감 " + end;
    }

    private String dt(LocalDateTime v) {
        return v == null ? "-" : v.format(MD_DOW) + " " + v.format(HM);
    }

    private String d(LocalDate v) {
        return v == null ? "-" : v.format(MD_DOW);
    }

    /** 발송 배치가 넘겨주는 (예약 → 일정/이벤트) 참조. */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationSchedule_Ref {
        private ExamSchedule schedule;
        private NotificationEventType eventType;
    }
}
