package com.test.test.exam.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * 키가 덜 채워진 소셜 등록을 등록부에서 걷어낸다.
 *
 * <p><b>왜 필요한가</b> — 스프링은 {@code client-secret} 이 비어 있어도 등록을 그대로 만든다.
 * 기동은 되지만 그 제공자는 <b>토큰 교환에서 반드시 실패</b>한다. 그러면 로그인 화면은
 * {@code /api/auth/providers} 로 물어 놓고도 "누르면 깨지는 버튼"을 다시 그리게 된다.
 * 여기서 미리 걷어내야 화면·서버가 같은 사실을 본다.
 *
 * <p><b>왜 "시크릿 비면 제외"를 일괄로 못 하나</b> — 카카오는 개발자센터에서 'Client Secret 사용'을
 * 켰을 때만 시크릿이 필요하고, 안 켜면 <b>빈 값이 정상</b>이다. 일괄 적용하면 멀쩡한 카카오가
 * 사라진다. 그래서 <b>시크릿이 규격상 필수인 제공자만</b> 이름으로 지정한다.
 *
 * <p>client-id 가 빈 경우는 여기까지 오지 못한다 — 스프링이 그 전에 기동을 깬다.
 * 그건 그대로 두는 편이 낫다. 아이디조차 없다는 건 설정을 하다 만 것이라, 조용히 넘어가면
 * 배포에서 "로그인 버튼이 왜 없지"로 한참 헤매게 된다.
 */
@Slf4j
@Component
public class IncompleteSocialLoginRemover implements BeanPostProcessor {

    /**
     * 시크릿 없이는 로그인이 성립하지 않는 제공자.
     * 구글은 웹 애플리케이션 클라이언트에 {@code client_secret} 이 필수다(카카오는 선택).
     */
    private static final Set<String> SECRET_REQUIRED = Set.of("google");

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!(bean instanceof InMemoryClientRegistrationRepository repository)) {
            return bean;
        }

        List<ClientRegistration> usable = new ArrayList<>();
        int total = 0;
        for (ClientRegistration registration : repository) {
            total++;
            if (isUsable(registration)) {
                usable.add(registration);
            } else {
                log.warn("[소셜로그인] {} 는 client-secret 이 비어 등록에서 제외했다 —"
                        + " 로그인 화면에도 안 나온다. 시크릿을 넣으면 저절로 살아난다.",
                        registration.getRegistrationId());
            }
        }

        if (usable.size() == total) {
            return bean;
        }
        return usable.isEmpty() ? new NoSocialLogin() : new InMemoryClientRegistrationRepository(usable);
    }

    private boolean isUsable(ClientRegistration registration) {
        if (!SECRET_REQUIRED.contains(registration.getRegistrationId())) {
            return true;
        }
        String secret = registration.getClientSecret();
        return secret != null && !secret.isBlank();
    }

    /**
     * 쓸 수 있는 제공자가 하나도 안 남았을 때.
     * {@link InMemoryClientRegistrationRepository} 는 빈 목록을 거부해서 이 자리를 대신한다.
     * 순회가 되므로 {@link SocialProvidersController} 가 빈 배열을 정확히 답할 수 있다.
     */
    private static final class NoSocialLogin
            implements ClientRegistrationRepository, Iterable<ClientRegistration> {

        @Override
        public ClientRegistration findByRegistrationId(String registrationId) {
            return null;
        }

        @Override
        public Iterator<ClientRegistration> iterator() {
            return Collections.emptyIterator();
        }
    }
}
