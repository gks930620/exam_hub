/** 2026-09-21T10:00 → 2026-09-21 10:00 — ISO 의 T 는 사람이 읽는 표기가 아니다. 비어 있으면 '-'. */
export function fmtAt(iso: string | null | undefined): string {
  if (!iso) return '-';
  return iso.replace('T', ' ').slice(0, 16);
}

/** 2026-09-05T00:00 → 2026-09-05 — 시험 종료일처럼 시각(00:00)이 뜻이 없는 값용. 비어 있으면 '-'. */
export function fmtDate(iso: string | null | undefined): string {
  if (!iso) return '-';
  return iso.slice(0, 10);
}

const WEEKDAY = ['일', '월', '화', '수', '목', '금', '토'];

/**
 * '2026-09-06' → '일'. 날짜만 있는 문자열을 new Date() 에 그대로 주면 <b>UTC 자정</b>으로 읽혀
 * UTC 보다 서쪽 시간대에서는 전날 요일이 나온다 — 현지 자정(T00:00:00)으로 읽는다.
 */
export function weekdayOf(date: string): string {
  return WEEKDAY[new Date(date.slice(0, 10) + 'T00:00:00').getDay()];
}

/** 0 → D-Day, 3 → D-3, -2 → D+2. 화면마다 제각각이던 표기를 하나로. */
export function ddayLabel(n: number): string {
  if (n === 0) return 'D-Day';
  return n > 0 ? `D-${n}` : `D+${-n}`;
}
