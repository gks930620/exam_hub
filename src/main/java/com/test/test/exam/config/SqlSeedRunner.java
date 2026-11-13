package com.test.test.exam.config;

import com.test.test.exam.repository.CertificateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/**
 * 시험 데이터를 {@code seed_exams.sql} 로 적재한다 — <b>로컬·운영 공통의 단 하나뿐인 시드 경로</b>다.
 *
 * <p>예전에는 자바 로더 셋이 JSON 을 읽어 수집 파이프라인({@code DiffService})에 태워 넣었다.
 * 빈 DB 라 비교할 대상이 없는데도 레코드마다 조회·비교·알림 재계산을 돌려서,
 * 일정 2,522건에 <b>75초</b>가 걸리고 SQL 로그가 91만 줄 나갔다(2026-11-13 실측).
 * 여기서는 INSERT 만 한다 — 같은 데이터가 <b>9.5초</b>에 들어간다.
 *
 * <p><b>비어 있을 때만 돈다.</b> 로컬은 인메모리라 기동마다 비어 있어 매번 돌고,
 * 운영은 첫 배포 때 한 번만 돈다. 두 번째 배포부터는 이미 데이터가 있어 건너뛰므로
 * 그동안 수집한 일정을 시드가 덮어쓰지 않는다.
 *
 * <p>테스트는 {@code seed.from-sql} 이 false(기본값)라 돌지 않는다 —
 * 통합테스트는 {@link ExamDataInitializer} 가 만드는 데모 픽스처를 쓴다.
 */
@Slf4j
@Component
@Order(-100)   // 다른 초기화기보다 먼저 — 이 데이터가 있어야 나머지가 "이미 있다"를 판단한다
@RequiredArgsConstructor
public class SqlSeedRunner {

    private static final String RESOURCE = "seed_exams.sql";

    /** 시드가 id 를 박아 넣으므로, 자동증가 카운터를 그 뒤로 밀어 줘야 하는 표들. */
    private static final List<String> SEEDED_TABLES =
            List.of("certificate", "certificate_detail", "exam_schedule", "notification_schedule");

    private final DataSource dataSource;
    private final CertificateRepository certificateRepository;

    @Value("${seed.from-sql:false}")
    private boolean enabled;

    /**
     * {@code ApplicationReadyEvent} 가 아니라 <b>{@code ContextRefreshedEvent}</b> 다.
     *
     * <p>스프링 부트는 컨텍스트 갱신이 끝난 뒤에 톰캣을 연다. 늦게 돌리면 "떴다"고 한 뒤
     * 시드가 들어가는 동안 <b>빈 사이트가 몇 초 노출된다</b>(실제로 그 창에 집계가 0 으로 나왔다).
     * 여기서 돌리면 첫 요청을 받기 전에 데이터가 다 들어가 있다.
     */
    @EventListener(ContextRefreshedEvent.class)
    @Transactional
    public void seed() {
        if (!enabled) {
            return;
        }
        long already = certificateRepository.count();
        if (already > 0) {
            log.info("[SqlSeed] 시험 데이터가 이미 {}종 있다 — 시드를 건너뛴다(운영 재배포는 이 경로다)", already);
            return;
        }

        ClassPathResource sql = new ClassPathResource(RESOURCE);
        if (!sql.exists()) {
            log.error("[SqlSeed] {} 이 없다 — 시험 데이터 없이 뜬다. 화면이 비어 보일 것이다", RESOURCE);
            return;
        }

        long t0 = System.currentTimeMillis();
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(sql);
        populator.setSqlScriptEncoding("UTF-8");

        try (Connection conn = dataSource.getConnection()) {
            populator.populate(conn);
            restartIdentities(conn);
        } catch (Exception e) {
            // 여기서 죽이지 않는다 — 데이터가 없는 채로라도 뜨는 편이 낫고, 로그를 보면 원인이 바로 나온다.
            log.error("[SqlSeed] 적재 실패 — 시험 데이터 없이 계속한다: {}", e.toString(), e);
            return;
        }

        log.info("[SqlSeed] {} 적재 완료 — 시험 {}종, {}ms",
                RESOURCE, certificateRepository.count(), System.currentTimeMillis() - t0);
    }

    /**
     * 자동증가 카운터를 시드가 쓴 id 뒤로 민다.
     *
     * <p>시드는 id 를 명시해서 넣는다. 카운터를 그대로 두면 다음 INSERT 가 1번부터 시작해
     * "Unique index or primary key violation" 으로 깨진다 — 새 글 하나 못 쓴다.
     *
     * <p>문법이 DB 마다 달라서 SQL 파일에 못 적는다. 붙은 DB 를 보고 고른다.
     */
    private void restartIdentities(Connection conn) throws Exception {
        String product = conn.getMetaData().getDatabaseProductName();
        boolean mysql = product != null && product.toLowerCase().contains("mysql");

        for (String table : SEEDED_TABLES) {
            long next = maxId(conn, table) + 1;
            if (next <= 1) {
                continue;   // 그 표에 시드가 없었다
            }
            String ddl = mysql
                    ? "ALTER TABLE " + table + " AUTO_INCREMENT = " + next
                    : "ALTER TABLE " + table + " ALTER COLUMN id RESTART WITH " + next;
            try (Statement st = conn.createStatement()) {
                st.execute(ddl);
            }
        }
    }

    private long maxId(Connection conn, String table) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(id), 0) FROM " + table)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }
}
