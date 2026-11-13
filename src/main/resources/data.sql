-- ─────────────────────────────────────────────────────────────
-- 로컬 개발용 시드 (SQL).
--
-- ⚠️ 로컬(local 프로파일)에서만 돈다. 운영(prod)은 application.yml 에서 mode: never 로 못 박았다.
--    운영 매니저는 직접 INSERT 한다
--    (절차: 사용자_문서/00_할일.md).
--
-- 시험 데이터는 여기 없다 — 로컬은 seed_exams.sql, 운영은 자바 로더(CertificateMasterInitializer)가 담당한다.
-- 여기엔 "사람 손이 필요한 계정"만 둔다. 테스트도 이 파일만 읽는다(seed_exams.sql 은 로컬 전용).
-- ─────────────────────────────────────────────────────────────

-- 매니저(운영자) 계정 — 로그인: manager / pass1234  (http://localhost:8101/manager/login)
--
-- 비밀번호는 BCrypt 해시로 넣는다. 평문을 넣으면 로그인이 안 된다(로그인이 해시와 비교한다).
-- 다른 값으로 바꾸려면:  ./gradlew managerHash -Ppassword=새비밀번호
-- provider='LOCAL' 이라 소셜 계정과 섞이지 않고, role='ADMIN' 이라야 /admin 이 열린다.
-- ⚠️ 이 파일은 <b>기동할 때마다</b> 돈다. 로컬 DB 는 인메모리라 매번 비어 있지만,
--    테스트처럼 컨텍스트를 재사용하는 경우까지 생각해 없을 때만 넣는다.
INSERT INTO member
  (provider, provider_id, password_hash, nickname, role, status,
   notify_reg, notify_exam, notify_change, created_at, updated_at)
SELECT
  'LOCAL', 'manager', '{bcrypt}$2a$10$gDoYeGZCnaegt96mMFKR6.qPEv8PF6hIQwcJHkBd4IMktc4ud0DGu',
  '로컬매니저', 'ADMIN', 'ACTIVE', true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
-- 닉네임이 '로컬매니저' 인 이유: 여기에도 유니크 제약이 있는데, MANAGER_USERNAME 으로 만든
-- 계정이 '매니저' 를 먼저 쓰고 있으면 겹쳐서 기동이 통째로 깨진다(실제로 겪었다).
-- 이름을 갈라 두면 두 계정이 나란히 있을 수 있다.
WHERE NOT EXISTS (
  SELECT 1 FROM member WHERE provider = 'LOCAL' AND provider_id = 'manager');
