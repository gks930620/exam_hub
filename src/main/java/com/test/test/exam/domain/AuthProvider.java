package com.test.test.exam.domain;

/**
 * 로그인 공급자.
 *
 * <p><b>일반 사용자는 소셜 단독</b>이다(설계 08). 자체 이메일/비밀번호 회원가입은 만들지 않는다.
 *
 * <p>{@link #LOCAL} 만 예외다 — <b>매니저(운영자) 계정</b>은 카카오·구글에 묶이면 안 된다.
 * 소셜 로그인이 막히거나 키가 만료되면 운영 자체를 못 하게 되고, 운영 계정을 개인 SNS 계정에
 * 얹는 것도 인수인계가 곤란하다. 그래서 매니저는 아이디/비밀번호 폼 로그인을 쓴다.
 * 이 값의 계정은 <b>가입 경로가 없고</b> 환경변수로만 만들어진다({@code ManagerAccountInitializer}).
 */
public enum AuthProvider {
    KAKAO("카카오"),
    GOOGLE("구글"),
    LOCAL("자체(매니저)");

    private final String label;

    AuthProvider(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 스프링 시큐리티의 registrationId("kakao"/"google") → enum */
    public static AuthProvider from(String registrationId) {
        return valueOf(registrationId.toUpperCase());
    }
}
