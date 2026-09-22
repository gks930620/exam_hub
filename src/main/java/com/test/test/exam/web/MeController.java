package com.test.test.exam.web;

import com.test.test.common.exception.BusinessRuleException;
import com.test.test.exam.auth.CurrentMember;
import com.test.test.exam.auth.MemberService;
import com.test.test.exam.domain.Member;

import java.util.Map;
import com.test.test.exam.notification.NotificationHistoryService;
import com.test.test.exam.service.IcsCalendar;
import com.test.test.exam.notification.TestNotificationService;
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
    private final TestNotificationService testNotificationService;
    private final NotificationHistoryService notificationHistoryService;

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
                memberService.changeNickname(member, request.getNickname())));
    }

    /** 알림 받을 이메일 등록·변경 — 카카오 로그인은 이메일을 안 주므로 직접 받는다 */
    @PutMapping("/email")
    public ResponseEntity<MeDtos.MemberResponse> updateEmail(
            @CurrentMember Member member,
            @Valid @RequestBody MeDtos.UpdateEmailRequest request) {
        return ResponseEntity.ok(MeDtos.MemberResponse.of(
                memberService.changeEmail(member, request.getEmail())));
    }

    /** 알림톡 수신 번호 등록·변경 — 이게 있어야 카톡으로 알림이 간다 */
    @PutMapping("/phone")
    public ResponseEntity<MeDtos.MemberResponse> updatePhone(
            @CurrentMember Member member,
            @Valid @RequestBody MeDtos.UpdatePhoneRequest request) {
        return ResponseEntity.ok(MeDtos.MemberResponse.of(
                memberService.changePhoneNumber(member, request.getPhoneNumber())));
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
        FavoriteDtos.CreateResponse res = favoriteService.addFavorite(member, request.getCertificateId());
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

    /**
     * 알림 유형 토글 저장.
     *
     * <p>세 값을 모두 받아야 한다({@code @Valid}). 빠진 값을 {@code false} 로 채워 저장하면
     * 사용자는 알림이 꺼진 줄도 모르고 접수 기간을 놓친다 — 이 서비스가 존재하는 이유가 그거다.
     */
    @PutMapping("/notify-settings")
    public ResponseEntity<MeDtos.NotifySettings> updateNotifySettings(
            @CurrentMember Member member,
            @Valid @RequestBody MeDtos.UpdateNotifySettingsRequest request) {
        memberService.updateNotifySettings(member,
                request.getNotifyReg(), request.getNotifyExam(), request.getNotifyChange());
        return ResponseEntity.ok(new MeDtos.NotifySettings(
                member.isNotifyReg(), member.isNotifyExam(), member.isNotifyChange()));
    }

    /**
     * 내 시험 일정을 <b>내 달력에 넣을 수 있는 파일</b>로 내려받는다.
     *
     * <p>우리는 메일로 알려 주지만 사람들이 실제로 일정을 보는 곳은 자기 휴대폰 달력이다.
     * 거기 넣어 두면 우리 메일이 스팸함에 빠져도 알림이 울린다 — <b>약속을 지키는 두 번째 줄</b>이다.
     *
     * <p>구글·애플·아웃룩이 다 읽는 표준(.ics)이라 연동을 따로 붙일 필요가 없다.
     */
    @GetMapping(value = "/calendar.ics", produces = "text/calendar;charset=UTF-8")
    public ResponseEntity<String> calendarIcs(@CurrentMember Member member) {
        String ics = IcsCalendar.render(favoriteService.upcomingEvents(member));
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"exam-hub.ics\"")
                .body(ics);
    }

    /**
     * 확인 메일 한 통 — <b>내 주소로 진짜 오나</b>.
     *
     * <p>주소에 오타가 하나 있으면 형식 검증은 통과하고, 발송도 성공으로 기록되고,
     * 메일만 조용히 사라진다. 사용자는 시험 접수를 놓친 뒤에야 안다. 지금 눌러 보면 지금 고칠 수 있다.
     *
     * <p>답은 정직해야 한다 — 발송이 로그 채널로 빠졌으면 "보냈습니다"가 아니라
     * "보낼 수단이 없습니다"라고 말한다({@code delivered=false}).
     */
    @PostMapping("/notify-settings/test")
    public ResponseEntity<TestNotificationService.Result> sendTestNotification(
            @CurrentMember Member member) {
        return ResponseEntity.ok(testNotificationService.send(member));
    }

    /**
     * 내가 받은 알림 목록 — <b>"나한테 뭘 보냈다는 건지"</b>.
     *
     * <p>확인 메일 버튼과 짝이다. 그 버튼은 "지금 보내면 오나"를, 이 목록은 "그동안 뭘 보냈나"를
     * 답한다. 둘이 어긋나면(보냈다는데 받은 게 없으면) 접수를 놓치기 전에 알아챈다.
     *
     * <p>{@code delivered} 는 성공이 아니라 <b>도달</b>이다 — 서버 로그로만 나간 건은 false 다.
     */
    @GetMapping("/notifications")
    public ResponseEntity<Map<String, Object>> notifications(@CurrentMember Member member) {
        return ResponseEntity.ok(Map.of(
                "items", notificationHistoryService.recent(member.getId())));
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
