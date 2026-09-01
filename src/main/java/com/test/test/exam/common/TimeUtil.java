package com.test.test.exam.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 모든 시각 판단의 단일 진입점 (NFR-01).
 * 서버 타임존 설정과 무관하게 항상 Asia/Seoul(KST) 기준으로 "지금"을 계산한다.
 * Dockerfile 의 {@code ENV TZ=Asia/Seoul} 와 이중 방어.
 *
 * API 응답 포맷도 여기서 통일한다 (설계 05: 날짜 {@code yyyy-MM-dd},
 * 시각 {@code yyyy-MM-dd'T'HH:mm}). 문자열로 직렬화해 Jackson 전역 설정에
 * 의존하지 않고 포맷을 확정한다.
 */
public final class TimeUtil {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private TimeUtil() {
    }

    /** 현재 KST 시각 */
    public static LocalDateTime now() {
        return LocalDateTime.now(KST);
    }

    /** 오늘 KST 날짜 */
    public static LocalDate today() {
        return LocalDate.now(KST);
    }

    /** LocalDateTime → "yyyy-MM-dd'T'HH:mm" (null 안전) */
    public static String format(LocalDateTime dt) {
        return dt == null ? null : dt.format(DATETIME_FMT);
    }

    /** LocalDate → "yyyy-MM-dd" (null 안전) */
    public static String format(LocalDate d) {
        return d == null ? null : d.format(DATE_FMT);
    }
}
