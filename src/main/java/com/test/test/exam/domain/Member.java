package com.test.test.exam.domain;

import com.test.test.exam.common.TimeUtil;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 계정 (설계 08 §2-2). 소셜 로그인 단독 — 자체 이메일/비밀번호 계정은 만들지 않는다.
 *
 * <p><b>식별 키는 이메일이 아니라 {@code (provider, providerId)}</b> 다.
 * 카카오는 이메일 제공이 선택 동의라 없을 수 있고, 같은 사람이 카카오/구글로 각각 가입하면 별개 계정이 된다
 * (계정 통합은 범위 밖).
 *
 * <p>구 {@code AppUser}(기기식별)를 대체한다 — {@code deviceId} 는 없다.
 */
@Entity
@Table(name = "member",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_member_provider", columnNames = {"provider", "provider_id"}),
                @UniqueConstraint(name = "uk_member_nickname", columnNames = "nickname")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AuthProvider provider;

    @Column(name = "provider_id", nullable = false, length = 64)
    private String providerId;

    /**
     * 매니저(LOCAL) 계정의 BCrypt 비밀번호 해시. <b>소셜 계정은 항상 null</b> 이다 —
     * 소셜은 비밀번호라는 개념이 없고, 여기에 값이 생기면 소셜 계정도 폼 로그인이 되어 버린다.
     */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    /** 카카오는 미동의 시 없음 → null 허용. 이메일 알림은 이 값이 있어야 보낼 수 있다. */
    @Column(length = 255)
    private String email;

    /** 커뮤니티 표시명. 최초엔 공급자 값, 이후 사용자가 바꿀 수 있다. */
    @Column(nullable = false, length = 30)
    private String nickname;

    @Column(name = "profile_image", length = 500)
    private String profileImage;

    /**
     * 휴대폰번호(숫자만, 예: 01012345678).
     * <b>알림톡은 이메일이 아니라 번호로 발송</b>되므로 이게 없으면 카톡 알림을 못 보낸다.
     * 카카오 로그인의 전화번호 동의는 비즈앱이 필요해, 사용자가 직접 입력하는 경로도 열어 둔다.
     */
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    /**
     * 카카오 액세스 토큰 — "나에게 보내기"(KakaoMemoSender)에 쓴다.
     * 유효기간이 6시간이라 만료되면 refreshToken 으로 갱신한다.
     * <p>운영에서 알림톡을 켜면 이 경로는 안 쓰이지만, 사업자등록 전 테스트에는 이것뿐이다.
     */
    @Column(name = "kakao_access_token", length = 512)
    private String kakaoAccessToken;

    /** 카카오 리프레시 토큰(약 2개월). 이게 만료되면 사용자가 다시 로그인해야 한다. */
    @Column(name = "kakao_refresh_token", length = 512)
    private String kakaoRefreshToken;

    @Column(name = "kakao_token_expires_at")
    private LocalDateTime kakaoTokenExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private MemberRole role = MemberRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private MemberStatus status = MemberStatus.ACTIVE;

    @Column(name = "notify_reg", nullable = false)
    @Builder.Default
    private boolean notifyReg = true;

    @Column(name = "notify_exam", nullable = false)
    @Builder.Default
    private boolean notifyExam = true;

    @Column(name = "notify_change", nullable = false)
    @Builder.Default
    private boolean notifyChange = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = TimeUtil.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.role == null) {
            this.role = MemberRole.USER;
        }
        if (this.status == null) {
            this.status = MemberStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = TimeUtil.now();
    }

    // ===== 비즈니스 메서드 =====

    public void updateProfile(String email, String nickname, String profileImage) {
        if (email != null) {
            this.email = email;
        }
        if (nickname != null && !nickname.isBlank()) {
            this.nickname = nickname;
        }
        if (profileImage != null) {
            this.profileImage = profileImage;
        }
    }

    /** 알림 받을 이메일. 빈 값이면 해제한다. */
    public void changeEmail(String email) {
        this.email = (email == null || email.isBlank()) ? null : email.trim();
    }

    /** 번호는 항상 숫자만 저장한다 — 하이픈 유무로 같은 번호가 둘이 되면 안 된다. */
    public void changePhoneNumber(String phoneNumber) {
        this.phoneNumber = (phoneNumber == null || phoneNumber.isBlank())
                ? null : phoneNumber.replaceAll("[^0-9]", "");
    }

    /** 소셜 로그인 때 받은 카카오 토큰을 보관한다. */
    public void updateKakaoToken(String accessToken, String refreshToken, LocalDateTime expiresAt) {
        this.kakaoAccessToken = accessToken;
        if (refreshToken != null && !refreshToken.isBlank()) {
            this.kakaoRefreshToken = refreshToken;   // 갱신 응답에는 안 올 수 있어 기존 값을 지킨다
        }
        this.kakaoTokenExpiresAt = expiresAt;
    }

    /** "나에게 보내기"를 쓸 수 있는 상태인지 — 카카오 로그인 + 토큰 보유. */
    public boolean canReceiveKakaoMemo() {
        return isActive() && provider == AuthProvider.KAKAO
                && kakaoAccessToken != null && !kakaoAccessToken.isBlank();
    }

    public boolean isKakaoTokenExpired() {
        return kakaoTokenExpiresAt == null || kakaoTokenExpiresAt.isBefore(TimeUtil.now());
    }

    /** 알림톡을 보낼 수 있는 상태인지 */
    public boolean canReceiveAlimtalk() {
        return isActive() && phoneNumber != null && !phoneNumber.isBlank();
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updateNotifySettings(boolean notifyReg, boolean notifyExam, boolean notifyChange) {
        this.notifyReg = notifyReg;
        this.notifyExam = notifyExam;
        this.notifyChange = notifyChange;
    }

    /** 탈퇴 — 글·댓글은 남기고 작성자 표기만 "탈퇴한 사용자"가 된다(설계 08 §4-1). */
    public void withdraw() {
        this.status = MemberStatus.WITHDRAWN;
        this.email = null;
        this.profileImage = null;
        this.phoneNumber = null;
        this.kakaoAccessToken = null;
        this.kakaoRefreshToken = null;
    }

    public boolean isActive() {
        return this.status == MemberStatus.ACTIVE;
    }

    /**
     * 매니저 계정을 만든다 — 가입 경로가 없고 환경변수로만 생성된다.
     * 비밀번호는 <b>이미 해시된 값</b>을 받는다(엔티티가 인코더를 알 필요는 없다).
     */
    public static Member manager(String username, String passwordHash, String nickname) {
        return Member.builder()
                .provider(AuthProvider.LOCAL)
                .providerId(username)
                .passwordHash(passwordHash)
                .nickname(nickname)
                .role(MemberRole.ADMIN)
                .build();
    }

    /** 비밀번호를 바꾼다(해시된 값). 환경변수를 고치고 재기동하면 여기로 반영된다. */
    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * 폼 로그인을 시도할 수 있는 계정인지.
     * 소셜 계정은 해시가 없어 항상 false — 소셜 사용자가 매니저 로그인 창을 통과할 수 없다.
     */
    public boolean canLoginWithPassword() {
        return this.provider == AuthProvider.LOCAL
                && this.passwordHash != null
                && isActive();
    }

    public boolean isAdmin() {

        return this.role == MemberRole.ADMIN;
    }

    /** 이벤트 유형별 수신 토글이 켜져 있는지 */
    public boolean acceptsEvent(NotificationEventType.ToggleTarget target) {
        return switch (target) {
            case REG -> notifyReg;
            case EXAM -> notifyExam;
            case CHANGE -> notifyChange;
        };
    }

    /** 이메일 알림을 실제로 보낼 수 있는 상태인지 */
    public boolean canReceiveEmail() {
        return isActive() && email != null && !email.isBlank();
    }
}
