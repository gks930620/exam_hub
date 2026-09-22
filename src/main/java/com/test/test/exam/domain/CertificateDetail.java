package com.test.test.exam.domain;

import com.test.test.exam.common.TimeUtil;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 시험 하나의 <b>날짜가 아닌 정보</b> — 응시료·시험과목·검정방법·합격기준.
 *
 * <p>그전에는 상세 화면에 회차 일정표밖에 없었다. 취준생이 "이 시험 볼까"를 여기서 못 정하고
 * 결국 큐넷으로 나갔다 — 관심 등록까지 오는 길의 가장 큰 누수였다.
 *
 * <p><b>원문과 조각을 같이 들고 있다.</b> 큐넷은 이 정보를 구조화해서 주지 않고
 * {@code contents} 한 칸에 덩어리로 준다({@code QnetExamInfoParser} 참고). 613종의 표기 편차를
 * 다 맞출 수 있다고 믿지 않으므로, <b>못 가른 것은 비워 두고 원문을 지킨다</b> —
 * 화면은 조각이 없으면 원문을 그대로 보여주면 되고, 억지로 채운 값은 틀린 값이다.
 *
 * <p><b>응시자격은 없다.</b> 큐넷 API 가 주지 않는다. 가장 가까운 것이 관련학과라 그것만 담되
 * 응시자격이라고 부르지 않는다 — 다른 말을 같은 말로 쓰면 사용자가 속는다.
 *
 * <p>비큐넷 시험은 이 정보를 어디서 얻을지 아직 조사된 바가 없다(설계/시험데이터 06 문서 전무).
 * 그래서 이 표는 <b>비어 있는 것이 정상</b>인 시험이 많다.
 */
@Entity
@Table(name = "certificate_detail",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_certificate_detail", columnNames = "certificate_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CertificateDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "certificate_id", nullable = false)
    private Certificate certificate;

    /** 필기(큐넷은 1차라 부른다) 응시료. 못 읽었으면 null — <b>0 이 아니다</b>(0 은 무료로 보인다) */
    @Column(name = "fee_written")
    private Integer feeWritten;

    /** 실기(2차) 응시료. 실기가 없는 시험이면 null */
    @Column(name = "fee_practical")
    private Integer feePractical;

    /** 응시료 원문 — 못 갈랐을 때 화면이 이걸 그대로 보여준다 */
    @Column(name = "fee_raw", length = 500)
    private String feeRaw;

    /** 관련학과. <b>응시자격이 아니다</b> */
    @Column(name = "related_major", length = 1000)
    private String relatedMajor;

    @Column(name = "subjects", length = 2000)
    private String subjects;

    /** 검정방법 — 객관식인지 필답형인지, 몇 문항 몇 분인지 */
    @Column(name = "exam_method", length = 2000)
    private String examMethod;

    @Column(name = "pass_standard", length = 2000)
    private String passStandard;

    /** 취득방법 원문 전체. 조각을 못 가른 시험은 이것만 남는다 */
    @Column(name = "acquisition_raw", length = 4000)
    private String acquisitionRaw;

    /** 출제경향 원문. 길고 지저분해서 화면은 접어 둔다 */
    @Column(name = "trend_raw", length = 4000)
    private String trendRaw;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        this.collectedAt = TimeUtil.now();
    }

    /** 다시 받아왔을 때 갈아 끼운다 — 행을 지우고 새로 만들면 id 가 흔들린다. */
    public void apply(Integer feeWritten, Integer feePractical, String feeRaw,
                      String relatedMajor, String subjects, String examMethod,
                      String passStandard, String acquisitionRaw, String trendRaw) {
        this.feeWritten = feeWritten;
        this.feePractical = feePractical;
        this.feeRaw = feeRaw;
        this.relatedMajor = relatedMajor;
        this.subjects = subjects;
        this.examMethod = examMethod;
        this.passStandard = passStandard;
        this.acquisitionRaw = acquisitionRaw;
        this.trendRaw = trendRaw;
    }

    /** 사용자에게 보여줄 것이 하나라도 있나 — 전부 비었으면 화면에 칸을 만들지 않는다. */
    public boolean hasAnything() {
        return feeWritten != null || feePractical != null
                || notBlank(feeRaw) || notBlank(subjects) || notBlank(examMethod)
                || notBlank(passStandard) || notBlank(relatedMajor) || notBlank(acquisitionRaw);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
