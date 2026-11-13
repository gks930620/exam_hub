/**
 * 로컬 시드 `src/main/resources/seed_exams.sql` 를 다시 만든다.
 *
 * 로컬 DB 는 인메모리라 기동할 때마다 비어 있고, 그 파일 하나가 화면에 보이는 전부를 만든다.
 * 데이터가 낡으면(= 지난 일정만 남으면) 여기서 다시 뜬다.
 *
 * ── 쓰는 법 ───────────────────────────────────────────────────────────────
 *   1) 실제 수집으로 채운 파일 DB 를 하나 만든다
 *        ./gradlew bootRun --no-daemon --args="\
 *            --seed.from-sql=false --collect.on-startup=true \
 *            --spring.datasource.url=jdbc:h2:file:./build/seedgen/exam;MODE=MySQL \
 *            --spring.jpa.hibernate.ddl-auto=create"
 *      ⚠️ 큐넷 API 613콜 + 시행처 사이트 실호출이 나간다. 하루 한 번까지. 서버는 다 받은 뒤 내린다.
 *
 *   2) 덤프 → 시드로 변환
 *        node scripts/make_seed_sql.cjs ./build/seedgen/exam
 *
 *   3) 테스트가 행 수를 박아 두고 있다 — 바뀐 숫자로 고친다
 *        src/test/java/com/test/test/exam/config/LocalSeedSqlTest.java
 *
 * ── 안 넣는 것 ────────────────────────────────────────────────────────────
 *   member · post · comment · user_favorite · crawl_log · notification_log.
 *   개인 계정(소셜 로그인 이메일)과 QA 가 만든 글이 저장소에 들어가기 때문이다.
 *   매니저 계정은 `data.sql` 이 따로 넣는다.
 */
const { execFileSync } = require("child_process");
const fs = require("fs");
const path = require("path");
const os = require("os");

const BATCH = 100;
const TABLES = ["CERTIFICATE", "CERTIFICATE_DETAIL", "EXAM_SCHEDULE", "NOTIFICATION_SCHEDULE"]; // 부모 → 자식(FK 순서)
const OUT = path.join(__dirname, "..", "src", "main", "resources", "seed_exams.sql");

function fail(msg) {
  console.error("✗ " + msg);
  process.exit(1);
}

// ── H2 jar 과 java 를 찾는다 ────────────────────────────────────────────────
function findH2Jar() {
  const root = path.join(os.homedir(), ".gradle", "caches", "modules-2", "files-2.1", "com.h2database", "h2");
  if (!fs.existsSync(root)) fail("H2 jar 을 못 찾았다 — ./gradlew build 를 한 번 돌려 캐시를 채운다");
  for (const ver of fs.readdirSync(root)) {
    for (const hash of fs.readdirSync(path.join(root, ver))) {
      const dir = path.join(root, ver, hash);
      for (const f of fs.readdirSync(dir)) {
        if (f.endsWith(".jar") && !f.includes("sources")) return path.join(dir, f);
      }
    }
  }
  fail("H2 jar 을 못 찾았다");
}

function findJava() {
  if (process.env.JAVA_HOME) {
    const j = path.join(process.env.JAVA_HOME, "bin", "java" + (process.platform === "win32" ? ".exe" : ""));
    if (fs.existsSync(j)) return j;
  }
  return "java"; // PATH 에 있기를 기대
}

// ── 1) H2 SCRIPT 로 덤프 ───────────────────────────────────────────────────
const dbPath = process.argv[2];
if (!dbPath) fail("쓰는 법: node scripts/make_seed_sql.cjs <H2 파일 DB 경로(확장자 없이)>");
if (!fs.existsSync(dbPath + ".mv.db")) fail("그런 DB 가 없다: " + dbPath + ".mv.db");

const dump = path.join(os.tmpdir(), "exam_hub_seed_dump.sql");
const url = "jdbc:h2:file:" + path.resolve(dbPath).replace(/\\/g, "/") + ";MODE=MySQL";

console.log("덤프 중: " + url);
execFileSync(findJava(), ["-cp", findH2Jar(), "org.h2.tools.Shell",
  "-url", url, "-user", "sa", "-password", "",
  "-sql", `SCRIPT SIMPLE COLUMNS NOSETTINGS TO '${dump.replace(/\\/g, "/")}' CHARSET 'UTF-8' TABLE ${TABLES.join(", ")}`],
  { stdio: "inherit" });

// ── 2) 문장 단위로 쪼갠다 (작은따옴표 안의 ; 와 줄바꿈 때문에 줄 단위로는 못 자른다) ──
function statements(s) {
  const out = [];
  let buf = "", q = false;
  for (let i = 0; i < s.length; i++) {
    const c = s[i];
    if (c === "'") {
      if (q && s[i + 1] === "'") { buf += "''"; i++; continue; }
      q = !q; buf += c; continue;
    }
    if (c === ";" && !q) { out.push(buf.trim()); buf = ""; continue; }
    buf += c;
  }
  if (buf.trim()) out.push(buf.trim());
  return out;
}

