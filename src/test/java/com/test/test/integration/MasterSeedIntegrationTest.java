package com.test.test.integration;

import com.test.test.exam.config.CertificateMasterInitializer;
import com.test.test.exam.config.RollingAdmissionInitializer;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.Series;
import com.test.test.exam.repository.CertificateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 기동 시 마스터 시드가 <b>이미 있는 시험</b>을 어떻게 다루는지.
 *
 * <p>둘 다 "코드가 안 맞아 시드가 헛도는" 부류의 결함이다: 상시 표시는 코드가 다르면 못 찾았고,
 * 계열은 기존 값이 '기타'로 초기화된 채 마스터가 아는 값을 되돌려 주지 않았다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MasterSeedIntegrationTest {

    @Autowired
    private RollingAdmissionInitializer rollingAdmissionInitializer;

    @Autowired
    private CertificateMasterInitializer certificateMasterInitializer;

    @Autowired
    private CertificateRepository certificateRepository;

    @Test
    @DisplayName("상시 시드는 코드가 안 맞으면 이름(공백 무시)으로 찾는다")
    void rolling_seed_falls_back_to_name_when_code_differs() {
        String code = "ZZ-ROLL-" + UUID.randomUUID().toString().substring(0, 6);
        Certificate cert = certificateRepository.save(Certificate.builder()
                .name("제1종운전면허")   // 시드의 "제1종 운전면허"와 공백만 다르다
                .slug("제1종운전면허-" + code)
                .series(Series.ETC).agency("도로교통공단").sourceCode(code)
                .build());

        rollingAdmissionInitializer.mark();

        assertTrue(certificateRepository.findById(cert.getId()).orElseThrow().isRollingAdmission(),
                "코드가 달라 상시 표시를 못 붙였다 — 이름으로 한 번 더 찾아야 한다");
    }

    @Test
    @DisplayName("기존 시험의 계열이 '기타'면 마스터가 아는 계열로 되돌린다")
    void master_seed_restores_series_when_existing_is_etc() {
        Certificate cert = certificateRepository.findBySlug("정보처리기사").orElseThrow();
        Series expected = cert.getSeries();
        assertNotEquals(Series.ETC, expected, "기준이 될 시험의 계열이 이미 기타다 — 시드가 바뀌었나?");

        cert.updateMeta(cert.getName(), Series.ETC, cert.getAgency(), cert.getCategory());
        certificateRepository.save(cert);

        certificateMasterInitializer.seedMaster();

        assertEquals(expected, certificateRepository.findById(cert.getId()).orElseThrow().getSeries(),
                "마스터가 계열을 알고 있는데 '기타'로 남겨 둔다");
    }
}
