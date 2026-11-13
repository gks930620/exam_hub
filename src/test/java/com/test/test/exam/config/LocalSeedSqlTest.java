package com.test.test.exam.config;

import com.test.test.exam.collect.DemoDataProvider;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.CertificateLifecycle;
import com.test.test.exam.domain.Series;
import com.test.test.exam.repository.CertificateDetailRepository;
import com.test.test.exam.repository.CertificateRepository;
import com.test.test.exam.repository.ExamScheduleRepository;
import com.test.test.exam.repository.NotificationScheduleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로컬·운영이 함께 쓰는 시드 {@code seed_exams.sql} 이 성한지.
 *
 * <p>로컬 DB 는 인메모리라 <b>기동할 때마다 비어 있고</b>, 이 파일 하나가 화면에 보이는 전부를 만든다.
 * 운영도 첫 배포에 이 파일로 뜬다. 깨진 걸 "서버 켜 보니 아무것도 없다"로 알아차리면 늦다.
 *
 * <p>여기서만 로컬과 같은 조건({@code seed.from-sql=true})으로 띄운다 — 다른 통합테스트는
 * 기본값 false 라 {@link ExamDataInitializer} 가 만드는 데모 10종을 그대로 쓴다.
 *
 * <p><b>행 수를 박아 둔 이유</b>: 시드를 다시 만들면 이 숫자가 바뀐다. 바뀌는 건 정상이고,
 * 그때 이 테스트가 실패하면서 "시드가 갈렸다"는 사실을 알려 주는 것이 목적이다.
 * 숫자만 고치지 말고 <b>왜 달라졌는지</b> 확인하고 고친다.
 */
