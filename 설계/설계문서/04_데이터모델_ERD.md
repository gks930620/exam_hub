# 04. 데이터 설계 — exam-hub

| 항목 | 내용 |
|---|---|
| 문서 버전 | v1.0 |
| 작성일 | 2026-07-09 |
| DB | 개발 H2(MODE=PostgreSQL) · 운영 PostgreSQL(Railway) · Spring Data JPA `ddl-auto=update` |
| 시간 규칙 | 모든 시각 컬럼 KST 기준(`TimeUtil` 경유), `TIMESTAMP` 저장 |

> **⚠️ 구현 현행화 노트 (2026-07-24)**: 실제 구현은 개발 H2(**MODE=MySQL**, ddl-auto=create) · 운영 **MySQL**(Railway, ddl-auto=update). `exam_schedule.year`는 H2 예약어 회피로 컬럼명 **`exam_year`**로 매핑(엔티티 속성명은 `year` 유지).

---

## 1. ERD

> (ERD 그림은 만들어진 적이 없다. 실제 스키마는 `com.test.test.exam.domain` 엔티티가 기준이고,
> 계정·매니저 부분은 [08_계정과_커뮤니티.md](08_계정과_커뮤니티.md) §2 가 최신이다.)

관계 요약: `certificate 1—N exam_schedule 1—N notification_schedule 1—N notification_log`,
`app_user 1—N user_favorite N—1 certificate`, `app_user 1—N notification_log`. `crawl_log`는 독립 감사 테이블.

## 2. 테이블 정의

### 2-1. certificate — 자격증 마스터

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, auto | |
| name | VARCHAR(100) | NOT NULL | 자격증명 (예: 정보처리기사) |
| slug | VARCHAR(120) | UNIQUE, NOT NULL | pSEO URL 키 (예: `jeongboccheori-gisa` 또는 URL 인코딩 한글) |
| series | VARCHAR(30) | NOT NULL | 계열: TECHNICIAN(기사)/INDUSTRIAL(산업기사)/CRAFTSMAN(기능사)/SERVICE 등 |
| agency | VARCHAR(50) | NOT NULL | 시행기관 (한국산업인력공단 등) |
| source_code | VARCHAR(30) | UNIQUE | 공공 API 종목코드 (jmCd) — diff 매칭 키 |
| favorite_count | INT | DEFAULT 0 | 인기 정렬용 비정규화 카운트 |
| created_at / updated_at | TIMESTAMP | NOT NULL | |

인덱스: `idx_certificate_name(name)`, UNIQUE(slug), UNIQUE(source_code)

### 2-2. exam_schedule — 회차별 시험 일정 (핵심)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, auto | |
| certificate_id | BIGINT | FK, NOT NULL | → certificate |
| year | INT | NOT NULL | 시행 연도 |
| round | INT | NOT NULL | 회차 (1, 2, 3…) |
| exam_type | VARCHAR(20) | NOT NULL | WRITTEN(필기) / PRACTICAL(실기) |
| reg_start_at | TIMESTAMP | | 원서접수 시작 (시각 포함, 보통 10:00) |
| reg_end_at | TIMESTAMP | | 원서접수 마감 (보통 18:00) |
| exam_start_date | DATE | | 시험 시작일 |
| exam_end_date | DATE | | 시험 종료일 (기간 시험 대응, 단일일이면 시작일과 동일) |
| result_date | DATE | | 합격 발표일 |
| status | VARCHAR(20) | NOT NULL | ACTIVE / PENDING_REVIEW(검증 보류) / CANCELED / DONE |
| source_url | VARCHAR(500) | | 원문(큐넷) 링크 |
| source_hash | VARCHAR(64) | | 원본 레코드 해시 — diff 비교용 |
| collected_at | TIMESTAMP | NOT NULL | 마지막 수집 확인 시각 |
| created_at / updated_at | TIMESTAMP | NOT NULL | |

- UNIQUE: `(certificate_id, year, round, exam_type)` — 수집 upsert 자연 키
- 인덱스: `idx_schedule_reg_start(reg_start_at)`, `idx_schedule_exam(exam_start_date)` — 알림 대상·D-day 조회용
- 검증 제약(서비스 레벨): `reg_start_at ≤ reg_end_at < exam_start_date ≤ exam_end_date < result_date` (누락 필드는 검사 생략)

### 2-3. app_user — 기기 단위 사용자 (계정 없음)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, auto | |
| device_id | VARCHAR(64) | UNIQUE, NOT NULL | 앱 최초 실행 시 생성 UUID |
| fcm_token | VARCHAR(255) | | FCM 등록 토큰 (갱신 가능, 만료 시 NULL 처리) |
| plan | VARCHAR(10) | NOT NULL DEFAULT 'FREE' | FREE / PRO |
| notify_reg | BOOLEAN | DEFAULT TRUE | 접수 알림 수신 |
| notify_exam | BOOLEAN | DEFAULT TRUE | 시험 D-7/D-1 알림 수신 |
| notify_change | BOOLEAN | DEFAULT TRUE | 일정 변경 알림 수신 |
| created_at / updated_at | TIMESTAMP | NOT NULL | |

