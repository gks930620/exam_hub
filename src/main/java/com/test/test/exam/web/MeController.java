package com.test.test.exam.web;

import com.test.test.common.exception.BusinessRuleException;
import com.test.test.exam.auth.CurrentMember;
import com.test.test.exam.auth.MemberService;
import com.test.test.exam.domain.Member;
import com.test.test.exam.service.FavoriteService;
import com.test.test.exam.web.dto.FavoriteDtos;
import com.test.test.exam.web.dto.MeDtos;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 로그인 사용자 전용 API (설계 08 §4).
 * 전부 로그인 필수 — 비로그인은 {@code 401 UNAUTHENTICATED}.
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final FavoriteService favoriteService;
    private final MemberService memberService;

    /** 내 정보 */
    @GetMapping
    public ResponseEntity<MeDtos.MemberResponse> me(@CurrentMember Member member) {
        return ResponseEntity.ok(MeDtos.MemberResponse.of(member));
    }

    /** 닉네임 변경 */
    @PatchMapping
    public ResponseEntity<MeDtos.MemberResponse> updateProfile(
            @CurrentMember Member member,
            @Valid @RequestBody MeDtos.UpdateProfileRequest request) {
        return ResponseEntity.ok(MeDtos.MemberResponse.of(
                memberService.changeNickname(member, request.nickname())));
    }

    /** 알림 받을 이메일 등록·변경 — 카카오 로그인은 이메일을 안 주므로 직접 받는다 */
    @PutMapping("/email")
    public ResponseEntity<MeDtos.MemberResponse> updateEmail(
            @CurrentMember Member member,
            @Valid @RequestBody MeDtos.UpdateEmailRequest request) {
        return ResponseEntity.ok(MeDtos.MemberResponse.of(
                memberService.changeEmail(member, request.email())));
    }

    /** 알림톡 수신 번호 등록·변경 — 이게 있어야 카톡으로 알림이 간다 */
    @PutMapping("/phone")
    public ResponseEntity<MeDtos.MemberResponse> updatePhone(
            @CurrentMember Member member,
            @Valid @RequestBody MeDtos.UpdatePhoneRequest request) {
        return ResponseEntity.ok(MeDtos.MemberResponse.of(
                memberService.changePhoneNumber(member, request.phoneNumber())));
    }

    /** 회원 탈퇴 — 글·댓글은 남고 작성자 표기만 "탈퇴한 사용자"가 된다 */
    @DeleteMapping
    public ResponseEntity<Void> withdraw(@CurrentMember Member member) {
        memberService.withdraw(member);
        return ResponseEntity.noContent().build();
    }

    /** 홈 D-day 카드 목록 */
    @GetMapping("/favorites")
    public ResponseEntity<FavoriteDtos.ListResponse> favorites(@CurrentMember Member member) {
        return ResponseEntity.ok(favoriteService.getFavorites(member));
    }

    /** 관심 등록 */
    @PostMapping("/favorites")
    public ResponseEntity<FavoriteDtos.CreateResponse> addFavorite(
            @CurrentMember Member member,
            @Valid @RequestBody FavoriteDtos.CreateRequest request) {
        FavoriteDtos.CreateResponse res = favoriteService.addFavorite(member, request.certificateId());
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    /** 관심 해제 */
    @DeleteMapping("/favorites/{certificateId}")
    public ResponseEntity<Void> removeFavorite(
            @CurrentMember Member member,
            @PathVariable Long certificateId) {
        favoriteService.removeFavorite(member, certificateId);
        return ResponseEntity.noContent().build();
    }

    /** 알림 유형 토글 현재값 조회 — 설정 화면 초기값 */
    @GetMapping("/notify-settings")
    public ResponseEntity<MeDtos.NotifySettings> notifySettings(@CurrentMember Member member) {
        return ResponseEntity.ok(new MeDtos.NotifySettings(
                member.isNotifyReg(), member.isNotifyExam(), member.isNotifyChange()));
    }

    /** 알림 유형 토글 저장 */
    @PutMapping("/notify-settings")
    public ResponseEntity<MeDtos.NotifySettings> updateNotifySettings(
            @CurrentMember Member member,
            @RequestBody MeDtos.NotifySettings request) {
        memberService.updateNotifySettings(member,
                request.notifyReg(), request.notifyExam(), request.notifyChange());
        return ResponseEntity.ok(new MeDtos.NotifySettings(
                member.isNotifyReg(), member.isNotifyExam(), member.isNotifyChange()));
    }

    /**
     * 월간 캘린더 이벤트.
     *
     * <p>달을 먼저 검증한다 — 안 하면 {@code LocalDate.of(year, 13, 1)} 이 터져 500 이 난다.
     * 관심 시험이 없을 때는 그 앞에서 빈 목록으로 빠져나가 안 터졌기 때문에, 데이터가 쌓인
     * 계정에서만 나는 사고였다(QA 2026-09-03).
     */
    @GetMapping("/calendar")
    public ResponseEntity<MeDtos.CalendarResponse> calendar(
            @CurrentMember Member member,
            @RequestParam int year,
            @RequestParam int month) {
        if (month < 1 || month > 12) {
            throw new BusinessRuleException("month 는 1~12 사이여야 합니다.");
        }
        if (year < 2000 || year > 2100) {
            throw new BusinessRuleException("year 는 2000~2100 사이여야 합니다.");
        }
        return ResponseEntity.ok(favoriteService.calendar(member, year, month));
    }
}
