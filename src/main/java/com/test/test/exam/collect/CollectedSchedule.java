package com.test.test.exam.collect;

import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.Series;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 수집 소스가 내보내는 정규화 레코드 1건 = 자격증 1종목의 한 회차/구분 일정.
 * (큐넷 API 한 행 ~= 종목코드 + 일정 필드). 소스별 원본 필드는 어댑터에서 이 형태로 변환한다.
 *
 * @param sourceCode      공공 API 종목코드(jmCd) — certificate 자연 키
 * @param certificateName 자격증명
 * @param series          계열
 * @param agency          시행기관
 */
public record CollectedSchedule(
        String sourceCode,
        String certificateName,
        Series series,
        String agency,
        String category,
        int year,
        int round,
        ExamType examType,
        LocalDateTime regStartAt,
        LocalDateTime regEndAt,
        LocalDate examStartDate,
        LocalDate examEndDate,
        LocalDate resultDate,
        String sourceUrl,
        /** 이 값을 어디서 얻었나 — 추정치를 확정처럼 보여주지 않기 위한 것 */
        ScheduleProvenance provenance
) {
    /**
     * 출처를 따로 안 밝히면 <b>시행처에서 읽어온 값</b>으로 본다.
     * 추정치(APPROX)는 반드시 명시해야 한다 — 기본값이 추정이면 실수로 경고가 남발된다.
     */
    public CollectedSchedule(String sourceCode, String certificateName, Series series, String agency,
                             String category, int year, int round, ExamType examType,
                             LocalDateTime regStartAt, LocalDateTime regEndAt,
                             LocalDate examStartDate, LocalDate examEndDate, LocalDate resultDate,
                             String sourceUrl) {
        this(sourceCode, certificateName, series, agency, category, year, round, examType,
                regStartAt, regEndAt, examStartDate, examEndDate, resultDate, sourceUrl,
                ScheduleProvenance.SCRAPED);
    }

    /** 필수 필드(연도/회차/구분/종목코드) 존재 여부 — DR-04-c 스킵 판정. */
    public boolean hasRequiredKeys() {
        return sourceCode != null && !sourceCode.isBlank()
                && certificateName != null && !certificateName.isBlank()
                && examType != null;
    }
}
