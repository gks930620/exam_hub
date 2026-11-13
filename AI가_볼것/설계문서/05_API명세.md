# 05. API 설계서 — exam-hub

> ## 🚩 구현 현행화 노트 (2026-09-03)
> 이 문서는 v1.0(앱 + SSR) 시절 계약이다. **아래 본문은 폐기로 읽을 것.** 현행 계약은 코드와 `08_계정과_커뮤니티.md` 가 기준이다.
> - 인증: `X-Device-Id` 는 **폐기**(2026-08-07) → 소셜 로그인 + `Authorization: Bearer` JWT. 매니저는 `POST /api/manager/login`.
> - `PUT /api/me/fcm-token` 없음(앱 미개발). 알림은 이메일.
> - `/cert/{slug}` SSR·sitemap 없음(CSR 전용, SEO 보류). 상세는 `GET /api/certificates/{id}`.
> - 매니저: `/api/admin/overview`(할 일·대기·정상·상시), `/api/admin/schedules`(수기 입력·취소), `/api/admin/data-map`(수집 지도).


| 항목 | 내용 |
|---|---|
| 문서 버전 | v1.0 |
| 작성일 | 2026-07-09 |
| 규약 | REST/JSON · 오류 응답 `{"message": "..."}` 통일 · 날짜 `yyyy-MM-dd`, 시각 `yyyy-MM-dd'T'HH:mm`(KST) |
| 인증 | 헤더 `X-Device-Id: {UUID}` (계정 없음 — 기기 식별). 최초 호출 시 서버가 app_user 자동 생성 |

---

## 1. 엔드포인트 요약

| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|:--:|
| GET | /api/certificates?query= | 자격증 검색 (부분 일치) | — |
| GET | /api/certificates/popular | 인기 자격증 TOP 10 | — |
| GET | /api/certificates/{id} | 자격증 상세 + 연간 일정 | — |
| GET | /api/me/favorites | 내 관심 목록 + D-day 카드 데이터 | O |
| POST | /api/me/favorites | 관심 등록 | O |
| DELETE | /api/me/favorites/{certificateId} | 관심 해제 | O |
| PUT | /api/me/fcm-token | FCM 토큰 등록/갱신 | O |
| PUT | /api/me/notify-settings | 알림 유형 토글 저장 | O |
| GET | /api/me/calendar?year=&month= | 월간 캘린더 이벤트 | O |
| GET | /cert/{slug} | **pSEO 서버 렌더 HTML** (API 아님) | — |
| GET | /cert | pSEO 색인 HTML | — |
| GET | /sitemap.xml | 사이트맵 (자동 생성) | — |

## 2. 상세 명세

### 2-1. GET /api/certificates?query=정보처리

자격증 검색. `query` 2자 미만이면 400.

```json
{
  "items": [
    {
      "id": 1,
      "name": "정보처리기사",
      "slug": "jeongbocheori-gisa",
      "series": "TECHNICIAN",
      "seriesLabel": "기사",
      "agency": "한국산업인력공단",
      "favorited": true
    },
    {
      "id": 6,
      "name": "정보처리산업기사",
      "slug": "jeongbocheori-saneopgisa",
      "series": "INDUSTRIAL",
      "seriesLabel": "산업기사",
      "agency": "한국산업인력공단",
      "favorited": false
    }
  ]
}
```

### 2-2. GET /api/certificates/{id}

상세 + 연간 일정. `year` 쿼리 생략 시 올해.

```json
{
  "id": 1,
  "name": "정보처리기사",
  "agency": "한국산업인력공단",
  "sourceUrl": "https://www.q-net.or.kr/...",
  "collectedAt": "2026-07-09T05:12",
  "favorited": true,
  "nextEvent": {
    "type": "REG_CLOSING",
    "label": "2회 필기 원서접수",
    "dday": 2,
    "at": "2026-07-11T18:00"
  },
  "schedules": [
    {
      "id": 101,
      "year": 2026,
      "round": 2,
      "examType": "WRITTEN",
      "regStartAt": "2026-07-06T10:00",
      "regEndAt": "2026-07-11T18:00",
      "examStartDate": "2026-08-02",
      "examEndDate": "2026-08-02",
      "resultDate": "2026-08-26",
      "status": "ACTIVE"
    },
    {
      "id": 102,
      "year": 2026,
      "round": 2,
      "examType": "PRACTICAL",
      "regStartAt": "2026-09-07T10:00",
      "regEndAt": "2026-09-10T18:00",
      "examStartDate": "2026-10-17",
      "examEndDate": "2026-11-01",
      "resultDate": "2026-11-25",
      "status": "ACTIVE"
    }
  ]
}
```

### 2-3. GET /api/me/favorites — 홈 D-day 카드

