package com.test.test.exam.auth;

import com.test.test.common.exception.BusinessRuleException;
import com.test.test.common.exception.DuplicateResourceException;
import com.test.test.common.exception.EntityNotFoundException;
import com.test.test.exam.domain.AuthProvider;
import com.test.test.exam.domain.Member;
import com.test.test.exam.domain.UserFavorite;
import com.test.test.exam.repository.MemberRepository;
import com.test.test.exam.repository.UserFavoriteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 계정 관리 — 소셜 로그인 upsert, 프로필 변경, 탈퇴.
 * <p>최초 로그인이 곧 회원가입이다(별도 가입 절차 없음).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private static final int NICKNAME_MAX = 30;

    private final MemberRepository memberRepository;
    private final UserFavoriteRepository userFavoriteRepository;

    /** 로그인한 사용자 조회. 탈퇴 계정은 없는 것으로 취급한다. */
    @Transactional(readOnly = true)
    public Optional<Member> findActive(Long memberId) {
        return memberRepository.findById(memberId).filter(Member::isActive);
    }

    @Transactional(readOnly = true)
    public Member getOrThrow(Long memberId) {
        return findActive(memberId)
                .orElseThrow(() -> new EntityNotFoundException("계정을 찾을 수 없습니다."));
    }

    /**
     * 소셜 로그인 성공 시 호출 — 있으면 프로필 갱신, 없으면 생성.
     * 식별 키는 이메일이 아니라 {@code (provider, providerId)} 다(설계 08 §2-2).
     */
    @Transactional
    public Member upsertFromOAuth(AuthProvider provider, String providerId,
                                  String email, String nickname, String profileImage) {
        return memberRepository.findByProviderAndProviderId(provider, providerId)
                .map(existing -> {
                    existing.updateProfile(email, null, profileImage); // 닉네임은 사용자가 바꿨을 수 있어 덮지 않는다
                    return existing;
                })
                .orElseGet(() -> {
                    Member created = memberRepository.save(Member.builder()
                            .provider(provider)
                            .providerId(providerId)
                            .email(email)
                            .nickname(uniqueNickname(nickname))
                            .profileImage(profileImage)
                            .build());
                    log.info("[Member] 신규 가입 provider={} id={}", provider, created.getId());
                    return created;
                });
    }

    /** 닉네임은 unique 라 겹치면 뒤에 숫자를 붙인다. */
    private String uniqueNickname(String raw) {
        String base = (raw == null || raw.isBlank()) ? "회원" : raw.trim();
        if (base.length() > NICKNAME_MAX - 3) {
            base = base.substring(0, NICKNAME_MAX - 3);
        }
        String candidate = base;
        int n = 2;
        while (memberRepository.existsByNickname(candidate)) {
            candidate = base + n++;
        }
        return candidate;
    }

    @Transactional
    public Member changeNickname(Member member, String nickname) {
        String trimmed = nickname == null ? "" : nickname.trim();
        if (trimmed.isEmpty() || trimmed.length() > NICKNAME_MAX) {
            throw new BusinessRuleException("닉네임은 1~" + NICKNAME_MAX + "자여야 합니다.");
        }
        if (!trimmed.equals(member.getNickname()) && memberRepository.existsByNickname(trimmed)) {
            throw new DuplicateResourceException("이미 사용 중인 닉네임입니다.");
        }
        member.changeNickname(trimmed);
        return memberRepository.save(member);
    }

    /** 소셜 로그인 때 받은 카카오 토큰 보관 — "나에게 보내기"에 쓴다. */
    @Transactional
    public void updateKakaoToken(Member member, String accessToken, String refreshToken,
                                 java.time.LocalDateTime expiresAt) {
        member.updateKakaoToken(accessToken, refreshToken, expiresAt);
        memberRepository.save(member);
    }

    /** 알림 받을 이메일 등록·변경. 빈 값이면 해제. */
    @Transactional
    public Member changeEmail(Member member, String email) {
        member.changeEmail(email);
        return memberRepository.save(member);
    }

    /** 알림톡 수신 번호 등록·변경. 빈 값이면 해제(그러면 이메일로만 간다). */
    @Transactional
    public Member changePhoneNumber(Member member, String phoneNumber) {
        member.changePhoneNumber(phoneNumber);
        return memberRepository.save(member);
    }

    @Transactional
    public void updateNotifySettings(Member member, boolean reg, boolean exam, boolean change) {
        member.updateNotifySettings(reg, exam, change);
        memberRepository.save(member);
    }

    /**
     * 탈퇴. 관심 등록도 지운다 — 탈퇴자에게 알림이 가면 안 되고, 시험의 관심 수가 부풀면 인기순이 왜곡된다.
     * 글·댓글은 남는다(작성자 표기만 "탈퇴한 사용자").
     */
    @Transactional
    public void withdraw(Member member) {
        List<UserFavorite> favorites = userFavoriteRepository.findByMemberOrderByCreatedAtAsc(member);
        favorites.forEach(f -> f.getCertificate().decrementFavorite());
        userFavoriteRepository.deleteAll(favorites);

        member.withdraw();
        memberRepository.save(member);
        log.info("[Member] 탈퇴 id={} (관심 {}건 해제)", member.getId(), favorites.size());
    }
}
