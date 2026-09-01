package com.test.test.exam.domain;

/**
 * 알림 발송 채널.
 *
 * <p>한국에서 알림은 결국 카톡이라 카톡 계열이 우선이고, 못 보내는 사용자에게 EMAIL 로 폴백한다
 * (NotificationSenderChain).
 *
 * <ul>
 *   <li>{@code ALIMTALK} — 운영용. 남에게 보낼 수 있지만 <b>사업자등록이 필요</b>하다.</li>
 *   <li>{@code KAKAO_MEMO} — 개발·테스트용 "나에게 보내기". 사업자 없이 되지만
 *       <b>앱 멤버(본인·팀원)에게만</b> 간다.</li>
 *   <li>{@code EMAIL} — 카톡을 못 받는 사용자용 폴백.</li>
 *   <li>{@code LOG} — 키가 하나도 없을 때의 스텁.</li>
 * </ul>
 */
public enum NotificationChannel {
    ALIMTALK,
    KAKAO_MEMO,
    EMAIL,
    LOG
}
