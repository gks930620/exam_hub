package com.test.test.exam.manager;

import com.test.test.exam.domain.AuthProvider;
import com.test.test.exam.domain.Member;
import com.test.test.exam.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.test.test.common.config.RuntimeEnv;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매니저(운영자) 계정을 <b>환경변수에서</b> 만든다.
 *
 * <p><b>왜 가입 화면이 없나</b>: 매니저는 시험 일정을 손으로 고칠 수 있는 계정이다.
 * 가입 경로를 열어 두면 그 자체가 공격면이 되고, "누가 매니저인지"를 코드가 아니라
 * 사람이 관리하게 된다. 그래서 <b>서버를 띄우는 사람만</b> 만들 수 있게 환경변수로 못 박는다.
 *
 * <p>환경 무관 실행(코드컨벤션 §5): 로컬은 {@code .env}, 배포는 Railway 변수 —
 * 이름이 같으므로 <b>코드를 고치지 않고</b> 양쪽에서 똑같이 동작한다.
 *
 * <pre>
 *   MANAGER_USERNAME=manager
 *   MANAGER_PASSWORD=충분히-긴-비밀번호
 * </pre>
 *
 * <p>둘 중 하나라도 없으면 <b>매니저 계정을 만들지 않는다</b>(로그인 화면이 "설정 안 됨"을 알린다).
 * 비밀번호를 바꾸고 재기동하면 기존 계정의 해시가 갱신된다 — 잊었을 때 되찾는 유일한 방법이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManagerAccountInitializer implements ApplicationRunner {

    /**
     * 운영에서 요구하는 최소 길이. 매니저는 시험 일정을 고칠 수 있는 계정이라 짧으면 그냥 뚫린다.
     *
     * <p><b>로컬은 이 제한을 걸지 않는다</b> — 개발하며 하루에도 몇 번씩 치는 값이라
     * 길면 오히려 창을 열어 두거나 메모에 적어 두게 된다. 대신 운영에서는 기동을 막는다
     * (RailwayDeploymentValidator). 로컬의 편의가 운영까지 따라가는 게 진짜 위험이다.
     */
    public static final int MIN_PASSWORD_LENGTH = 10;

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Value("${manager.username:}")
    private String username;

    @Value("${manager.password:}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (username.isBlank() || password.isBlank()) {
            log.info("[매니저] MANAGER_USERNAME/MANAGER_PASSWORD 가 없어 매니저 계정을 만들지 않습니다. "
                    + "운영 화면을 쓰려면 .env 에 두 값을 넣고 재기동하세요.");
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            if (RuntimeEnv.isProdLike(environment)) {
                // 운영에서는 만들지 않는다. 기동 자체는 RailwayDeploymentValidator 가 막는다.
                log.error("[매니저] 운영인데 비밀번호가 {}자입니다. {}자 이상이어야 계정을 만듭니다.",
                        password.length(), MIN_PASSWORD_LENGTH);
                return;
            }
            log.warn("[매니저] 비밀번호가 {}자로 짧습니다 — 로컬이라 그대로 만듭니다. "
                    + "배포할 때는 {}자 이상이어야 기동됩니다.", password.length(), MIN_PASSWORD_LENGTH);
        }

        String hash = passwordEncoder.encode(password);
        memberRepository.findByProviderAndProviderId(AuthProvider.LOCAL, username)
                .ifPresentOrElse(
                        existing -> {
                            existing.changePasswordHash(hash);
                            log.info("[매니저] 기존 계정의 비밀번호를 환경변수 값으로 갱신했습니다. id={}", username);
                        },
                        () -> {
                            memberRepository.save(Member.manager(username, hash, nicknameFor(username)));
                            log.info("[매니저] 계정을 만들었습니다. id={} — /manager/login 에서 로그인하세요.", username);
                        });
    }

    /** 닉네임은 유니크 제약이 있어 겹치면 못 만든다 — 매니저는 표시명을 따로 준다. */
    private String nicknameFor(String username) {
        String candidate = "매니저";
        return memberRepository.existsByNickname(candidate) ? "매니저-" + username : candidate;
    }
}
