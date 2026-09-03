// 다크 모드 — <html data-theme> 하나만 바꾸면 킷 토큰이 전부 따라온다(Lets 킷 공통 계약).
// 첫 페인트 세팅은 index.html 인라인 스크립트가 담당한다(CSS 로드 전에 깜빡임을 막는다).

export type ThemeSetting = 'light' | 'dark' | 'system';

const KEY = 'theme';

export function readTheme(): ThemeSetting {
  const v = localStorage.getItem(KEY);
  return v === 'light' || v === 'dark' || v === 'system' ? v : 'system';
}

export function isDark(setting: ThemeSetting): boolean {
  return setting === 'dark'
    || (setting === 'system' && matchMedia('(prefers-color-scheme: dark)').matches);
}

export function applyTheme(setting: ThemeSetting): void {
  const dark = isDark(setting);
  document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
  // 이걸 같이 세팅해야 스크롤바·기본 폼 컨트롤까지 어두워진다.
  document.documentElement.style.colorScheme = dark ? 'dark' : 'light';
  localStorage.setItem(KEY, setting);
}
