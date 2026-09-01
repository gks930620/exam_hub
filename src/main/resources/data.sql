-- ─────────────────────────────────────────────────────────────
-- 로컬 개발용 시드 (SQL).
--
-- ⚠️ 로컬에서만 돈다. spring.sql.init.mode=embedded 라 H2(인메모리)일 때만 실행되고,
--    운영 MySQL 에서는 실행되지 않는다. 운영 매니저는 직접 INSERT 한다
--    (절차: 진행사항/02_내가_할일.md 4순위).
--
-- 시험 데이터는 여기 없다 — 자바 로더(CertificateMasterInitializer 등)가 담당한다.
-- 여기엔 "사람 손이 필요한 계정"만 둔다.
-- ─────────────────────────────────────────────────────────────

-- 매니저(운영자) 계정 — 로그인: manager / pass1234  (http://localhost:8081/manager/login)
--
-- 비밀번호는 BCrypt 해시로 넣는다. 평문을 넣으면 로그인이 안 된다(로그인이 해시와 비교한다).
-- 다른 값으로 바꾸려면:  ./gradlew managerHash -Ppassword=새비밀번호
-- provider='LOCAL' 이라 소셜 계정과 섞이지 않고, role='ADMIN' 이라야 /admin 이 열린다.
-- ⚠️ 이 파일은 <b>기동할 때마다</b> 돈다. 로컬 DB 가 파일이 된 뒤로는 계정이 이미 있는 채로
--    다시 실행되므로, 그냥 INSERT 하면 두 번째 기동부터 중복 키로 기동이 깨진다.
--    그래서 없을 때만 넣는다.
INSERT INTO member
  (provider, provider_id, password_hash, nickname, role, status,
   notify_reg, notify_exam, notify_change, created_at, updated_at)
SELECT
  'LOCAL', 'manager', '{bcrypt}$2a$10$gDoYeGZCnaegt96mMFKR6.qPEv8PF6hIQwcJHkBd4IMktc4ud0DGu',
  '매니저', 'ADMIN', 'ACTIVE', true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
  SELECT 1 FROM member WHERE provider = 'LOCAL' AND provider_id = 'manager');