### 2-4. user_favorite — 관심 자격증

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, auto | |
| app_user_id | BIGINT | FK, NOT NULL | → app_user |
| certificate_id | BIGINT | FK, NOT NULL | → certificate |
| created_at | TIMESTAMP | NOT NULL | |

- UNIQUE: `(app_user_id, certificate_id)`
- 무료 3개 제한은 서비스 레벨 검증 (plan=FREE && count≥3 → 409)

### 2-5. notification_schedule — 발송 예약 (이벤트 단위)

일정 1건에서 파생되는 "보낼 시점"들. 수집/변경 시 재계산.

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, auto | |
| exam_schedule_id | BIGINT | FK, NOT NULL | → exam_schedule |
| event_type | VARCHAR(30) | NOT NULL | 아래 이벤트 유형 표 |
| send_at | TIMESTAMP | NOT NULL | 발송 예정 시각 (KST) |
| status | VARCHAR(20) | NOT NULL | PENDING / SENT / CANCELED / SKIPPED(과거시점) |
| created_at / updated_at | TIMESTAMP | NOT NULL | |

- UNIQUE: `(exam_schedule_id, event_type)` · 인덱스: `idx_notif_pending(status, send_at)`

**이벤트 유형 및 발송 시각 규칙**

| event_type | 발송 시각 | 대상 토글 |
|---|---|---|
| REG_OPEN_EVE | 접수 시작 전날 20:00 | notify_reg |
| REG_OPEN_DAY | 접수 시작 당일 09:00 | notify_reg |
| REG_CLOSE_EVE | 접수 마감 전날 20:00 | notify_reg |
| EXAM_D7 | 시험일 7일 전 09:00 | notify_exam |
| EXAM_D1 | 시험일 전날 20:00 | notify_exam |
| SCHEDULE_CHANGED | 변경 감지 즉시 (다음 배치) | notify_change |

### 2-6. notification_log — 발송 이력 (사용자 단위)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, auto | |
| app_user_id | BIGINT | FK, NOT NULL | 수신자 |
| notification_schedule_id | BIGINT | FK, NOT NULL | 어떤 예약의 발송인지 |
| channel | VARCHAR(10) | NOT NULL | FCM / LOG(개발) |
| sent_at | TIMESTAMP | NOT NULL | |
| result | VARCHAR(20) | NOT NULL | SUCCESS / FAILED / TOKEN_EXPIRED |
| error_message | VARCHAR(500) | | 실패 사유 |

- UNIQUE: `(app_user_id, notification_schedule_id)` — **중복 발송 방지 멱등 키 (FR-27)**
- TOKEN_EXPIRED 발생 시 app_user.fcm_token NULL 처리 (정리 배치)

### 2-7. crawl_log — 수집 감사 로그

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, auto | |
| source | VARCHAR(30) | NOT NULL | QNET_API 등 (ScheduleSource 식별자) |
| started_at / finished_at | TIMESTAMP | NOT NULL / NULL | |
| fetched_count | INT | | API 응답 건수 |
| new_count / updated_count / skipped_count | INT | | diff 결과 |
| pending_review_count | INT | | 검증 보류 건수 (DR-04-b) |
| success | BOOLEAN | NOT NULL | |
| error_message | VARCHAR(1000) | | |

## 3. 데이터 흐름 규칙

1. **수집(upsert)**: 자연 키 `(certificate_id, year, round, exam_type)`로 매칭 → `source_hash` 다르면 UPDATE + 변경 필드 판정
2. **알림 재계산**: exam_schedule INSERT/UPDATE 시 notification_schedule 파생 행 재생성 — 과거 시점은 SKIPPED, 기존 PENDING 중 시각 바뀐 것은 UPDATE, 일정 CANCELED 시 예약도 CANCELED
3. **변경 알림**: 접수기간·시험일 변경 감지 시 SCHEDULE_CHANGED 예약 1건 생성(즉시 발송분)
4. **발송 배치(매 5분)**: `status=PENDING AND send_at <= now` → 해당 일정의 자격증을 관심 등록한 사용자 × 토글 필터 → notification_log UNIQUE 제약으로 멱등 발송 → 예약 SENT 처리
5. **보존 정책**: notification_log 180일 · crawl_log 90일 후 삭제 배치 (월 1회)

## 4. 데모 데이터 (`@Profile("!prod")`)

- certificate 10건: 정보처리기사, 전기기사, 산업안전기사, 소방설비기사(전기), 건축기사, 정보처리산업기사, 지게차운전기능사, 한식조리기능사, SQLD, 컴퓨터활용능력1급
- exam_schedule: 각 자격증당 2026년 2~3회차 × 필기/실기 (접수중·접수 예정·완료 상태가 골고루 나오게 날짜 배치)
- app_user 1건 + user_favorite 3건 — 홈 화면 데모 즉시 확인용
