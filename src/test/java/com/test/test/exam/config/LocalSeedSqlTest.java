package com.test.test.exam.config;

import com.test.test.exam.domain.Certificate;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로컬 시드 {@code seed_exams.sql} 이 실제로 들어가는지.
 *
 * <p>로컬 DB 는 인메모리라 <b>기동할 때마다 비어 있고</b>, 이 파일 하나가 화면에 보이는 전부를 만든다.
 * 깨지면 로컬이 빈 사이트가 되는데, 깨진 걸 알아차리는 시점이 "서버 켜 보니 아무것도 없다"라면 늦다.
 *
 * <p>그래서 여기서만 로컬과 같은 조건으로 띄운다 — 다른 테스트는 {@code seed.from-sql} 이 false 라
 * 예전처럼 {@link ExamDataInitializer} 가 만든 데모 픽스처를 쓴다(그쪽 기대값을 건드리지 않으려고).
 *
 * <p><b>행 수를 박아 둔 이유</b>: 시드를 다시 만들면 이 숫자가 바뀐다. 바뀌는 건 정상이고,
 * 그때 이 테스트가 실패하면서 "시드가 갈렸다"는 사실을 알려 주는 것이 목적이다.
 * 숫자만 고치지 말고 <b>왜 달라졌는지</b> 확인하고 고친다.
 */
@SpringBootTest(properties = {
        "seed.from-sql=true",
        "spring.sql.init.data-locations=classpath:data.sql,classpath:seed_exams.sql"
})
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
        // "Unique index or primary key violation" 으로 깨진다 — seed_exams.sql 끝의 RESTART WITH 가 막는다.
        Certificate saved = certificateRepository.save(Certificate.builder()
                .name("시드 뒤에 들어온 시험").slug("after-seed-" + System.nanoTime())
                .series(Series.ETC).agency("테스트")
                .build());

        assertTrue(saved.getId() > 870,
                "새 행의 id(" + saved.getId() + ")가 시드가 쓴 번호 뒤여야 한다");
    }
}
