package com.test.test.exam.manager;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 매니저 폼 로그인 (사용자 결정 2026-08-10 — 운영 계정은 소셜에 묶지 않는다).
 *
 * <p>일반 사용자는 소셜 단독이고 여기로 들어올 수 없다. 소셜 계정에는 비밀번호 해시가
 * 아예 없어서 {@code canLoginWithPassword()} 가 항상 false 다.
 *
 * <p>발급되는 토큰은 소셜 로그인과 <b>같은 JWT</b> 다 — 인증 방식만 다르고 그 뒤는 동일하게
 * {@code Authorization: Bearer} 로 흐른다. 관리 API 는 {@code ROLE_ADMIN} 으로 걸린다.
 *
 * <p>시도 제한(429)의 키는 (IP, 아이디)다. 프록시(Railway) 뒤에서는 {@code forward-headers-strategy: framework}
 * 가 {@code X-Forwarded-For} 를 풀어 주므로 {@code getRemoteAddr()} 가 실제 클라이언트 주소다.
 */
@RestController
@RequestMapping("/api/manager")
@RequiredArgsConstructor
public class ManagerLoginController {

    private final ManagerLoginService managerLoginService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest http) {
        return ResponseEntity.ok(new LoginResponse(
                managerLoginService.login(request.getUsername(), request.getPassword(), http.getRemoteAddr())));
    }

    /**
     * 매니저 계정이 설정돼 있는지 — 로그인 화면이 "설정 안 됨"을 안내하려고 묻는다.
     * 계정 존재 여부만 주고 아이디는 주지 않는다(추측의 힌트가 된다).
     */
    @GetMapping("/available")
    public ResponseEntity<AvailabilityResponse> available() {
        return ResponseEntity.ok(new AvailabilityResponse(managerLoginService.isConfigured()));
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        @NotBlank(message = "아이디를 입력하세요.")
        private String username;
        @NotBlank(message = "비밀번호를 입력하세요.")
        private String password;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginResponse {
        private String token;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvailabilityResponse {
        private boolean configured;
    }
}
