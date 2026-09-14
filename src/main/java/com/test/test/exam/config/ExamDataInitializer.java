package com.test.test.exam.config;

import com.test.test.exam.collect.CollectService;
import com.test.test.exam.collect.DemoDataProvider;
import com.test.test.exam.domain.AuthProvider;
import com.test.test.exam.domain.Member;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.UserFavorite;
import com.test.test.exam.repository.MemberRepository;
import com.test.test.exam.repository.CertificateRepository;
import com.test.test.exam.repository.UserFavoriteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 데모 데이터 시드 + 기동 시 파일 시드 적재 (설계 04 §4, {@code @Profile("!prod")}).
 * 1) 자격증 마스터 10종 2) 데모 사용자 + 관심 3종 — <b>DB 가 비어 있을 때만</b>.
 * 3) 파일 시드(스냅샷·비큐넷·데모 일정) 적재 — <b>매 기동</b>. 파일은 공짜고, 스냅샷은 DB 가 더 새로우면 스스로 건너뛴다.
 *
 * <p>예전엔 3)도 "DB 가 비어 있을 때만" 안에 있어서, 로컬 DB 가 파일 H2 가 된 뒤로는 두 번째 기동부터
 * 시드 파일을 고쳐도 반영되지 않았다(2026-09-03).
 */
@Slf4j
@Component
@Profile("!prod")
@RequiredArgsConstructor
public class ExamDataInitializer {

    /** 데모 계정 — 로그인 없이 화면을 확인할 때 쓴다(개발 전용). */
    public static final String DEMO_PROVIDER_ID = "demo-0001";

    private final CertificateRepository certificateRepository;
    private final MemberRepository memberRepository;
    private final UserFavoriteRepository userFavoriteRepository;
    private final DemoDataProvider demoDataProvider;
    private final CollectService collectService;

    /**
     * 기동할 때 <b>네트워크</b> 수집(큐넷 613콜 + 스크래퍼 8곳)을 돌릴지. <b>기본 false.</b>
     *
     * <p>예전엔 "큐넷이 꺼져 있으면 돌린다"였는데, {@code .env} 가 없는 새 환경에서는 큐넷이 꺼져 있고
     * 스크래퍼는 켜져 있어(기본 true) 첫 기동에 시행처 8곳을 실호출했다. 명시적으로 켠 경우만 돈다:
     * {@code --collect.on-startup=true}. 파일 시드는 이 값과 무관하게 늘 읽는다.
     */
    @Value("${collect.on-startup:false}")
    private boolean collectOnStartup;

    @EventListener(ApplicationReadyEvent.class)
    @Order(0)   // 시험 마스터 시드(CertificateMasterInitializer, @Order(100))보다 먼저 — 이쪽이 일정까지 만든다
    @Transactional
    public void seed() {
        if (certificateRepository.count() == 0) {
            seedDemo();
        } else {
            log.info("[Seed] 자격증 데이터가 이미 존재 — 데모 마스터는 건너뛰고 파일 시드만 다시 읽는다");
        }

        if (!collectOnStartup) {
            log.info("[Seed] 네트워크 수집 생략 — 큐넷 호출 한도와 시행처 사이트를 아끼기 위해서다. "
                    + "지금 받으려면 --collect.on-startup=true (05:00 배치는 그대로 돈다)");
            collectService.collectWithoutNetwork();
            return;
        }
        // 수집은 외부 네트워크에 기대는 일이라 언제든 실패할 수 있다.
        // 그때 앱이 안 뜨면 이미 들어와 있는 시험 정보까지 못 보게 된다 —
        // 서비스가 죽는 것보다 "오늘 수집이 안 됐다"가 훨씬 낫다.
        try {
            collectService.collectAll();
        } catch (Exception e) {
            log.error("[Seed] 기동 수집 실패 — 기존 데이터로 서비스는 계속합니다: {}", e.toString());
        }
        log.info("[Seed] 초기 수집 배치 완료 — 일정/알림 예약 생성됨");
    }

    /** 데모 마스터 10종 + 데모 계정(관심 3종). 일정은 수집(MockScheduleSource)이 만든다. */
    private void seedDemo() {
        List<Certificate> certs = demoDataProvider.demoCertificates().stream()
                .map(d -> Certificate.builder()
                        // slug 은 이름에서 만든다 — 규칙은 reconcileSlugs() 한 곳에만 둔다
                        .name(d.name()).slug(d.name().replaceAll("\\s+", ""))
                        .series(d.series()).agency(d.agency())
                        .category(d.category())
                        .sourceCode(d.sourceCode())
                        .build())
                .map(certificateRepository::save)
                .toList();
        log.info("[Seed] 자격증 마스터 {}종 생성", certs.size());

        Member demo = memberRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, DEMO_PROVIDER_ID)
                .orElseGet(() -> memberRepository.save(Member.builder()
                        .provider(AuthProvider.GOOGLE).providerId(DEMO_PROVIDER_ID)
                        .email("demo@example.com").nickname("데모사용자")
                        .build()));
        certs.stream().limit(3).forEach(c -> {
            userFavoriteRepository.save(UserFavorite.builder().member(demo).certificate(c).build());
            c.incrementFavorite();
            certificateRepository.save(c);
        });
        log.info("[Seed] 데모 계정({}) 관심 3종 등록", demo.getNickname());
    }
}
