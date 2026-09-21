package com.test.test.common.config;

import com.test.test.exam.auth.JwtProvider;
import com.test.test.exam.domain.NotificationChannel;
import com.test.test.exam.manager.ManagerAccountInitializer;
import com.test.test.exam.notification.NotificationSender;
import com.test.test.exam.notification.NotificationSenderChain;
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
 *   <li>H2 콘솔이 켜져 있다 → <b>임의 SQL 실행 창</b>이 열린다 → 차단</li>
 *   <li>발송 채널이 하나도 없다 → <b>알림이 로그로만 나가고 아무도 못 받는다</b> → 차단</li>
 * </ol>
 *
 * <p>여섯 가지 모두 "조용히 잘못 동작"하는 부류다 — 기동은 되고 로그도 멀쩡한데 데이터가 날아가거나,
 * 인증이 뚫려 있거나, 알림이 아무에게도 안 간다. 그래서 기동 시점에 시끄럽게 죽인다.
 *
 * <p>⚠️ H2 콘솔은 {@code application-prod.yml} 도 끄지만 그건 <b>prod 프로파일이 켜졌을 때만</b>이다.
 * Railway 에 올리며 {@code SPRING_PROFILES_ACTIVE} 를 빠뜨리면 나머지 설정이 맞는 한 기동은
 * 성공하고 콘솔만 열린 채 뜬다. 그래서 프로파일과 무관하게 여기서 한 번 더 막는다.
 *
 * <p>파일 업로드가 없어 BUCKET 검증은 대상이 아니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RailwayDeploymentValidator {

    private final Environment environment;
    /** 지금 떠 있는 발송 채널을 알기 위해 받는다 — 빈이 뜨는 조건이 곧 설정이다 */
    private final List<NotificationSender> notificationSenders;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    @Value("${app.link-base:}")
    private String linkBase;

    @Value("${manager.password:}")
    private String managerPassword;

    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

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

        // ⑤ H2 콘솔: 임의 SQL 을 치는 창이다. 붙을 JDBC 주소를 사람이 직접 넣는 구조라
        //    운영 DB 가 MySQL 이어도 그 창에서 운영 DB 로 붙을 수 있다.
        //    application-prod.yml 이 끄고는 있지만 그건 prod 프로파일이 켜졌을 때만이다 —
        //    SPRING_PROFILES_ACTIVE 를 빠뜨리고 올리면 나머지 설정이 맞는 한 기동은 성공하고,
        //    콘솔만 조용히 열린 채로 뜬다. 프로파일과 무관하게 여기서 한 번 더 막는다.
        if (h2ConsoleEnabled) {
            errors.add("운영에서 H2 콘솔이 켜져 있습니다(임의 SQL 실행 창이 열립니다). "
                    + "SPRING_PROFILES_ACTIVE=prod 를 설정하거나 SPRING_H2_CONSOLE_ENABLED=false 로 끄세요.");
        }

        // ⑥ 발송 채널: 알림톡도 이메일도 안 켜져 있으면 발송은 체인 끝의 LogNotificationSender 까지
        //    흘러가고, 그건 서버 로그에 한 줄 찍고 언제나 SUCCESS 를 돌려준다.
        //    그래서 notification_log 는 성공으로 가득 차고 통계도 100% 인데 받은 사람은 없다.
        //    이 서비스의 약속이 통째로 깨지는데 아무 신호가 없는 상태라 기동을 막는다.
        List<String> live = notificationSenders.stream()
                .filter(s -> !(s instanceof NotificationSenderChain))
                .map(NotificationSender::channel)
                .filter(c -> c != NotificationChannel.LOG)
                .map(Enum::name)
                .distinct()
                .toList();
        if (live.isEmpty()) {
            errors.add("운영에서 실제 발송 채널이 하나도 켜져 있지 않습니다(알림이 서버 로그로만 나가고 "
                    + "아무에게도 가지 않는데 발송 성공으로 기록됩니다). "
                    + "MAIL_ENABLED=true 와 메일 자격증명을 설정하거나 ALIMTALK_ENABLED=true 로 켜세요.");
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
