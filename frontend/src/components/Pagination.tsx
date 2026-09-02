import Icon from './Icon';
/**
 * 번호 페이징. "이전/다음"만으로는 몇십 쪽을 오갈 수 없다 — 지금 몇 쪽인지도,
 * 멀리 건너뛸 방법도 없기 때문이다(사용자 지적).
 *
 * 처음·끝과 현재 쪽 주변(±2)만 번호로 두고 사이는 생략표로 줄인다.
 * page 는 서버와 같게 0부터 세고, 화면에만 1부터 보여준다.
 */
export default function Pagination({ page, totalPages, onChange }: {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}) {
  if (totalPages <= 1) return null;

  const shown = new Set<number>([0, totalPages - 1]);
  for (let p = page - 2; p <= page + 2; p++) {
    if (p >= 0 && p < totalPages) shown.add(p);
  }
  const pages = [...shown].sort((a, b) => a - b);

  const items: (number | 'gap')[] = [];
  pages.forEach((p, i) => {
    if (i > 0 && p - pages[i - 1] > 1) items.push('gap');
    items.push(p);
  });

  return (
    <nav className="k-pager pagination" aria-label="쪽 이동">
      <button className="page-btn arrow" disabled={page === 0}
              onClick={() => onChange(page - 1)} aria-label="이전 쪽"><Icon name="chevronLeft" size={16} /></button>
      {items.map((it, i) =>
        it === 'gap'
          ? <span key={`gap-${i}`} className="page-gap" aria-hidden="true">…</span>
          : (
            <button key={it}
                    className={`page-btn${it === page ? ' active' : ''}`}
                    aria-current={it === page ? 'page' : undefined}
                    onClick={() => onChange(it)}>
              {it + 1}
            </button>
          ))}
      <button className="page-btn arrow" disabled={page >= totalPages - 1}
              onClick={() => onChange(page + 1)} aria-label="다음 쪽"><Icon name="chevronRight" size={16} /></button>
    </nav>
  );
}