정렬 규칙(접수중 → 접수 예정 → 시험 예정)이 서버에서 적용된 상태로 반환.

```json
{
  "plan": "FREE",
  "limit": 3,
  "items": [
    {
      "certificateId": 1,
      "name": "정보처리기사",
      "badge": "REG_OPEN",
      "badgeLabel": "접수중",
      "eventLabel": "2회 필기 접수 마감",
      "eventAt": "2026-07-11T18:00",
      "dday": 2
    },
    {
      "certificateId": 2,
      "name": "전기기사",
      "badge": "EXAM_UPCOMING",
      "badgeLabel": "시험 예정",
      "eventLabel": "2회 실기 시험",
      "eventAt": "2026-08-19T00:00",
      "dday": 41
    }
  ]
}
```

### 2-4. POST /api/me/favorites

```json
// 요청
{ "certificateId": 3 }

// 201 응답
{ "certificateId": 3, "favoriteCount": 3 }

// 409 — 무료 한도 초과
{ "message": "무료 플랜은 관심 자격증을 3개까지 등록할 수 있어요." }
```

### 2-5. PUT /api/me/fcm-token

```json
// 요청
{ "fcmToken": "fXk3...:APA91b..." }

// 200 응답
{ "message": "등록되었습니다." }
```

### 2-6. PUT /api/me/notify-settings

```json
// 요청
{ "notifyReg": true, "notifyExam": true, "notifyChange": false }

// 200 응답 — 저장된 값 반환
{ "notifyReg": true, "notifyExam": true, "notifyChange": false }
```

### 2-7. GET /api/me/calendar?year=2026&month=7

```json
{
  "events": [
    { "date": "2026-07-06", "type": "REG_START", "certificateId": 1, "name": "정보처리기사", "label": "2회 필기 접수 시작" },
    { "date": "2026-07-11", "type": "REG_END",   "certificateId": 1, "name": "정보처리기사", "label": "2회 필기 접수 마감" }
  ]
}
```

### 2-8. 공통 오류

| 코드 | 상황 | 본문 |
|---|---|---|
| 400 | 검증 실패 (query 2자 미만 등) | `{"message": "검색어는 2자 이상 입력하세요."}` |
| 404 | 자격증/일정 없음 | `{"message": "자격증을 찾을 수 없습니다."}` |
| 409 | 무료 한도·중복 등록 | `{"message": "..."}` |

## 3. pSEO 라우트 (서버 렌더)

| 경로 | 렌더 | 비고 |
|---|---|---|
| /cert/{slug} | Thymeleaf(또는 유사) HTML — H1, 일정 `<table>`, D-day, 앱 CTA, JSON-LD(v2) | 캐시: 서버 메모리 10분 (일 1회 수집이라 충분) |
| /cert | 계열별 색인 | 내부링크 허브 |
| /sitemap.xml | certificate 전건 URL + lastmod=updated_at | 수집 배치 후 자동 갱신 |

- slug 404 시: 유사 자격증 검색 결과 페이지로 소프트 랜딩 (404 상태 코드 유지)
- Flutter 웹 도구 화면과 분리: **검색 유입 페이지는 전부 서버 HTML** (기술 표준 A/C 원칙)

## 4. 수집 배치 (내부 — 외부 API 아님)

| 항목 | 내용 |
|---|---|
| 트리거 | `@Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")` + 임박 종목 재확인 `0 0 17 * * *` |
| 흐름 | ① `ScheduleSource.fetch()` (큐넷 공공 API 호출, 페이지네이션) → ② 응답을 정규화 DTO로 변환 → ③ 자연 키 매칭 upsert + source_hash diff → ④ 검증(날짜 순서/30일 이상 이동 시 PENDING_REVIEW) → ⑤ notification_schedule 재계산 → ⑥ crawl_log 기록 |
| 발송 배치 | `@Scheduled(fixedDelay = 300_000)` — PENDING & send_at 도래 예약 → 관심 사용자 조회 → FCM 발송 → notification_log 기록(UNIQUE 멱등) |
| 관리 확인 | PENDING_REVIEW 건은 로그로 노출, MVP는 DB에서 수동 승인 (관리자 화면 v2) |
| 장애 | 2회 연속 실패 시 관리자 알림. 수집 실패해도 조회·알림은 기존 데이터로 정상 동작 |

### FCM 페이로드 예시

```json
{
  "notification": {
    "title": "정보처리기사 접수가 내일 시작돼요",
    "body": "07.06(월) 10:00 접수 시작 · 마감 07.11(토) 18:00"
  },
  "data": {
    "type": "REG_OPEN_EVE",
    "certificateId": "1",
    "examScheduleId": "101",
    "route": "/certificate/1"
  }
}
```
