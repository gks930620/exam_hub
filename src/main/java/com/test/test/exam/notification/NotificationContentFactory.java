package com.test.test.exam.notification;

import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.NotificationEventType;
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
        ExamSchedule s = ref.schedule();
        Certificate c = s.getCertificate();
        NotificationEventType type = ref.eventType();
        String cert = c.getName();
        String roundType = s.getRound() + "회 " + s.getExamType().getLabel();

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
                body = roundType + " 접수 마감 " + dt(s.getRegEndAt());
            }
            case EXAM_D7 -> {
                title = cert + " 시험 D-7";
                body = roundType + " 시험 " + d(s.getExamStartDate());
            }
            case EXAM_D1 -> {
                title = cert + " 시험이 내일이에요";
                body = roundType + " 시험 " + d(s.getExamStartDate());
            }
            case SCHEDULE_CHANGED -> {
                title = cert + " 일정이 변경됐어요";
                body = roundType + " 일정이 업데이트됐어요. 앱에서 확인하세요.";
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
        data.put("route", "/certificate/" + c.getId());

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
    public record NotificationSchedule_Ref(ExamSchedule schedule, NotificationEventType eventType) {
    }
}
