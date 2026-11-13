# data — 돌리면 생기는 것

소스가 아니라 **실행 결과물**만 여기 모인다. 이 README 말고는 전부 git 에 올리지 않는다.

| 폴더 | 무엇 | 없으면 |
|---|---|---|
| `h2/` | 로컬 파일 H2 DB (`exam.mv.db`) | 다음 기동에 새로 만든다 — **첫 기동 9분**(시험일정 2,400여 건 적재) |
| `logs/` | `application.log` · `error.log` (날짜별 보관) | 기동하면 다시 생긴다 |

## 초기화

```
# 서버를 내리고
rm -rf data/h2
```

다시 켜면 시드부터 새로 만든다(= 예전 `ddl-auto: create` 와 같은 상태).
스키마를 크게 바꿔 `update` 가 못 따라갈 때도 이렇게 한다.

## 왜 한 폴더에 모았나

- 루트에 `.localdb/` · `logs/` 가 흩어져 있었다. **"지워도 되는 것"이 한 군데에 있어야** 초기화가 쉽다.
- `build/` 아래에 두지 않는 이유: `./gradlew clean` 이 DB까지 날린다. 데이터는 빌드 산출물이 아니다.

## 운영(Railway)에는 없다

운영 DB 는 Railway MySQL 이고, 로그는 Railway 가 수집한다. 이 폴더는 **로컬 전용**이다.

> 업로드 기능은 이 프로젝트에 아직 없다. 생기면 `data/uploads/` 로 온다
> (운영은 Railway Storage Buckets — `code-convention` 스킬 §5-3).
