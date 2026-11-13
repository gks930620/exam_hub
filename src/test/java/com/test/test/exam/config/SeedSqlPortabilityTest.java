package com.test.test.exam.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 시드 {@code seed_exams.sql} 이 <b>MySQL 에서도</b> 돌 수 있는 모양인지.
 *
 * <p>로컬은 H2, 운영은 MySQL 인데 <b>같은 파일</b>로 뜬다. 그런데 시드를 만드는 도구가 H2 라서,
 * 다시 뜰 때마다 H2 전용 문법이 섞여 들어올 수 있다. 그걸 알아차리는 시점이 <b>배포</b>라면 늦다 —
 * 운영 DB 가 빈 채로 뜨고, 사이트에 시험이 하나도 안 보인다.
 *
 * <p>이 환경에는 도커가 없어 <b>실제 MySQL 로 돌려보지 못한다</b>(2026-11-14). 그래서 실행 대신
 * "MySQL 이 못 읽는 모양"을 글자로 막는다. 실행 검증은 첫 배포 때 해야 한다.
 *
 * <p>막는 것들 — 전부 2026-11-14 에 실제로 섞여 있었거나 섞일 수 있던 것이다:
 * <ul>
 *   <li>{@code U&'\d55c\ad6d'} — H2 가 한글을 뱉는 기본 형식. <b>MySQL 은 이 문법을 모른다.</b></li>
 *   <li>{@code ALTER TABLE ... ALTER COLUMN id RESTART WITH} — H2 전용. MySQL 은 {@code AUTO_INCREMENT =} 다.
 *       그래서 자동증가 카운터는 파일이 아니라 {@code SqlSeedRunner} 가 맞춘다.</li>
 *   <li>역슬래시 — MySQL 은 문자열 안의 {@code \} 를 이스케이프로 읽는다(H2 는 글자로 읽는다).
 *       같은 값이 두 DB 에서 다르게 저장되면 조용히 어긋난다.</li>
 *   <li>{@code CREATE}/{@code DROP} — 스키마는 엔티티(ddl-auto)가 만든다. 시드가 표를 만들면 둘이 싸운다.</li>
 * </ul>
 */
class SeedSqlPortabilityTest {

    private static final String RESOURCE = "seed_exams.sql";

    private String seed() throws IOException {
        ClassPathResource r = new ClassPathResource(RESOURCE);
        assertTrue(r.exists(), RESOURCE + " 이 없다 — 로컬·운영이 데이터 없이 뜬다");
        return new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    /** 주석을 뺀 본문만 본다 — 주석에는 "U&'...' 를 쓰지 않는다" 같은 설명이 들어 있다. */
    private String body() throws IOException {
        List<String> lines = new ArrayList<>();
        for (String line : seed().split("\r?\n")) {
            if (!line.stripLeading().startsWith("--")) {
                lines.add(line);
            }
        }
        return String.join("\n", lines);
    }

    @Test
    @DisplayName("H2 전용 유니코드 이스케이프를 쓰지 않는다 — MySQL 이 U&'...' 를 모른다")
    void no_h2_unicode_literals() throws IOException {
        assertEquals(0, count(body(), "U&'"),
                "H2 가 한글을 U&'\\d55c' 로 뱉었다. scripts/make_seed_sql.cjs 의 디코더가 빠졌는지 보라");
    }

    @Test
    @DisplayName("문자열에 역슬래시가 없다 — MySQL 은 이스케이프로 읽고 H2 는 글자로 읽는다")
    void no_backslashes() throws IOException {
        assertEquals(0, count(body(), "\\"),
                "역슬래시가 든 값은 두 DB 에서 다르게 저장된다");
    }

    @Test
    @DisplayName("자동증가 카운터를 파일에서 건드리지 않는다 — 문법이 DB마다 달라 SqlSeedRunner 가 맞춘다")
    void no_identity_restart_statements() throws IOException {
        assertEquals(0, count(body().toUpperCase(), "ALTER TABLE"),
                "ALTER COLUMN ... RESTART WITH 는 H2 전용이다");
    }

    @Test
    @DisplayName("스키마를 만들지 않는다 — 표는 엔티티(ddl-auto)가 만든다")
    void inserts_only() throws IOException {
        String upper = body().toUpperCase();
        assertEquals(0, count(upper, "CREATE TABLE"), "시드가 표를 만들면 ddl-auto 와 싸운다");
        assertEquals(0, count(upper, "DROP TABLE"), "시드가 표를 지우면 안 된다");
    }

    /**
     * 날짜 리터럴은 {@code DATE '...'} · {@code TIMESTAMP '...'} 만 쓴다 — 둘 다 MySQL 이 읽는다.
     * 다른 접두사(H2 의 {@code X'..'} 같은 것)가 섞이면 운영에서 깨진다.
     */
    @Test
    @DisplayName("날짜·시각 말고 다른 리터럴 접두사가 없다")
    void only_portable_literal_prefixes() throws IOException {
        Matcher m = Pattern.compile("\\b([A-Z]+) '").matcher(body());
        List<String> unexpected = new ArrayList<>();
        while (m.find()) {
            String prefix = m.group(1);
            if (!prefix.equals("DATE") && !prefix.equals("TIMESTAMP") && !unexpected.contains(prefix)) {
                unexpected.add(prefix);
            }
        }
        assertTrue(unexpected.isEmpty(), "MySQL 이 모를 수 있는 리터럴 접두사: " + unexpected);
    }

    @Test
    @DisplayName("시드가 실제로 행을 담고 있다 — 빈 파일이 조용히 통과하지 않게")
    void seed_is_not_empty() throws IOException {
        assertTrue(count(body().toUpperCase(), "INSERT INTO ") >= 4,
                "표 네 개(시험·상세·일정·알림예약)에 INSERT 가 있어야 한다");
    }

    private int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }
}
