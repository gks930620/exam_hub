# Halo 디자인 시스템 — 이식 가이드

> **이 폴더를 통째로 다른 프로젝트에 복사한 뒤, 그 프로젝트의 Claude Code 에게 이 문서를 읽히면 된다.**
> 원본 프로젝트(dev-toolbox) 없이도 이 폴더만으로 완결된다.
>
> 시안 10개를 3라운드로 검토해 확정한 디자인이다(2026-08-05). 아래 §7 에 **탈락한 방향**도 적어 뒀다 —
> 그게 이 디자인이 무엇이 *아닌지*를 알려 주기 때문에, 새 화면을 만들 때 오히려 더 유용하다.

## 폴더에 든 것

| 파일 | 무엇 | 필수? |
|---|---|---|
| `HALO-디자인-가이드.md` | 이 문서. 원칙·규칙·적용 절차 | **필수** |
| `halo-tokens.css` | 색·그림자·둥글기 토큰 (라이트 + 다크) | **필수 — 그대로 복사** |
| `halo-components.css` | 기본 컴포넌트 (버튼·카드·입력·표·헤더·사이드바 등) | 권장 — 필요한 것만 골라 써도 됨 |
| `halo-미리보기.html` | 브라우저로 열면 전 컴포넌트가 보이는 데모 | 권장 — **먼저 열어 볼 것** |

**시작하는 법**: `halo-미리보기.html` 을 브라우저로 연다. 우상단 버튼으로 다크 모드도 확인한다.
그 화면이 목표물이다. 그다음 이 문서 §3(적용 절차)로 간다.

---

## 1. 한 줄 요약

> **화려함을 한 곳에 모은다.**

전체를 예쁘게 만들려 하지 않는다. 화면의 **10% 정도만** 화려하게 하고 나머지는 철저히 조용하게 둔다.
그러면 그 10%가 훨씬 강해 보이고, 90%는 읽기 편해진다.

이게 전부다. 아래 규칙은 전부 이 한 문장을 지키는 방법일 뿐이다.

---

## 2. 네 가지 원칙 (이것만 지키면 Halo 다)

### ① 읽는 면은 불투명하게

카드·패널·사이드바·본문 — **눈이 머무는 곳은 전부 불투명 흰색**(`--surface`)이다.
여기에 그라디언트·유리·반투명을 쓰지 않는다.

```css
.card { background: var(--surface); }   /* ✅ */
.card { background: var(--grad); }      /* ❌ 읽는 면에 그라디언트 */
.card { background: rgba(255,255,255,.7); backdrop-filter: blur(10px); } /* ❌ 유리 */
```

> **왜**: 반투명 카드 위에 글을 얹으면 배경이 비쳐 대비가 매 순간 달라진다. 예뻐 보이지만 읽기 나쁘다.

### ② 그라디언트는 초점에만

`--grad` 를 쓸 수 있는 곳은 **정해져 있다**:

| 쓴다 ✅ | 안 쓴다 ❌ |
|---|---|
| 히어로 블록 (페이지당 **1개**) | 카드 배경 |
| 주 버튼 (`.btn.primary`, 화면당 **1~2개**) | 패널·사이드바 배경 |
| 사이드바의 **활성** 항목 하나 | 비활성 목록 항목 |
| 아이콘 배지 (카드 아이콘·페이지 아바타) | 본문 텍스트 |
| **선택된** 칩 | 표 헤더 |
| 강조할 숫자 카드 **하나** | 모든 숫자 카드 |

> **판단 기준**: "이 화면에서 그라디언트가 몇 군데 보이나?" 세어 본다. **3~5개면 적당, 8개 넘으면 실패다.**

### ③ 유리(blur)는 헤더에만

`backdrop-filter` 는 **헤더 한 곳**에서만 쓴다. 콘텐츠가 실제로 뒤로 스크롤돼 지나가는 유일한 자리라서다.

사이드바는 고정돼 있고 뒤로 아무것도 안 지나가므로 유리로 만들 이유가 없다 → 불투명 카드로 띄운다.

