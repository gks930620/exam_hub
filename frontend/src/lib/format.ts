/** 2026-09-21T10:00 → 2026-09-21 10:00 — ISO 의 T 는 사람이 읽는 표기가 아니다. 비어 있으면 '-'. */
export function fmtAt(iso: string | null | undefined): string {
  if (!iso) return '-';
  return iso.replace('T', ' ').slice(0, 16);
}
