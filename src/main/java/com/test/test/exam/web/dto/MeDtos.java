package com.test.test.exam.web.dto;

import com.test.test.exam.domain.Member;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 로그인 사용자(내 정보/설정/캘린더) 관련 DTO (설계 08 §4-1).
 */
public final class MeDtos {

    private MeDtos() {
    }

    /** GET /api/me — 내 정보. 공급자 ID·토큰 같은 건 내보내지 않는다. */
    public record MemberResponse(
            Long id,
            String nickname,
            String email,
            String profileImage,
            String phoneNumber,
            String provider,
            String role
    ) {
        public static MemberResponse of(Member m) {
            return new MemberResponse(
                    m.getId(), m.getNickname(), m.getEmail(), m.getProfileImage(), m.getPhoneNumber(),
                    m.getProvider().name(), m.getRole().name());
        }
    }

    /** PATCH /api/me */
    public record UpdateProfileRequest(
            @NotBlank(message = "닉네임은 필수입니다.")
            @Size(max = 30, message = "닉네임은 30자 이하여야 합니다.")
            String nickname
    ) {
    }

    /**
     * PUT /api/me/email — 알림 받을 이메일.
     * <p><b>왜 직접 받나</b>: 카카오는 이메일 동의항목이 비즈 앱 전환 후에만 열려서
     * 일반 앱에서는 주소를 받을 수 없다(요청하면 KOE205 로 로그인 자체가 막힌다).
     * 알림 채널이 이메일뿐인 동안에는 이 입력이 유일한 수신 경로다.
     */
    public record UpdateEmailRequest(
            @Email(message = "이메일 형식이 올바르지 않습니다.")
            @Size(max = 255, message = "이메일이 너무 깁니다.")
            String email
    ) {
    }

    /**
     * PUT /api/me/phone — 알림톡 수신 번호.
     * 알림톡은 이메일이 아니라 <b>번호</b>로 발송되므로 이게 있어야 카톡 알림을 받는다.
     */
    public record UpdatePhoneRequest(
            @Pattern(regexp = "^01[016789][0-9]{7,8}$|^$",
                     message = "휴대폰번호 형식이 올바르지 않습니다. 예: 01012345678")
            String phoneNumber
    ) {
    }

    /** GET·PUT /api/me/notify-settings 요청/응답 (동일 형태) */
    public record NotifySettings(
            boolean notifyReg,
            boolean notifyExam,
            boolean notifyChange
    ) {
    }

    /** GET /api/me/calendar 응답 */
    public record CalendarResponse(List<CalendarEvent> events) {
    }

    public record CalendarEvent(
            String date,
            String type,
            Long certificateId,
            String name,
            String label
    ) {
    }
}
