package com.test.test.exam.web.dto;

import com.test.test.exam.domain.Member;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 로그인 사용자(내 정보/설정/캘린더) 관련 DTO (설계 08 §4-1).
 *
 * <p><b>DTO 는 record 가 아니라 class 다</b>(코드 컨벤션 §0). 요청 DTO 에는 {@code @Setter} 도 붙인다 —
 * Jackson 이 JSON 을 채워 넣을 손잡이가 없으면 필드가 전부 null 로 들어오고, 그건 컴파일에 안 걸린다.
 */
public final class MeDtos {

    private MeDtos() {
    }

    /** GET /api/me — 내 정보. 공급자 ID·토큰 같은 건 내보내지 않는다. */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberResponse {
        private Long id;
        private String nickname;
        private String email;
        private String profileImage;
        private String phoneNumber;
        private String provider;
        private String role;

        public static MemberResponse of(Member m) {
            return new MemberResponse(
                    m.getId(), m.getNickname(), m.getEmail(), m.getProfileImage(), m.getPhoneNumber(),
                    m.getProvider().name(), m.getRole().name());
        }
    }

    /** PATCH /api/me */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateProfileRequest {
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = 30, message = "닉네임은 30자 이하여야 합니다.")
        private String nickname;
    }

    /**
     * PUT /api/me/email — 알림 받을 이메일.
     * <p><b>왜 직접 받나</b>: 카카오는 이메일 동의항목이 비즈 앱 전환 후에만 열려서
     * 일반 앱에서는 주소를 받을 수 없다(요청하면 KOE205 로 로그인 자체가 막힌다).
     * 알림 채널이 이메일뿐인 동안에는 이 입력이 유일한 수신 경로다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateEmailRequest {
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "이메일이 너무 깁니다.")
        private String email;
    }

    /**
     * PUT /api/me/phone — 알림톡 수신 번호.
     * 알림톡은 이메일이 아니라 <b>번호</b>로 발송되므로 이게 있어야 카톡 알림을 받는다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdatePhoneRequest {
        @Pattern(regexp = "^01[016789][0-9]{7,8}$|^$",
                 message = "휴대폰번호 형식이 올바르지 않습니다. 예: 01012345678")
        private String phoneNumber;
    }

    /** GET·PUT /api/me/notify-settings 응답 */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotifySettings {
        private boolean notifyReg;
        private boolean notifyExam;
        private boolean notifyChange;
    }

    /**
     * PUT /api/me/notify-settings 요청.
     *
     * <p><b>세 값을 모두 보내야 한다.</b> {@code Boolean} + {@code @NotNull} 인 이유가 이것이다 —
     * {@code boolean} 이면 JSON 에 없는 필드를 Jackson 이 조용히 {@code false} 로 채우고,
     * 서버는 그걸 사용자의 뜻으로 알고 저장한다. 실제로 {@code {}} 를 보내면 200 과 함께
     * <b>알림 셋이 전부 꺼졌다</b>(2026-09-10 실측). 이 서비스는 "접수 기간을 놓치면 반년 대기"를
     * 막으려고 있는데, 그 알림이 화면상 성공인 채로 꺼지는 게 가장 나쁜 실패다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateNotifySettingsRequest {
        @NotNull(message = "원서접수 알림 여부를 보내세요.")
        private Boolean notifyReg;
        @NotNull(message = "시험일 알림 여부를 보내세요.")
        private Boolean notifyExam;
        @NotNull(message = "일정 변경 알림 여부를 보내세요.")
        private Boolean notifyChange;
    }

    /** GET /api/me/calendar 응답 */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CalendarResponse {
        private List<CalendarEvent> events;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CalendarEvent {
        private String date;
        private String type;
        private Long certificateId;
        private String name;
        private String label;
    }
}
