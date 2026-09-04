package com.test.test.exam.admin;

import java.util.Collection;

/**
 * 시행기관 이름 매칭 — <b>한 곳에서만</b> 한다.
 *
 * <p>시드마다 같은 기관을 다르게 적는다: "한국방송통신전파진흥원(KCA)" / "한국방송통신전파진흥원",
 * "한국보건의료인국가시험원(국시원)" / "한국보건의료인국가시험원", "금융투자협회 / 한국금융연수원".
 * 매니저 판정(어느 소스가 맡는가)과 일정 없음 이유(크롤링 예정인가)가 각자 비교하면 한쪽만 맞는 일이 생긴다.
 *
 * <p>규칙: 괄호 접미사를 떼고 공백을 지운 뒤, <b>어느 한쪽이 다른 쪽을 포함하면</b> 같은 기관으로 본다.
 */
public final class AgencyMatcher {

    private AgencyMatcher() {
    }

    /** {@code agency} 가 {@code candidates} 중 하나와 같은 기관인가. */
    public static boolean matches(String agency, Collection<String> candidates) {
        String a = normalize(agency);
        if (a.isEmpty() || candidates == null) {
            return false;
        }
        for (String candidate : candidates) {
            String c = normalize(candidate);
            if (!c.isEmpty() && (a.contains(c) || c.contains(a))) {
                return true;
            }
        }
        return false;
    }

    /** {@code agencies} 중 하나라도 {@code candidates} 와 같은 기관인가. */
    public static boolean matchesAny(Collection<String> agencies, Collection<String> candidates) {
        if (agencies == null || candidates == null || candidates.isEmpty()) {
            return false;
        }
        for (String agency : agencies) {
            if (matches(agency, candidates)) {
                return true;
            }
        }
        return false;
    }

    /** 괄호 접미사·공백 제거, 대소문자 무시. */
    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("\\([^)]*\\)", "")
                .replaceAll("\\s+", "")
                .toLowerCase();
    }
}
