# 모든시험한번에보기 — 웹 프런트 (React CSR)

이 서비스의 **웹 화면**. **CSR(React + Vite + TypeScript)** 이다.
백엔드는 REST API 전용(Spring Boot)이고, 이 앱이 그 API를 소비한다.

> ⚠️ **개발 방식 확정(2026-07-29)**: 웹 전용 · **무조건 CSR**. SSR(Thymeleaf pSEO)은 제거됨.
> SEO는 지금 신경 쓰지 않음 — 필요해지면 나중에 Next.js(SSR)로 전환. (프로젝트 루트 `.claude/CLAUDE.md` 참고)

## 실행

```bash
cd frontend
npm install
npm run dev        # http://localhost:5101  (/api 요청은 vite proxy 로 8101 백엔드에 전달)
```

백엔드를 먼저 띄워야 데이터가 보인다: 저장소 루트에서 `./gradlew bootRun` (로컬 프로파일, H2 + 데모/시드 자동 적재).

- 프록시 대상 변경: `.env` 의 `VITE_API_TARGET` (기본 `http://localhost:8080`).
- 배포 시 API 절대주소: `.env` 의 `VITE_API_BASE_URL`.

## 구조

```
src/
├── api/
│   ├── client.ts   # fetch 래퍼 + X-Device-Id(기기 식별, localStorage UUID)
│   ├── types.ts    # 백엔드 DTO 타입 (CertificateDtos/FavoriteDtos/MeDtos 대응)
│   └── exams.ts    # 엔드포인트별 호출 함수
├── pages/
│   ├── HomePage.tsx      # 내 시험 D-day 카드  (GET /api/me/favorites)
│   ├── SearchPage.tsx    # 검색+인기, ★관심 토글 (GET /api/certificates, POST/DELETE favorites)
│   ├── DetailPage.tsx    # 자격증 상세+연간일정 (GET /api/certificates/{id})
│   ├── CalendarPage.tsx  # 월간 이벤트        (GET /api/me/calendar)
│   └── SettingsPage.tsx  # 알림 토글          (PUT /api/me/notify-settings)
├── App.tsx        # 라우팅 + 하단 탭
└── main.tsx
```

## 인증
계정/로그인 없음 — **기기 식별(X-Device-Id)**. 최초 접속 시 UUID 를 만들어 `localStorage`에 저장하고 모든 요청 헤더에 붙인다(앱과 동일 규약). 회원가입/OAuth 도입은 v1.1 미확정 사항.

## 남은 것
- 자격증 정보 페이지 slug 라우트(`/api/certificates/by-slug/{slug}`) 연결 — 상세는 현재 id 라우트만 사용.
- 알림 설정 현재값 조회 API(GET) 없어서 기본 전체 ON 에서 시작 → 백엔드에 GET 추가 시 연동.
- 검색 카테고리 필터·뱃지 UI, 로딩/스켈레톤, 에러 토스트 등 UX 다듬기.
- 테스트(vitest) 작성.
