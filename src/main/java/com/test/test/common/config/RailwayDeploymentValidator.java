package com.test.test.common.config;

import com.test.test.exam.auth.JwtProvider;
import com.test.test.exam.manager.ManagerAccountInitializer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 배포(운영) 필수 설정 검증기 — "조용한 성공(데이터 소실)"보다 "시끄러운 실패"를 택한다.
 * (코드컨벤션 §5-2, 개발배포 체크리스트 A2 fail-fast)
 *
 * <p>운영으로 감지되는 조건: <b>prod 프로파일 활성</b> 또는 <b>RAILWAY_* 환경변수 존재</b>.
 * 운영일 때만 아래를 검사하고, 하나라도 걸리면 한국어 로그 + 예외로 기동을 중단한다.
 * <ol>
 *   <li>JDBC URL 이 H2/인메모리(:mem:) → 재배포마다 데이터 소실 → 차단</li>
 *   <li>JWT 서명키가 개발용 기본값 → <b>토큰을 누구나 위조</b>할 수 있다 → 차단</li>
 *   <li>알림 링크 주소가 localhost → 메일은 가는데 <b>링크가 죽어</b> 접수하러 갈 수 없다 → 차단</li>
 *   <li>매니저 비밀번호가 로컬용으로 짧다 → <b>운영 화면이 그대로 뚫린다</b> → 차단</li>
 * </ol>
 *
 * <p>세 가지 모두 "조용히 잘못 동작"하는 부류다 — 기동은 되고 로그도 멀쩡한데 데이터가 날아가거나,
 * 인증이 뚫려 있거나, 알림이 무용지물이 된다. 그래서 기동 시점에 시끄럽게 죽인다.
 *
 * <p>파일 업로드가 없어 BUCKET 검증은 대상이 아니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RailwayDeploymentValidator {

    private final Environment environment;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    @Value("${app.link-base:}")
    private String linkBase;

    @Value("${manager.password:}")
    private String managerPassword;

    @PostConstruct
    public void validate() {
        if (!isProdLikeEnvironment()) {
            log.debug("[RailwayDeploymentValidator] 로컬/개발 환경 — 배포 검증 생략");
            return;
        }

        log.info("[RailwayDeploymentValidator] 운영 환경 감지 — 배포 필수 설정 검증 시작");
        List<String> errors = new ArrayList<>();

        // ① DB: 운영에서 H2/인메모리로 뜨면 재배포마다 데이터가 사라진다.
        String url = datasourceUrl == null ? "" : datasourceUrl.toLowerCase();
        if (url.isBlank() || url.contains("jdbc:h2") || url.contains(":h2:") || url.contains(":mem:")) {
            errors.add("운영에서 JDBC URL 이 비었거나 H2/인메모리입니다(재배포 시 데이터 소실). "
                    + "SPRING_DATASOURCE_URL 을 Railway MySQL 로 설정하세요. 현재값=[" + datasourceUrl + "]");
        }

        // ② JWT: 개발용 기본키로 운영에 뜨면 서명키가 저장소에 공개된 셈이라 누구나 토큰을 위조한다.
        if (jwtSecret == null || jwtSecret.isBlank() || JwtProvider.DEV_SECRET.equals(jwtSecret)) {
            errors.add("운영에서 JWT 서명키가 비었거나 개발용 기본값입니다(토큰 위조 가능). "
                    + "JWT_SECRET_KEY 에 32자 이상 임의 문자열을 설정하세요.");
        }

        // ③ 알림 링크: 메일은 정상 발송되는데 버튼만 localhost 로 가면 아무도 눈치채지 못한다.
        String link = linkBase == null ? "" : linkBase.toLowerCase();
        if (link.isBlank() || link.contains("localhost") || link.contains("127.0.0.1")) {
            errors.add("운영에서 알림 링크 주소가 비었거나 localhost 입니다(메일 속 링크가 죽습니다). "
                    + "APP_PUBLIC_BASE_URL 에 공개 도메인(https://...)을 설정하세요. 현재값=[" + linkBase + "]");
        }

        // ④ 매니저 비밀번호: 로컬에서 쓰던 짧은 값이 그대로 배포되는 게 가장 흔한 사고다.
        //    설정 자체를 안 했으면 매니저 계정이 없는 것이라 문제가 아니다(운영 화면만 못 쓴다).
        if (!managerPassword.isBlank() && managerPassword.length() < ManagerAccountInitializer.MIN_PASSWORD_LENGTH) {
            errors.add("운영에서 매니저 비밀번호가 " + managerPassword.length() + "자입니다(운영 화면이 뚫립니다). "
                    + "MANAGER_PASSWORD 를 " + ManagerAccountInitializer.MIN_PASSWORD_LENGTH + "자 이상으로 바꾸세요.");
        }

        // 파일 업로드가 없어 BUCKET 검증은 대상이 아니다.

        if (!errors.isEmpty()) {
            String joined = errors.stream().map(e -> " - " + e).collect(Collectors.joining("\n"));
            log.error("[RailwayDeploymentValidator] 운영 배포 필수 설정 누락으로 기동을 중단합니다:\n{}", joined);
            throw new IllegalStateException("운영 배포 설정 검증 실패:\n" + joined);
        }

        log.info("[RailwayDeploymentValidator] 운영 배포 필수 설정 검증 통과");
    }

    private boolean isProdLikeEnvironment() {
        return RuntimeEnv.isProdLike(environment);
    }
}
