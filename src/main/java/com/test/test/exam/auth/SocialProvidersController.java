package com.test.test.exam.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 지금 쓸 수 있는 소셜 로그인 제공자.
 *
 * <p>로그인 화면이 카카오·구글 버튼을 무조건 그리고 있었는데, 키를 안 넣은 제공자는 등록 자체가
 * 안 만들어져서 누르면 {@code Invalid Client Registration with Id: ...} 로 <b>흰 화면 500</b> 이
 * 떴다. 키를 하나만 넣은 상태는 이 서비스의 정상 상태라서(구글은 Client Secret 대기 중),
 * 화면이 <b>서버에 실제로 등록된 것만</b> 그리게 한다.
 *
 * <p>매니저 로그인의 {@code /api/manager/available} 과 같은 발상이다 — 화면이 설정 상태를 추측하지
 * 않고 서버에 묻는다. 비밀값은 주지 않고 <b>등록 ID 이름만</b> 준다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class SocialProvidersController {

    /**
     * 등록이 하나도 없으면 스프링이 이 빈을 아예 안 만든다(로컬에서 흔한 상태) —
     * 그래서 주입이 아니라 {@link ObjectProvider} 로 받아 없으면 빈 목록을 준다.
     */
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;

    @GetMapping("/providers")
    public ResponseEntity<ProvidersResponse> providers() {
        return ResponseEntity.ok(new ProvidersResponse(registeredIds()));
    }

    private List<String> registeredIds() {
        ClientRegistrationRepository repository = clientRegistrations.getIfAvailable();
        // 인메모리 구현은 Iterable 이지만 계약은 아니다 — 순회할 수 없으면 모른다고 답한다
        if (!(repository instanceof Iterable<?> registrations)) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (Object registration : registrations) {
            if (registration instanceof ClientRegistration client) {
                ids.add(client.getRegistrationId());
            }
        }
        return ids;
    }

    @Getter
    @AllArgsConstructor
    public static class ProvidersResponse {
        private final List<String> providers;
    }
}