### ④ 카드 윗변에 1px 그라디언트 선

Halo 의 서명이다. 평소엔 `opacity: .16` 으로 거의 안 보이다가 **hover 시 `.85`** 로 또렷해진다.

```css
.card::before {
  content: ""; position: absolute; left: 16px; right: 16px; top: 0; height: 2px;
  border-radius: 0 0 3px 3px;
  background: var(--grad); opacity: .16; transition: opacity .2s;
}
.card:hover::before { opacity: .85; }
```

> **좌우를 16px 씩 띄우는 게 핵심이다.** `left:0; right:0` 으로 끝까지 채우면 촌스러워진다.

---

## 3. 적용 절차 (다른 프로젝트에서)

### 3-1. 파일 두 개를 넣는다

```
프로젝트/
  src/styles/
    halo-tokens.css      ← 그대로 복사 (수정 금지에 가깝게)
    halo-components.css  ← 복사 후 필요한 것만 남겨도 됨
```

진입점에서 **토큰을 먼저** 불러온다. 순서가 바뀌면 변수가 없는 상태로 컴포넌트가 로드된다.

```js
// React/Vue/Svelte — main.jsx, main.ts 등
import "./styles/halo-tokens.css";
import "./styles/halo-components.css";
```

```html
<!-- 순수 HTML -->
<link rel="stylesheet" href="/styles/halo-tokens.css" />
<link rel="stylesheet" href="/styles/halo-components.css" />
```

### 3-2. 다크 모드를 연결한다

`<html>` 의 `data-theme` 속성만 바꾸면 토큰이 전부 따라온다. 컴포넌트 CSS 는 손댈 필요 없다.

```js
// 최소 구현
function applyTheme(setting) {          // "light" | "dark" | "system"
  const dark = setting === "dark" ||
    (setting === "system" && matchMedia("(prefers-color-scheme: dark)").matches);
  document.documentElement.setAttribute("data-theme", dark ? "dark" : "light");
  document.documentElement.style.colorScheme = dark ? "dark" : "light"; // 스크롤바·폼도 맞춤
}
applyTheme(localStorage.getItem("theme") || "system");
```

> `colorScheme` 을 같이 세팅해야 브라우저 기본 UI(스크롤바·`<select>`)까지 어두워진다. 빠뜨리기 쉽다.

### 3-3. 화면을 만든다

`halo-미리보기.html` 의 마크업을 골라 복사하는 게 가장 빠르다. 클래스 이름은 프로젝트 규칙에 맞게 바꿔도 되지만,
**구조(불투명 면 + 그림자 + 윗변 선 + 알약 버튼)는 유지**한다.

### 3-4. 새 컴포넌트를 만들 때

> **규칙: 새 CSS 에 `#hex` 를 쓰고 싶어지면, 토큰이 하나 부족한 것이다.**
> 하드코딩하지 말고 `halo-tokens.css` 에 토큰을 먼저 추가한 뒤 그걸 참조한다.
> 이걸 어기면 다크 모드가 그 컴포넌트에서만 깨진다 — 나중에 찾기 매우 어렵다.

```css
/* ❌ */ .my-widget { background: #ffffff; box-shadow: 0 4px 12px rgba(0,0,0,.1); border: 1px solid #eee; }
/* ✅ */ .my-widget { background: var(--surface); box-shadow: var(--sh); border: none; }
```

---

## 4. 치수 규칙 (외우면 편한 값들)

| 항목 | 값 | 비고 |
|---|---|---|
| 카드·패널 둥글기 | `--radius` **20px** | |
| 입력창·작은 요소 | `--radius-sm` **13px** | |
| 버튼·칩·배지 | **999px** (알약) | 예외 없음. 사각 버튼을 쓰면 Halo 가 아니다 |
| 히어로 | 26px | 카드보다 조금 더 크게 |
| 아이콘 배지 | 40px(카드) / 56px(페이지 제목) | 둥글기 `--radius-sm` / 19px |
| 본문 폰트 | 14.5px / line-height 1.65 | |
| 페이지 제목 | 25px / weight 750 / letter-spacing −.025em | |
| 히어로 제목 | 32px / weight 800 / letter-spacing −.03em | |
| 라벨·캡션 | 12.5px / `--muted2` | |
| 섹션 헤딩 | 11px / weight 700 / **uppercase** / letter-spacing .1em | 사이드바 그룹명 등 |
| 최대 폭 | 1360px | |
| 사이드바 | 250px | |
| 카드 그리드 | 3열 → 2열(1024px) → 1열(768px) | gap 14px |

