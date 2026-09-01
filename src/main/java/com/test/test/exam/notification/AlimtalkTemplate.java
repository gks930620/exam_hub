package com.test.test.exam.notification;

import com.test.test.exam.domain.NotificationEventType;

/**
 * 알림톡 템플릿 매핑.
 *
 * <p><b>알림톡은 아무 문구나 못 보낸다.</b> 카카오에 <b>미리 승인받은 템플릿</b>만 발송할 수 있고,
 * 본문에서 바꿀 수 있는 건 {@code #{변수}} 자리뿐이다. 그래서 이벤트 유형마다 템플릿을 하나씩 만들어
 * 승인받고, 그 코드를 여기에 적어 둔다.
 *
 * <p>승인 신청에 쓸 <b>완성된 문구</b>는 {@code 운영/01_카카오_설정.md} 에 있다.
 * 문구를 바꾸려면 재심사(영업일 1~2일)라, 승인 후에는 여기 상수와 실제 템플릿이 어긋나지 않게 주의한다.
 *
 * <p>템플릿 코드는 설정으로 뺀다({@code notification.alimtalk.template.*}) —
 * 승인 전에는 코드를 모르고, 대행사/채널을 바꾸면 코드도 바뀌기 때문이다.
 */
public enum AlimtalkTemplate {

    /** 원서접수 시작 (전날·당일) */
    REG_OPEN("EXAM_REG_OPEN", "notification.alimtalk.template.reg-open"),

    /** 원서접수 마감 임박 — 이게 이 서비스의 핵심 알림이다 */
    REG_CLOSING("EXAM_REG_CLOSING", "notification.alimtalk.template.reg-closing"),

    /** 시험일 임박 (D-7 / D-1) */
    EXAM_SOON("EXAM_D_DAY", "notification.alimtalk.template.exam-soon"),

    /** 일정 변경·연기·취소 */
    SCHEDULE_CHANGED("EXAM_CHANGED", "notification.alimtalk.template.changed");

    /** 승인 신청 시 쓸 기본 코드(참고용). 실제 코드는 설정값이 우선한다. */
    private final String defaultCode;
    private final String propertyKey;

    AlimtalkTemplate(String defaultCode, String propertyKey) {
        this.defaultCode = defaultCode;
        this.propertyKey = propertyKey;
    }

    public String getDefaultCode() {
        return defaultCode;
    }

    public String getPropertyKey() {
        return propertyKey;
    }

    /** 알림 이벤트 유형 → 어떤 템플릿으로 보낼지. */
    public static AlimtalkTemplate from(NotificationEventType type) {
        return switch (type.getToggleTarget()) {
            case REG -> type.name().contains("CLOSE") || type.name().contains("END")
                    ? REG_CLOSING : REG_OPEN;
            case EXAM -> EXAM_SOON;
            case CHANGE -> SCHEDULE_CHANGED;
        };
    }
}