/**
 * H2 는 아스키가 아닌 글자를 `U&'\d55c\ad6d'` 꼴로 뱉는다. **MySQL 은 이 문법을 모른다.**
 * 운영(MySQL)에서도 같은 파일이 돌아야 하므로 평문 UTF-8 문자열로 되돌린다.
 * 덤으로 한 글자가 6바이트에서 3바이트로 줄어 파일이 절반 아래로 작아진다.
 */
function decodeUnicodeLiterals(sql) {
  const ESC_SUPPLEMENTARY = /\\\+([0-9a-fA-F]{6})/g;   // \+01F600
  const ESC_BMP = /\\([0-9a-fA-F]{4})/g;               // \d55c
  // 리터럴은 "따옴표가 하나 더 따라오지 않는 따옴표"에서 끝난다(안쪽 따옴표는 '' 로 겹쳐 쓴다).
  // 탐욕적으로 쓰면 파일 끝까지 한 덩어리로 잡아먹는다.
  return sql.replace(/U&'((?:[^']|'')*?)'(?!')/g, (_, body) =>
    "'" + body
      .replace(ESC_SUPPLEMENTARY, (_, h) => String.fromCodePoint(parseInt(h, 16)))
      .replace(ESC_BMP, (_, h) => String.fromCharCode(parseInt(h, 16)))
      + "'");
}

const RE = /^INSERT INTO "PUBLIC"\."([A-Z_]+)"\(([^)]*)\) VALUES\s*([\s\S]+)$/;
const byTable = new Map();
for (const st of statements(decodeUnicodeLiterals(fs.readFileSync(dump, "utf8")))) {
  if (!st.startsWith("INSERT INTO")) continue;   // CREATE/ALTER 는 버린다 — 스키마는 엔티티가 만든다
  const m = st.match(RE);
  if (!m) fail("파싱 실패: " + st.slice(0, 120));
  const [, table, cols, values] = m;
  const norm = cols.replace(/"/g, "").toLowerCase();
  if (!byTable.has(table)) byTable.set(table, { cols: norm, rows: [] });
  if (byTable.get(table).cols !== norm) fail("한 표 안에서 컬럼 목록이 섞였다: " + table);
  byTable.get(table).rows.push(values.trim());
}

// ── 3) 여러 행을 한 문장으로 묶어 쓴다 (행마다 컬럼 목록을 반복하면 파일이 두 배가 된다) ──
const today = new Date().toISOString().slice(0, 10);
const parts = [`-- ─────────────────────────────────────────────────────────────
-- 로컬 시험 데이터 시드 — scripts/make_seed_sql.cjs 가 만든 파일이다.
-- 손으로 고치지 않는다. 다시 만드는 법은 그 스크립트 주석에 있다. (생성: ${today})
--
-- 로컬 DB 는 인메모리라 기동할 때마다 비어 있다. 이 파일이 그걸 채운다.
-- 예전엔 자바 로더가 JSON 을 읽어 수집 파이프라인(DiffService)을 태워 넣었는데, 레코드 1건당
-- 쿼리가 14.5개씩 나가 2,400건에 75초가 걸렸다(2026-11-13 실측). 여기서는 INSERT 만 한다.
--
-- 회원·게시글은 없다 — 개인 계정과 QA 가 만든 글이 섞이기 때문이다.
-- 매니저 계정은 data.sql 이 넣는다.
--
-- H2(로컬)와 MySQL(운영) **둘 다에서** 돌아야 한다. 그래서 H2 전용 문법을 쓰지 않는다 —
-- 한글은 U&'...' 이스케이프가 아니라 평문으로 넣고, 자동증가 카운터는 SqlSeedRunner 가 맞춘다.
-- ─────────────────────────────────────────────────────────────`];

let total = 0;
for (const table of TABLES) {
  const t = byTable.get(table);
  if (!t || t.rows.length === 0) continue;
  total += t.rows.length;
  parts.push(`\n-- ${table.toLowerCase()} — ${t.rows.length}행`);
  for (let i = 0; i < t.rows.length; i += BATCH) {
    parts.push(`INSERT INTO ${table.toLowerCase()} (${t.cols}) VALUES\n${t.rows.slice(i, i + BATCH).join(",\n")};`);
  }
}
if (total === 0) fail("넣을 행이 하나도 없다 — DB 가 비어 있는 것 같다");

// 자동증가 카운터를 미는 구문은 여기 넣지 않는다 — H2 와 MySQL 의 문법이 달라서
// (ALTER COLUMN ... RESTART WITH  vs  AUTO_INCREMENT =) SqlSeedRunner 가 붙은 DB 를 보고 처리한다.

fs.writeFileSync(OUT, parts.join("\n") + "\n", "utf8");
fs.unlinkSync(dump);

for (const table of TABLES) {
  console.log("  " + table.toLowerCase().padEnd(24) + (byTable.get(table)?.rows.length ?? 0) + "행");
}
console.log(`합계 ${total}행 → ${OUT} (${(fs.statSync(OUT).size / 1024 / 1024).toFixed(1)} MB)`);
console.log("※ LocalSeedSqlTest 의 행 수도 같이 고친다.");