**테두리 대신 그림자.** `border` 는 표의 행 구분선(`--border`)처럼 꼭 필요한 곳에만 쓴다.
면을 띄우는 건 전부 `--sh` 다.

**큰 제목은 자간을 좁힌다.** `letter-spacing: -.02em ~ -.03em`. 이게 없으면 헐거워 보인다.

---

## 5. 자주 하는 실수 (코드 리뷰 체크리스트)

- [ ] 카드 배경에 그라디언트를 넣지 않았나 → **① 위반**
- [ ] 한 화면에 주 버튼(`.primary`)이 3개 이상인가 → **② 위반.** 무엇이 주 동작인지 안 보인다
- [ ] 사이드바나 카드에 `backdrop-filter` 를 썼나 → **③ 위반**
- [ ] 윗변 선을 `left:0; right:0` 로 끝까지 채웠나 → **④ 위반**
- [ ] 버튼이 사각형인가 → 알약(999px)이어야 한다
- [ ] `#hex` 를 하드코딩했나 → 다크 모드에서 그 부분만 깨진다
- [ ] 다크 모드에서 `--surface` 를 순수 검정(`#000`)으로 뒀나 → **그림자가 안 보여 카드가 평평해진다.**
      `#1c1930` 처럼 살짝 밝은 값이어야 한다
- [ ] 다크 모드에서 그림자를 라이트와 같은 값으로 뒀나 → 안 보인다. 훨씬 진하게 (`rgba(0,0,0,.35)`)
- [ ] 배경 메시(오로라)를 진하게 키웠나 → 본문 대비가 떨어진다. 위쪽에 옅게만
- [ ] 그라디언트 요소에 일반 그림자(`--sh`)를 줬나 → `--sh-acc`(색 있는 그림자)를 써야 뜬다

---

## 6. 스택별 적용 메모

### React / Vue / Svelte

그대로 쓸 수 있다. 클래스 이름만 컴포넌트에 붙이면 된다.
CSS Modules 를 쓴다면 **토큰 파일만은 전역**으로 불러야 한다(`:root` 가 스코프되면 안 된다).

### Tailwind

토큰을 `tailwind.config` 로 옮긴다:

```js
theme: {
  extend: {
    colors: {
      point: "var(--point)", surface: "var(--surface)", soft: "var(--soft)",
      muted: "var(--muted)", muted2: "var(--muted2)",
    },
    borderRadius: { halo: "20px", "halo-sm": "13px" },
    boxShadow: {
      halo: "var(--sh)", "halo-lg": "var(--sh-lg)", "halo-acc": "var(--sh-acc)",
    },
    backgroundImage: { grad: "var(--grad)", "grad-hot": "var(--grad-hot)" },
  },
}
```

`halo-tokens.css` 는 **그대로 두고** 위처럼 참조만 한다. 값을 config 에 복제하면 다크 모드가 깨진다.
다크는 Tailwind 의 `dark:` 대신 `data-theme` 방식을 유지하는 게 낫다 — 토큰 한 벌만 바꾸면 되기 때문이다.

### 서버 렌더링(SSR)이 있다면

테마 깜빡임(FOUC)을 막으려면 `<head>` 에 인라인 스크립트를 넣어 **CSS 로드 전에** `data-theme` 을 세팅한다.

```html
<script>
  (function () {
    var s = localStorage.getItem("theme") || "system";
    var d = s === "dark" || (s === "system" && matchMedia("(prefers-color-scheme: dark)").matches);
    document.documentElement.setAttribute("data-theme", d ? "dark" : "light");
  })();
</script>
```

### 아이콘

