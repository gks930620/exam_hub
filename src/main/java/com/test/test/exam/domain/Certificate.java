package com.test.test.exam.domain;

import com.test.test.exam.common.TimeUtil;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 자격증 마스터 (설계 04 §2-1).
 */
@Entity
@Table(name = "certificate", indexes = {
        @Index(name = "idx_certificate_name", columnList = "name")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /** pSEO URL 키 (예: 정보처리기사). URL 인코딩 한글 또는 로마자 slug */
    @Column(nullable = false, length = 120, unique = true)
    private String slug;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private Series series;

    @Column(nullable = false, length = 50)
    private String agency;

    /** 시험 분류(각종 시험 통합용): 국가기술자격/한국사/어학-영어/IT-데이터 등. 화면·필터·pSEO 그룹핑. */
    @Column(length = 40)
    private String category;

    /** 공공 API 종목코드(jmCd) — diff 매칭 키 */
    @Column(length = 30, unique = true)
    private String sourceCode;

    @Column(nullable = false)
    @Builder.Default
    private Integer favoriteCount = 0;

    /** 지금도 시행되는가. 폐지·개칭된 시험도 기록은 남긴다 — {@link CertificateLifecycle} */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "lifecycle", nullable = false, length = 20)
    @Builder.Default
    private CertificateLifecycle lifecycle = CertificateLifecycle.ACTIVE;

    /**
     * 상시·예약제 시험인가 (AWS·MOS·컴활·운전면허·TOEFL 등).
     * 원하는 날짜에 신청하는 방식이라 <b>"일정"이라는 것이 존재하지 않는다</b> —
     * 회차·접수마감·D-day 전부 해당 없음. 매니저 화면의 "일정 없음"에도 세지 않는다.
     * (사용자 결정 2026-09-01: "상시예약제는 따로 다른 시험들과 구별되어야")
     */
    // DDL 기본값이 필수다 — 로컬 DB 는 update 라 기존 행이 있는 채로 컬럼이 추가되는데,
    // 기본값 없는 NOT NULL 추가는 H2/MySQL 둘 다 거부한다(실제로 겪었다).
    @Column(name = "rolling_admission", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean rollingAdmission = false;

    public boolean isRollingAdmission() {
        return Boolean.TRUE.equals(rollingAdmission);
    }

    public void markRollingAdmission() {
        this.rollingAdmission = true;
    }

    public void clearRollingAdmission() {
        this.rollingAdmission = false;
    }

    /** 이름이 바뀐 경우 새 이름. 사용자에게 "이걸 찾으시나요"를 보여주기 위한 것 */
    @Column(name = "superseded_by", length = 100)
    private String supersededBy;

    /** 왜 이 상태인지 — 매니저가 판단할 근거. 없으면 나중에 아무도 이유를 모른다 */
    @Column(name = "lifecycle_note", length = 300)
    private String lifecycleNote;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = TimeUtil.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.favoriteCount == null) {
            this.favoriteCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = TimeUtil.now();
    }

    // ===== 비즈니스 메서드 =====

    public void incrementFavorite() {
        this.favoriteCount = (this.favoriteCount == null ? 0 : this.favoriteCount) + 1;
    }

    public void decrementFavorite() {
        int next = (this.favoriteCount == null ? 0 : this.favoriteCount) - 1;
        this.favoriteCount = Math.max(next, 0);
    }

    public void updateMeta(String name, Series series, String agency, String category) {
        this.name = name;
        this.series = series;
        this.agency = agency;
        this.category = category;
    }

    /**
     * 공공 API 종목코드를 붙인다. 이게 있어야 시험일정을 <b>이름이 아니라 코드로</b> 붙일 수 있다.
     * 이름 매칭은 "웹디자인기능사 vs 웹디자인개발기능사" 같은 표기 차이에서 반드시 깨진다.
     */
    public void linkSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    /**
     * slug 을 이름에 다시 맞춘다. 수집이 종목코드로 행을 찾아 <b>이름만 고치면</b> slug 이 남의 것으로
     * 남기 때문에, {@code CertificateMasterInitializer.reconcileSlugs()} 가 기동 때 되돌린다.
     */
    public void renameSlug(String slug) {
        this.slug = slug;
    }

    /** 폐지·개칭 기록. 지우지 않고 상태로 남겨 "왜 없어졌는지"를 답할 수 있게 한다. */
    public void markLifecycle(CertificateLifecycle lifecycle, String supersededBy, String note) {
        this.lifecycle = lifecycle;
        this.supersededBy = supersededBy;
        this.lifecycleNote = note;
    }

    public boolean isVisibleToUsers() {
        return lifecycle == null || lifecycle.isVisibleToUsers();
    }

    /**
     * 사용자 화면에서 숨긴 이유 — 관심 목록에 남은 카드가 "왜 D-day 가 없는지" 답할 수 있게.
     * 개칭이면 새 이름을 안내한다. 보이는 시험이면 null.
     */
    public String hiddenReason() {
        if (isVisibleToUsers()) {
            return null;
        }
        if (lifecycle == CertificateLifecycle.RENAMED && supersededBy != null && !supersededBy.isBlank()) {
            return "'" + supersededBy + "'(으)로 이름이 바뀌었습니다. 새 이름으로 다시 등록해 주세요.";
        }
        if (lifecycle == CertificateLifecycle.ABOLISHED) {
            return "폐지된 시험입니다. 더 이상 일정이 없습니다.";
        }
        // 살아 있는 시험이다 — 폐지라고 하면 거짓말이 된다. 우리 사정을 그대로 밝힌다.
        if (lifecycle == CertificateLifecycle.EXCLUDED) {
            return "이 서비스에서 다루지 않는 시험입니다. 시행처가 일정을 공개하는 곳을 찾지 못했습니다.";
        }
        return lifecycle.getLabel();
    }
}
