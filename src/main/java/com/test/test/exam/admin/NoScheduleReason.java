package com.test.test.exam.admin;

import com.test.test.exam.domain.Certificate;

import java.util.Set;

/**
 * 일정이 없는 시험의 <b>이유</b> — 매니저가 손댈 것과 아닌 것을 가른다.
 *
 * <p>"일정 없음 143종"은 한 덩어리가 아니다. 큐넷 종목은 공고가 나면 배치가 받고, 시행처 사이트에
 * 날짜가 보이는 곳은 스크래퍼가 붙을 예정이며, 정말로 사람이 넣어야 하는 것은 그 나머지다.
 * 근거와 목록은 {@code 설계/시험데이터/05_일정없음_분류.md} — <b>그 문서의 C 표와 여기
 * {@link #CRAWL_PLANNED_AGENCIES} 는 같이 고친다.</b> 상시·예약제는 여기 오지 않는다(별도 상태 ROLLING).
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

    /** 순수 HTTP 에 날짜가 보이는 시행처(05 문서 C 표). 스크래퍼가 붙으면 일정이 생겨 자연히 목록에서 빠진다. */
    static final Set<String> CRAWL_PLANNED_AGENCIES = Set.of(
            "대한상공회의소", "한국방송통신전파진흥원(KCA)", "서울대학교 TEPS관리위원회", "한국데이터산업진흥원",
            "한국어문회", "금융감독원", "한국정보통신자격협회(ICQA)", "한국정보통신진흥협회(KAIT)", "YBM",
            "한국CPO포럼", "한국신용정보협회", "국회사무처", "보험연수원", "삼일회계법인",
            "한국정보통신인력개발센터", "국립국어원");

    public static NoScheduleReason of(Certificate c) {
        String code = c.getSourceCode();
        if (code != null && code.matches("[0-9]{4}")) {
            return ANNOUNCEMENT_PENDING;   // 큐넷 종목코드(jmCd)는 숫자 4자리
        }
        String agency = c.getAgency();
        if (agency != null && CRAWL_PLANNED_AGENCIES.stream().anyMatch(agency::contains)) {
            return CRAWL_PLANNED;
        }
        return MANUAL;
    }
}
