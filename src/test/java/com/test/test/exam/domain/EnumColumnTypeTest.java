package com.test.test.exam.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>상태값을 하나 늘렸다고 서버가 안 뜨면 안 된다.</b>
 *
 * <p>2026-09-08 에 {@link CertificateLifecycle} 에 값 하나를 더했더니 기동이 통째로 깨졌다.
 * <pre>Value not permitted for column "('ABOLISHED','ACTIVE','RENAMED','UNVERIFIED')": "EXCLUDED"</pre>
 * 원인: MySQL 모드에서 {@code @Enumerated(STRING)} 이 <b>네이티브 ENUM 타입</b>으로 만들어졌다.
 * 이 프로젝트는 {@code ddl-auto=update} 라 <b>ENUM 의 값 목록은 저절로 안 넓어진다.</b>
 * 그래서 값을 더하는 순간 기존 DB(로컬 파일 H2, 운영 MySQL 모두)에서 앱이 못 뜬다.
 *
 * <p>알림 채널·회차 상태·게시판·로그인 제공자까지 <b>enum 컬럼 13개가 전부</b> 같은 처지였다.
 * 값을 더하는 일은 앞으로도 계속 생긴다 — 그때마다 운영이 멈추면 안 된다.
 *
 * <p>그래서 enum 은 {@code varchar} 로 저장한다. 값 목록이 스키마에 박히지 않으니
 * 값을 늘려도 DDL 을 건드릴 일이 없다.
 */
@SpringBootTest
@ActiveProfiles("test")
class EnumColumnTypeTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("enum 컬럼은 네이티브 ENUM 이 아니다 — 값을 늘려도 기동이 안 깨지게")
    void enums_are_stored_as_varchar_not_native_enum() {
        List<Map<String, Object>> native_ = jdbc.queryForList(
                "SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE DATA_TYPE = 'ENUM'");

        assertTrue(native_.isEmpty(),
                "네이티브 ENUM 컬럼이 있다 — 여기에 값을 하나 더하는 순간 기존 DB 에서 기동이 깨진다: " + native_);
    }

    /** 컬럼 자체가 사라지면 위 테스트는 공허하게 통과한다 — 실제로 검사할 대상이 있는지 확인한다. */
    @Test
    @DisplayName("검사할 enum 컬럼이 실제로 있다")
    void there_are_enum_columns_to_check() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_NAME IN ('CERTIFICATE','EXAM_SCHEDULE','MEMBER','NOTIFICATION_LOG',"
                        + "'NOTIFICATION_SCHEDULE','POST') "
                        + "AND COLUMN_NAME IN ('LIFECYCLE','SERIES','EXAM_TYPE','PROVENANCE','STATUS',"
                        + "'PROVIDER','ROLE','CHANNEL','RESULT','EVENT_TYPE','BOARD')",
                Integer.class);

        assertTrue(count != null && count >= 10,
                "enum 을 담는 컬럼을 못 찾았다 — 이 테스트가 아무것도 검증하지 않는다 (" + count + ")");
    }
}
