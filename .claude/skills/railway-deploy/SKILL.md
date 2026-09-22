---
name: railway-deploy
description: 이 프로젝트를 Railway에 배포하는 단계별 절차. "Railway에 배포해줘" 또는 "배포해줘" 요청 시 이 절차를 따른다.
---

# Railway 배포

이 저장소는 **컨테이너 하나가 React 화면 + REST API 를 모두 서빙**한다. 프런트를 따로 배포하지
않는다 — `Dockerfile` 이 프런트를 빌드해 `src/main/resources/static` 에 넣고 JAR 하나로 만든다.

Railway 는 저장소에 `Dockerfile` 이 있으면 그걸 쓴다. 별도 설정 파일(railway.json·nixpacks.toml)은
두지 않는다.

## 배포 전 조건 (여기서 막히면 그 다음은 볼 것 없다)

1. **테스트 통과** — `./gradlew test` 와 `cd frontend && npm test`.
   ⚠️ **`bootRun` 이 떠 있으면 `./gradlew test` 를 돌리지 않는다.** devtools 가 재기동하며 시드가 깨진다.
   먼저 서버를 내리고 돌린다.
2. **qa 판정 통과** — 기능 변경이 있었다면.
3. `git push` 가 끝나 있을 것. Railway 는 GitHub 저장소를 본다(`gks930620/exam_hub`).

## 처음 한 번만 (사람이 한다)

| # | 할 일 | 확인 |
|:-:|---|---|
| 1 | Railway 프로젝트 생성 → GitHub 저장소 연결 | 빌드가 `Dockerfile` 을 집었는지 로그에서 확인 |
| 2 | **MySQL 플러그인 추가** | 접속 정보(host·db·user·password)를 받는다 |
| 3 | 아래 환경변수 전부 입력 | 하나라도 빠지면 기동이 중단된다(그게 정상이다) |
| 4 | **카카오·구글 콘솔에 배포 도메인 Redirect URI 추가** | 로컬 것은 지우지 않는다 |

환경변수 전체 목록은 **`진행사항/02_내가_할일.md` §4순위**에 있다. 여기 다시 적지 않는다 —
두 군데에 적으면 한쪽이 반드시 낡는다.

## 배포

`main` 에 push 하면 Railway 가 자동으로 빌드·배포한다. 수동으로 다시 굴리려면 Railway 대시보드의
Redeploy 를 쓴다.

## 배포 후 확인 (이 순서대로)

1. **기동했나** — `GET /healthz` 가 200 + `{"status":"UP"}`.
   기동이 막혔다면 로그 맨 위의 `[RailwayDeploymentValidator]` 줄이 이유를 한국어로 말해 준다.
2. **화면이 뜨나** — 루트(`/`)에서 시험 찾기 화면. 스타일이 없으면 프런트 빌드가 깨진 것이다.
3. **DB 가 MySQL 인가** — 로그에 H2 가 보이면 안 된다. 검증기가 막지만 눈으로도 한 번 본다.
4. **로그인** — 카카오·구글 한 번씩. `KOE205` 가 나오면 `설계/운영/01_카카오_설정.md`.
5. **운영 화면** — `/manager/login` 으로 들어가 `/admin/health` 를 연다.
   - 위(알림): **켜진 발송 수단에 EMAIL 이 있어야 한다.** LOG 뿐이면 아무에게도 안 간다.
   - 아래(수집): 첫 배치(매일 05:00) 전에는 "기록 없음"이 정상이다. 다음 날 다시 본다.
6. **수집이 실제로 도나** — 배포 다음 날 `/admin/health` 를 다시 열어 큐넷·스크래퍼가 정상인지 본다.
7. **고장 경보가 닿나** — `ALERT_EMAIL` 이 채워져 있어야 한다. 매일 06:00 에 수집·알림을 보고
   **문제가 있을 때만** 한 통 온다. 비워 두면 화면을 여는 사람만 고장을 알게 된다.
   조용한 날은 정상이라는 뜻이다.

## 기동이 중단됐을 때

`RailwayDeploymentValidator` 는 **조용히 잘못 도는 것보다 시끄럽게 죽는 쪽**을 택한다.
여섯 가지를 보고, 걸리면 한국어로 이유를 적고 기동을 멈춘다.

| 막는 것 | 안 막으면 |
|---|---|
| JDBC 가 H2/인메모리 | 재배포마다 데이터가 사라진다 |
| JWT 키가 개발용 기본값 | 저장소에 공개된 키라 누구나 토큰을 위조한다 |
| 알림 링크가 localhost | 메일은 가는데 링크가 죽어 접수하러 못 간다 |
| 매니저 비밀번호가 짧다 | 운영 화면이 그대로 뚫린다 |
| H2 콘솔이 켜져 있다 | 임의 SQL 실행 창이 열린다 |
| 발송 채널이 하나도 없다 | 알림이 로그로만 나가는데 **발송 성공으로 기록된다** |

마지막 것이 특히 조용하다 — 발송 체인의 끝 `LogNotificationSender` 가 언제나 성공을 돌려주기
때문에, 통계는 100% 인데 받은 사람은 없다. 그래서 기동 자체를 막는다.

## 자주 겪는 문제

**`Could not resolve "../../design_kits_lets/base.css"`** — 프런트가 디자인 킷을 프런트 폴더 밖에서
읽는데 이미지에 그 폴더가 없다. `Dockerfile` 1단계가 킷을 `frontend` 의 형제 자리에 복사해야 한다.
`DockerBuildContextTest` 가 이걸 지키므로, 이 오류를 봤다면 테스트를 먼저 돌려 본다.

**`authorizationGrantType cannot be null`** — 카카오는 스프링 내장 provider 가 아니라 기본값이 없다.
`...KAKAO_AUTHORIZATION_GRANT_TYPE` 과 `...KAKAO_REDIRECT_URI` 까지 넣어야 한다.

**화면은 뜨는데 스타일이 없다** — 프런트 빌드 산출물이 `static` 에 안 들어갔다.
Docker 2단계의 `COPY --from=web /src/frontend/dist ./src/main/resources/static` 경로를 확인한다.

**새로고침하면 404** — SPA 폴백(`SpaWebConfig`)이 도는지 본다. `/api/**` 는 폴백에서 제외돼 있다.

**포트** — `server.port: ${PORT:8101}`. Railway 가 `PORT` 를 주입한다. 손대지 않는다.

## 되돌리기

Railway 대시보드에서 이전 배포를 Rollback 한다. DB 스키마는 `ddl-auto: update` 라
컬럼 추가는 되돌려도 남는다 — 지우는 마이그레이션은 손으로 해야 한다.