원본 프로젝트는 Google Material Icons 를 썼다(`<span class="material-icons">이름</span>`).
필수가 아니다 — **아이콘 배지의 그라디언트 사각형이 본체**이고 안의 글리프는 무엇이든 된다.
외부 요청 없이 가려면 인라인 SVG 나 이모지로 대체해도 디자인은 유지된다.

---

## 7. 이 디자인이 무엇이 *아닌지* (탈락한 방향 6종)

시안 10개 중 6개가 탈락했다. **새 화면을 만들 때 아래로 흘러가면 Halo 에서 벗어난 것이다.**

| 탈락 시안 | 방향 | 왜 안 되나 |
|---|---|---|
| A. Blueprint | 다크 + 모노스페이스 + 고밀도(IDE 감성) | 개발자용이어도 **기본은 밝고 여유 있게**. 다크는 옵션이지 정체성이 아니다 |
| B. Paper | 크림 지면 + 명조체 + 넓은 여백 | **세리프를 쓰지 않는다.** 읽는 물건이 아니라 쓰는 물건이다 |
| C. Brutalist | 3px 검정 테두리 + 하드 그림자 + 원색 | **굵은 테두리·원색을 쓰지 않는다.** 경계는 그림자로 만든다 |
| E. Console | 아이콘 레일 3단 + 초고밀도 | **정보를 빽빽하게 채우지 않는다.** 여백이 이 디자인의 재료다 |
| H. Warm | 살구·모래·올리브 등 따뜻한 파스텔 | **색온도는 차가운 쪽**(보라·하늘·핑크)이다 |
| I. Bento | 크기가 다른 벤토 타일 + 사이드바 없음 | **균일한 카드 그리드**를 쓴다. 비균일 배치로 리듬을 주지 않는다. 좌측 사이드바 구조를 유지한다 |

한 줄로: **밝고 · 차갑고 · 여유 있고 · 둥글고 · 균일하고 · 테두리 없는** 쪽이다.

---

## 8. 받는 쪽 Claude Code 에게 주면 좋은 지시문

아래를 그대로 붙여넣으면 된다.

```
halo-design-kit/ 폴더의 HALO-디자인-가이드.md 를 읽고 이 프로젝트에 Halo 디자인을 적용해줘.

- halo-tokens.css 는 그대로 가져오고, 진입점에서 가장 먼저 불러올 것
- 기존 화면을 토큰 기반으로 바꾸되, 하드코딩된 색·그림자·둥글기는 전부 토큰으로 교체
- 네 원칙(①불투명 면 ②그라디언트는 초점만 ③유리는 헤더만 ④카드 윗변 선)을 지킬 것
- 다크 모드는 data-theme 방식으로 연결
- 작업 후 §5 체크리스트로 자가 점검하고 위반 항목을 보고할 것
```

프로젝트에 `CLAUDE.md` 가 있다면 다음 한 줄을 넣어 두면 이후 작업에서도 유지된다.

```md
- **디자인: Halo** — 원칙은 `halo-design-kit/HALO-디자인-가이드.md`.
  **단일 출처는 `halo-tokens.css` 의 `:root` 토큰** — 새 컴포넌트는 토큰만 조합해 만들고 색을 하드코딩하지 않는다.
```

---

## 9. 원본 프로젝트 참고 (선택)

이 디자인은 `dev-toolbox`(한국어 개발자 유틸 모음, 도구 46종)에서 실제로 운영 중이다.
실제 적용 사례가 더 필요하면 그 저장소의 아래 파일들을 참고할 수 있다 — **없어도 이 문서로 충분하다.**

- `frontend/src/styles.css` — 토큰 원본 + 레이아웃
- `frontend/src/styles-tools.css` — 파생 컴포넌트 30여 종 (표·캔버스·게이지·미리보기 카드 등)
- `설계/디자인_Halo/j-halo.html` — 최초 확정 시안(그 프로젝트 더미 데이터 기준)
- `설계/디자인_Halo/디자인_결정기록.md` — 10개 시안의 3라운드 심사 기록 (§7 의 원본)
