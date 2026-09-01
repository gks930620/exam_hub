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
 * 데모 데이터 시드 (설계 04 §4, {@code @Profile("!prod")}).
 * 1) 자격증 마스터 10종 2) 데모 기기 사용자 + 관심 3종 3) 수집 배치 1회 실행으로 일정/알림 예약 생성.
 * 기동 완료 후(ApplicationReadyEvent) 1회 실행, 이미 데이터가 있으면 스킵.
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

    @Value("${qnet.api.enabled:false}")
    private boolean qnetApiEnabled;

    /**
     * 기동할 때 수집을 한 번 돌릴지. <b>비워 두면 알아서 정한다</b>(권장).
     *
     * <ul>
     *   <li>큐넷 실 API 가 <b>꺼져</b> 있으면(로컬 기본) Mock·시드라 공짜다 → 돌린다.</li>
     *   <li>큐넷을 <b>켜면</b> 종목별 1콜씩 613콜이 나간다. 개발계정 한도가 일 1,000회라
     *       재시작 두 번이면 넘긴다 → 안 돌리고 05:00 스케줄러에 맡긴다.</li>
     * </ul>
     * 지금 당장 받아야 하면 {@code --collect.on-startup=true}.
     */
    @Value("${collect.on-startup:}")
    private String collectOnStartup;

    /** 빈 값이면 "큐넷이 꺼져 있을 때만" 이 기본 동작이 된다. */
    private boolean shouldCollectOnStartup() {
        return collectOnStartup == null || collectOnStartup.isBlank()
                ? !qnetApiEnabled
                : Boolean.parseBoolean(collectOnStartup.trim());
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(0)   // 시험 마스터 시드(CertificateMasterInitializer, @Order(100))보다 먼저 — 이쪽이 일정까지 만든다
    @Transactional
    public void seed() {
        if (certificateRepository.count() > 0) {
            log.info("[Seed] 자격증 데이터가 이미 존재 — 시드 스킵");
            return;
        }

        // 1) 자격증 마스터
        List<Certificate> certs = demoDataProvider.demoCertificates().stream()
                .map(d -> Certificate.builder()
                        .name(d.name()).slug(d.slug())
                        .series(d.series()).agency(d.agency())
                        .category(d.category())
                        .sourceCode(d.sourceCode())
                        .build())
                .map(certificateRepository::save)
                .toList();
        log.info("[Seed] 자격증 마스터 {}종 생성", certs.size());

        // 2) 데모 사용자 + 관심 3종
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

        // 3) 수집 배치 1회 → exam_schedule + notification_schedule 파생
        //
        // ⚠️ 큐넷 실 API 를 켠 채로는 기동마다 돌리지 않는다. 종목별로 1콜씩 613콜이 나가는데
        //    개발계정 한도가 일 1,000회다 — 개발 중 두 번만 재시작해도 한도를 넘긴다
        //    (실제로 넘겨서 613콜이 전부 429 로 돌아온 적이 있다, 2026-08-10).
        //    실 수집은 05:00 스케줄러가 하고, 지금 당장 받아야 하면 아래 값을 켠다.
        if (!shouldCollectOnStartup()) {
            log.info("[Seed] 네트워크 수집 생략 — 큐넷 호출 한도를 아끼기 위해서다. "
                    + "지금 받으려면 --collect.on-startup=true (05:00 배치는 그대로 돈다)");
            // 파일 시드는 공짜다. 이것까지 건너뛰면 재시작할 때마다 일정이 하나도 없는 화면이 된다.
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
}