// ⚠️ 시드를 쓰는 테스트는 **자기 DB 이름**을 가져야 한다.
// 테스트 전체가 같은 인메모리 DB(testdb)를 ddl-auto=create-drop 으로 공유한다. 컨텍스트가
// 새로 뜰 때마다 표를 지우고 다시 만들기 때문에, 시드를 넣어 둔 컨텍스트의 데이터가
// 다른 테스트가 뜨는 순간 통째로 날아간다(실행 순서에 따라 되다 안 되다 한다 — 실측).
@SpringBootTest(properties = {"seed.from-sql=true", "spring.datasource.url=jdbc:h2:mem:seeddb_local;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class LocalSeedSqlTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private ExamScheduleRepository examScheduleRepository;

    @Autowired
    private NotificationScheduleRepository notificationScheduleRepository;

    @Autowired
    private CertificateDetailRepository certificateDetailRepository;

    @Test
    @DisplayName("시드가 네 표를 모두 채운다")
    void seed_fills_every_table() {
        assertEquals(870, certificateRepository.count(), "시험 마스터");
        assertEquals(2948, examScheduleRepository.count(), "회차 일정");
        assertEquals(14739, notificationScheduleRepository.count(), "알림 예약");
        assertEquals(50, certificateDetailRepository.count(), "응시료·시험과목 등 상세");
    }

    @Test
    @DisplayName("화면이 쓰는 집계가 시드만으로 나온다")
    void stats_come_from_the_seed_alone() throws Exception {
        mockMvc.perform(get("/api/certificates/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExams").value(841))
                .andExpect(jsonPath("$.rolling").value(34));
    }

    @Test
    @DisplayName("시드 뒤에 새 행을 넣어도 PK 가 충돌하지 않는다")
    void identity_counter_is_pushed_past_the_seed() {
        // 시드는 id 를 박아서 넣는다. 자동증가 카운터를 안 밀어 두면 다음 INSERT 가 1번부터 시작해
        // "Unique index or primary key violation" 으로 깨진다 — SqlSeedRunner 가 그걸 밀어 준다.
        Certificate saved = certificateRepository.save(Certificate.builder()
                .name("시드 뒤에 들어온 시험").slug("after-seed-" + System.nanoTime())
                .series(Series.ETC).agency("테스트")
                .build());

        assertTrue(saved.getId() > 870,
                "새 행의 id(" + saved.getId() + ")가 시드가 쓴 번호 뒤여야 한다");
    }

    /**
     * 큐넷 수집은 이름이 아니라 <b>종목코드로 행을 찾는다</b>. 데모가 A 시험에 B 의 코드를 달아 두면
     * 수집이 그 행을 B 로 알고 <b>이름을 B 로 바꿔 버린다</b> — slug 은 안 바뀌므로
     * "이름은 B, slug 은 A" 인 행이 남는다. 2026-09-10 에 실제로 그랬다(산업안전기사에 2290).
     *
     * <p>예전엔 {@code seed/qnet_master.json} 과 대조했다. 시드가 SQL 하나가 된 뒤로는
     * 적재된 DB 가 곧 기준이다.
     */
    @Test
    @DisplayName("데모 시드의 종목코드는 시드에 적재된 그 시험의 코드와 같다")
    void demo_codes_match_the_seeded_master() {
        Map<String, String> codeByName = new HashMap<>();
        Map<String, String> nameByCode = new HashMap<>();
        certificateRepository.findAll().forEach(c -> {
            if (c.getSourceCode() != null && !c.getSourceCode().isBlank()) {
                codeByName.put(c.getName(), c.getSourceCode());
                nameByCode.put(c.getSourceCode(), c.getName());
            }
        });

        List<String> wrong = new ArrayList<>();
        for (DemoDataProvider.DemoCert cert : new DemoDataProvider().demoCertificates()) {
            String expected = codeByName.get(cert.name());
            if (expected == null) {
                continue;   // 시드에 없는 데모 종목 — 대조할 기준이 없다
            }
            if (!expected.equals(cert.sourceCode())) {
                wrong.add("%s 에 %s 를 달아 놨다 — 그건 %s 의 코드다(맞는 코드 %s)".formatted(
                        cert.name(), cert.sourceCode(),
                        nameByCode.getOrDefault(cert.sourceCode(), "(시드에 없는 코드)"), expected));
            }
        }
        assertTrue(wrong.isEmpty(),
                "데모 종목코드가 시드와 어긋난다. 수집이 이 행의 이름을 남의 이름으로 바꾼다:\n  "
                        + String.join("\n  ", wrong));
    }

    /**
     * 목록에서 뺀 시험({@link CertificateLifecycle#EXCLUDED})과 대체된 시험이 성한지.
     *
     * <p>예전엔 {@code certificate_lifecycle.json} 이 <b>이름으로</b> 시험을 찾아 상태를 입혔다 —
     * 한 글자만 달라도 조용히 아무 일도 안 일어나서, 뺐다고 믿은 시험이 그대로 남았다.
     * 이제는 상태가 행에 박혀 들어오므로 그 사고는 구조적으로 없다. 남은 위험은
     * <b>대체 대상이 실제로 있는가</b>와 <b>뺀 시험이 화면에 안 보이는가</b>다.
     */
    @Test
    @DisplayName("대체된 시험은 실제로 있는 시험을 가리키고, 뺀 시험은 공개 수에서 빠진다")
    void lifecycle_rows_point_at_real_exams() {
        Set<String> names = new HashSet<>();
        certificateRepository.findAll().forEach(c -> names.add(c.getName()));

        List<String> dangling = new ArrayList<>();
        long excluded = 0;
        for (Certificate c : certificateRepository.findAll()) {
            if (c.getLifecycle() == CertificateLifecycle.EXCLUDED) {
                excluded++;
            }
            String to = c.getSupersededBy();
            if (to != null && !to.isBlank() && !names.contains(to)) {
                dangling.add(c.getName() + " → " + to);
            }
        }

        assertTrue(dangling.isEmpty(),
                "없는 시험으로 대체됐다고 적혀 있다 — 화면이 빈 링크를 만든다: " + dangling);
        assertTrue(excluded > 0, "뺀 시험이 하나도 없다 — 이 검사가 아무것도 검증하지 않는다");
        assertTrue(excluded < certificateRepository.count(),
                "전부 빠졌다 — 시드가 잘못 만들어졌다");
    }
}
