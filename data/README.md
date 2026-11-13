# data — 돌리면 생기는 것

소스가 아니라 **실행 결과물**만 여기 모인다. 이 README 말고는 전부 git 에 올리지 않는다.

| 폴더 | 무엇 | 없으면 |
|---|---|---|
| `logs/` | `application.log` · `error.log` (날짜별 보관) | 기동하면 다시 생긴다 |

통째로 지워도 된다. 기동하면 다시 생긴다.

## DB 는 여기 없다

로컬 DB 는 **인메모리**다(`jdbc:h2:mem:examhub`). 서버를 끄면 사라지고, 켜면
`src/main/resources/seed_exams.sql` 이 다시 채운다 — **초기화할 것이 없다.**

> 한동안 여기 `h2/` 폴더에 파일 DB 가 있었다(2026-09-01 ~ 11-13).
> 적재가 느려서 파일로 피했던 건데, 느린 원인이 INSERT 가 아니라 수집 파이프라인과
> SQL 로깅이었다. 그걸 고치니 전체 기동이 23초가 되어 인메모리로 돌아왔다.

## 운영(Railway)에는 없다

운영 DB 는 Railway MySQL 이고, 로그는 Railway 가 수집한다. 이 폴더는 **로컬 전용**이다.

> 업로드 기능은 이 프로젝트에 아직 없다. 생기면 `data/uploads/` 로 온다
> (운영은 Railway Storage Buckets — `code-convention` 스킬 §5-3).
