// Lets — Tailwind 프리셋 (자동 생성: node tools/build.mjs)
// tailwind.config.js:  module.exports = { presets: [require("./tailwind.preset.js")], … }
// tokens.css 를 함께 로드해야 한다 — 여기 값들은 전부 CSS 변수를 가리킨다.
module.exports = {
  theme: {
    extend: {
      colors: {
        "bg": "var(--bg)",
        "bg-sunken": "var(--bg-sunken)",
        "surface": "var(--surface)",
        "surface-alt": "var(--surface-alt)",
        "surface-hover": "var(--surface-hover)",
        "text": "var(--text)",
        "muted": "var(--muted)",
        "muted2": "var(--muted2)",
        "point": "var(--point)",
        "point-hover": "var(--point-hover)",
        "point-soft": "var(--point-soft)",
        "point-text": "var(--point-text)",
        "on-point": "var(--on-point)",
        "point-2": "var(--point-2)",
        "border": "var(--border)",
        "border-strong": "var(--border-strong)",
        "ok": "var(--ok)",
        "ok-soft": "var(--ok-soft)",
        "ok-text": "var(--ok-text)",
        "warn": "var(--warn)",
        "warn-soft": "var(--warn-soft)",
        "warn-text": "var(--warn-text)",
        "err": "var(--err)",
        "err-soft": "var(--err-soft)",
        "err-text": "var(--err-text)",
        "overlay": "var(--overlay)",
        "focus": "var(--focus)"
      },
      spacing: {
        "1": "4px",
        "2": "8px",
        "3": "12px",
        "4": "16px",
        "5": "20px",
        "6": "24px",
        "8": "32px",
        "10": "40px"
      },
      fontSize: {
        "xs": "12px",
        "sm": "13.5px",
        "md": "15px",
        "lg": "17px",
        "xl": "22px",
        "2xl": "30px",
        "3xl": "42px"
      },
      borderRadius: { sm: 'var(--r-sm)', DEFAULT: 'var(--r)', lg: 'var(--r-lg)', pill: 'var(--r-pill)' },
      boxShadow: { 1: 'var(--sh-1)', 2: 'var(--sh-2)', 3: 'var(--sh-3)' },
    },
  },
};
