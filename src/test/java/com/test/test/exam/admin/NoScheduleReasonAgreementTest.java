package com.test.test.exam.admin;

import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.Series;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * <b>매니저 두 화면이 같은 시험을 같게 분류하는가.</b>
 *
 * <p>할 일 화면(`/admin`)과 수집 지도(`/admin/sources`)는 "이 시험의 일정이 어디서 오나"를 각자
 * 판정했다. 할 일 화면만 <b>"살아 있는 소스가 종목코드를 대 놓고 맡는가"</b>를 봤고 수집 지도는
 * 안 봐서, 다섯 시험이 한쪽에선 "공고 전 — 자동", 다른 쪽에선 "수기 필수"였다:
 * ERP정보관리사 물류·생산·인사·회계(한국생산성본부), 파생상품투자권유대행인(금융투자협회).
 * 화면 숫자로는 <b>수기 35 vs 첫 일정 입력 30</b> 이었다(2026-09-10 실측).
 *
 * <p>매니저는 "수기 필수 35종"을 보고 이미 자동으로 들어올 다섯을 손으로 넣는다. 넣어 봐야
 * 다음 수집이 덮으니 헛일이고, 두 화면 중 뭘 믿어야 할지도 알 수 없게 된다.
 *
 * <p>그래서 판정을 {@link NoScheduleReason#of(Certificate, Set)} 한 곳으로 모았고,
 * 여기서 그 계약을 고정한다.
 */
class NoScheduleReasonAgreementTest {

    private static Certificate cert(String name, String agency, String sourceCode) {
        return Certificate.builder()
                .name(name).slug(name).series(Series.ETC)
                .agency(agency).sourceCode(sourceCode)
                .build();
    }

    @Test
    @DisplayName("살아 있는 소스가 종목코드를 대면 '공고 전 — 자동'이다 (수기가 아니다)")
    void namedByLiveSource_isAutomatic() {
        // 한국생산성본부는 크롤링 예정 기관 목록에 없다 → 코드를 안 보면 '수기 필수'가 된다
        Certificate erp = cert("ERP정보관리사 회계", "한국생산성본부", "M0797");
        assertEquals(NoScheduleReason.MANUAL, NoScheduleReason.of(erp),
                "전제 확인: 코드를 안 보면 수기로 떨어진다");

        assertEquals(NoScheduleReason.ANNOUNCEMENT_PENDING,
                NoScheduleReason.of(erp, Set.of("M0797")),
                "소스가 이 종목코드를 맡는다고 이름을 댔다 — 기다리면 들어온다");
    }

    @Test
    @DisplayName("소스가 안 맡는 종목이면 코드가 있어도 판정이 그대로다")
    void unknownCode_fallsBackToBaseRule() {
        Certificate insurance = cert("보험계리사", "금융감독원", "M0900");
        assertEquals(NoScheduleReason.of(insurance),
                NoScheduleReason.of(insurance, Set.of("M0797", "M0798")),
                "남의 코드만 맡는 소스는 이 시험에 아무 영향이 없다");
    }

    @Test
    @DisplayName("종목코드가 없으면 코드 목록은 판정을 못 바꾼다")
    void nullCode_isUnaffected() {
        Certificate noCode = cert("한국어교원 2급", "국립국어원", null);
        assertEquals(NoScheduleReason.of(noCode),
                NoScheduleReason.of(noCode, Set.of("M0797")));
    }

    @Test
    @DisplayName("큐넷 4자리 코드는 코드 목록과 무관하게 '공고 전 — 자동'이다")
    void qnetCode_staysAutomatic() {
        Certificate qnet = cert("정보처리기사", "한국산업인력공단", "1320");
        assertEquals(NoScheduleReason.ANNOUNCEMENT_PENDING, NoScheduleReason.of(qnet));
        assertEquals(NoScheduleReason.ANNOUNCEMENT_PENDING, NoScheduleReason.of(qnet, Set.of()));
    }

    @Test
    @DisplayName("빈 코드 목록을 주면 예전 판정과 완전히 같다 — 오버로드가 기존 규칙을 바꾸지 않는다")
    void emptyCodes_matchLegacyRule() {
        Certificate[] samples = {
                cert("정보처리기사", "한국산업인력공단", "1320"),
                cert("무역영어 1급", "대한상공회의소", null),
                cert("보험계리사", "금융감독원", null),
                cert("한국어교원 2급", "국립국어원", null),
                cert("ERP정보관리사 회계", "한국생산성본부", "M0797"),
        };
        for (Certificate c : samples) {
            assertEquals(NoScheduleReason.of(c), NoScheduleReason.of(c, Set.of()),
                    c.getName() + " 의 판정이 달라졌다");
        }
    }
}
