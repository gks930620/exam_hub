package com.test.test.exam.admin;

import com.test.test.exam.domain.Certificate;

import java.util.Set;

/**
 * 일정이 없는 시험의 <b>이유</b> — 매니저가 손댈 것과 아닌 것을 가른다.
 *
 * <p>"일정 없음 140종"(2026-09-08 실측)은 한 덩어리가 아니다. 큐넷 종목은 공고가 나면 배치가 받고, 시행처 사이트에
 * 날짜가 보이는 곳은 스크래퍼가 붙을 예정이며, 정말로 사람이 넣어야 하는 것은 그 나머지다.
 * 근거와 목록은 {@code 설계/시험데이터/05_일정없음_분류.md} — <b>그 문서의 C 표와 여기
 * {@link #CRAWL_PLANNED_AGENCIES} 는 같이 고친다.</b> 상시·예약제는 여기 오지 않는다(별도 상태 ROLLING).
 *
 * <p>기관명 매칭은 {@link AgencyMatcher}(괄호 접미사 무시·양방향 포함) — 매니저 판정과 같은 규칙이다.
 */
public enum NoScheduleReason {

    /** 큐넷 종목인데 2026 공고가 아직 없다 — 나오면 05:00 배치가 자동으로 받는다 */
    ANNOUNCEMENT_PENDING("자동 · 공고 전"),
    /** 시행처 사이트에 날짜가 보여 스크래퍼를 붙일 예정 — 기다리면 된다 */
    CRAWL_PLANNED("자동 · 크롤링 예정"),
    /** JS 렌더링·차단·PDF 공고라 지금 도구로는 못 받는다 — 매니저가 넣는다 */
    MANUAL("수기 필수");

    private final String label;

    NoScheduleReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * 순수 HTTP 에 날짜가 보이는 시행처(05 문서 C 표). 괄호 약칭은 적지 않는다 — 매칭이 떼고 본다.
     *
     * <p><b>스크래퍼를 붙이면 여기서 뺀다.</b> 남아 있으면 그 기관의 <i>못 긁는</i> 시험까지
     * "기다리면 자동으로 들어온다"고 안내해 매니저가 영영 안 넣는다. YBM·한국어문회·금융감독원을 뺀 이유가 그것이다(2026-09-08).
     * YBM 은 7종이 자동이 됐고 남은 BCT 는 사이트가 안 열린다. 어문회는 4종 전부 자동이 됐다.
     * 금감원은 공인회계사만 자동이고 보험계리사·손해사정사·보험중개사는 시험 사이트를 못 찾았다 — 진짜 수기다.
     */
    static final Set<String> CRAWL_PLANNED_AGENCIES = Set.of(
            "대한상공회의소", "한국방송통신전파진흥원", "서울대학교 TEPS관리위원회", "한국데이터산업진흥원",
            "한국정보통신진흥협회", "한국CPO포럼", "한국신용정보협회", "삼일회계법인",
            "한국정보통신인력개발센터", "국립국어원");
    // 뺀 곳(2026-09-09) — "기다리면 자동으로 들어온다"가 사실이 아니게 됐다:
    //   보험연수원        붙였다(InsuranceScheduleSource)
    //   한국정보통신자격협회  ICQA 는 정기시험 일정을 구글 캘린더 링크로만 안내한다(공지 610) — 못 긁는다
    //   국회사무처        robots.txt 가 전체 차단이라 긁지 않기로 했다

    /** 공단 시행인데 4자리 코드가 아니면 전문자격 — 기술자격 API 밖이라 큐넷 전문자격 게시판을 긁어야 한다(05 문서 C 표 1번). */
    private static final Set<String> QNET_AGENCY = Set.of("한국산업인력공단");

    public static NoScheduleReason of(Certificate c) {
        String code = c.getSourceCode();
        if (code != null && code.matches("[0-9]{4}")) {
            return ANNOUNCEMENT_PENDING;   // 큐넷 종목코드(jmCd)는 숫자 4자리
        }
        String agency = c.getAgency();
        if (AgencyMatcher.matches(agency, QNET_AGENCY)) {
            return CRAWL_PLANNED;
        }
        if (AgencyMatcher.matches(agency, CRAWL_PLANNED_AGENCIES)) {
            return CRAWL_PLANNED;
        }
        return MANUAL;
    }
}
