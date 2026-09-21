/**
 * 첫 로딩 자리표시 — 스피너·"불러오는 중…" 글자 대신 <b>자리를 먼저 잡는다</b>(설계 05 §8).
 *
 * 글자 한 줄은 내용이 뜨는 순간 레이아웃이 튄다. 스켈레톤은 곧 올 것의 크기로 서 있어서 안 튄다.
 * 개수는 한 화면분만 — 48개를 다 깔면 화면 전체가 맥동한다.
 *
 * 낭독기에는 "무엇을 불러오는 중"인지 글자로 남긴다(.k-sr) — 눈에서 지우는 것과 화면에서
 * 없애는 것은 다르다(§19-6). 테스트도 그 글자로 상태를 찾는다.
 */
export default function Skeleton({ kind = 'row', rows = 4, label }: {
  /** row = 목록 한 행(44px) · panel = 카드 한 장(160px) */
  kind?: 'row' | 'panel';
  rows?: number;
  /** 낭독기용 — "댓글을 불러오는 중…" 처럼 대상을 말한다 */
  label: string;
}) {
  return (
    <div className="skeleton-stack" role="status">
      <span className="k-sr">{label}</span>
      {Array.from({ length: rows }, (_, i) => (
        <div className={`k-skeleton ${kind === 'panel' ? 'sk-panel' : 'sk-row'}`} key={i} aria-hidden="true" />
      ))}
    </div>
  );
}
