import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { examApi } from '../api/exams';
import { useAuth, useRequireLogin } from '../auth';
import type { CategoryItem, CertItem } from '../api/types';
import Icon from '../components/Icon';

// 시험 찾기: 전체 목록을 기본으로 보여준다.
// 예전에는 인기 10종만 노출해서 "시험이 없다"고 느껴졌다 — 이제 분류 필터 + 더보기로 전체를 훑는다.
// 일정이 아직 없는 시험도 함께 나온다(hasSchedule=false → '일정 미정'). 등록해 두면 일정이 붙을 때 알림이 간다.
const PAGE_SIZE = 24;

export default function SearchPage() {
  const { me } = useAuth();
  const requireLogin = useRequireLogin();
  const [q, setQ] = useState('');
  const [cat, setCat] = useState('');
  const [cats, setCats] = useState<CategoryItem[]>([]);
  const [items, setItems] = useState<CertItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const timer = useRef<number>();

  useEffect(() => {
    examApi.categories().then((r) => setCats(r.items)).catch(() => { /* 필터는 없어도 목록은 보여준다 */ });
  }, []);

  // 검색어는 디바운스, 분류는 즉시. 둘 다 첫 페이지부터 다시.
  useEffect(() => {
    window.clearTimeout(timer.current);
    const run = () => load(0, false);
    if (q.trim()) {
      timer.current = window.setTimeout(run, 300);
      return () => window.clearTimeout(timer.current);
    }
    run();
  }, [q, cat]);

  async function load(next: number, append: boolean) {
    setLoading(true);
    try {
      const r = await examApi.browse({
        query: q.trim() || undefined,
        category: cat || undefined,
        page: next,
        size: PAGE_SIZE,
      });
      setItems((prev) => (append ? [...prev, ...r.items] : r.items));
      setTotal(r.totalElements);
      setTotalPages(r.totalPages);
      setPage(r.page);
      setErr(null);
    } catch (e) {
      setErr(e instanceof Error ? e.message : '목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }

  async function toggle(c: CertItem) {
    if (!me) {
      requireLogin();
      return;
    }
    try {
      if (c.favorited) await examApi.removeFavorite(c.id);
      else await examApi.addFavorite(c.id);
      setItems((prev) => prev.map((x) => (x.id === c.id ? { ...x, favorited: !x.favorited } : x)));
      setErr(null);
    } catch (e) {
      setErr(e instanceof Error ? e.message : '관심 등록에 실패했습니다.');
    }
  }

  const hasMore = page + 1 < totalPages;

  return (
    <>
      {/* 킷 문법: 연보라 히어로 띠에 큰 제목 하나 + 할 일(검색). 데모의 첫 인상을 그대로 따른다 */}
      <section className="k-hero search-hero">
        {/* 제목의 숫자는 검색·분류와 무관하게 늘 전체 — 분류 개수 합이 곧 전체다 */}
        <h1>시험 {(cats.reduce((a, c) => a + c.count, 0) || 800).toLocaleString()}종, 접수 마감을 놓치지 않게</h1>
        <p>큐넷·국시원·어학까지 한곳에서 찾고, 등록해 두면 접수 시작·마감을 알려 드립니다.</p>
        <div className="search-row">
          {/* 분류는 38개 — 칩으로 늘어놓으면 5줄이다. 고르는 건 드롭다운이 깔끔하다 */}
          <select className="k-select" value={cat} onChange={(e) => setCat(e.target.value)} aria-label="분류">
            <option value="">전체 분류</option>
            {groupCategories(cats).map((g) => (
              <optgroup key={g.label} label={g.label}>
                {g.items.map((c) => (
                  <option key={c.name} value={c.name}>{c.name} ({c.count})</option>
                ))}
              </optgroup>
            ))}
          </select>
          <div className="searchbar">
            <span className="ico" aria-hidden="true"><Icon name="search" size={18} /></span>
            <input
              className="k-input"
              placeholder="시험명 검색 (예: 정보처리기사, 토익, 한국사)"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              aria-label="시험명 검색"
            />
          </div>
        </div>
      </section>

      {err && <div className="k-alert k-alert--err">{err}</div>}

      <div className="k-section list-head">
        <h2>{q.trim() ? `‘${q.trim()}’ 검색 결과` : cat || '전체 시험'} <span className="more">{total.toLocaleString()}개</span></h2>
      </div>

      {items.length === 0 && !loading ? (
        <div className="k-empty state">
          <span className="big">결과가 없습니다</span>
          다른 이름이나 분류로 찾아보세요.
        </div>
      ) : (
        <div className="card-grid">
          {items.map((c) => (
            <div className="k-card k-card--hover exam-card" key={c.id}>
              <div className="top">
                <Link to={`/cert/${c.id}`} className="grow">
                  <h3>{c.name}</h3>
                  <p className="sub">{[c.category, c.agency].filter(Boolean).join(' · ')}</p>
                </Link>
                <button
                  className="star"
                  data-on={c.favorited}
                  onClick={() => toggle(c)}
                  aria-label={c.favorited ? '관심 해제' : '관심 등록'}
                  aria-pressed={c.favorited}
                >
                  <Icon name="star" size={20} filled={c.favorited} />
                </button>
              </div>
              {/* 상태는 배지 하나로 — 문장을 846번 반복하면 화면이 지저분해진다 */}
              <div className="foot">
                {c.rolling
                  ? <>
                      <span className="k-badge">상시시험</span>
                      {/* 상시만 한 줄 설명 — 28종뿐이고, 왜 일정이 없는지는 알려줘야 한다 */}
                      <span className="when">원하는 날짜에 신청하는 시험이라 정해진 일정이 없습니다</span>
                    </>
                  : c.hasSchedule
                    ? <span className="k-badge k-badge--ok">일정 있음</span>
                    : <span className="k-badge k-badge--warn">일정 미정</span>}
              </div>
            </div>
          ))}
        </div>
      )}

      {loading && <div className="k-empty state">불러오는 중…</div>}

      {hasMore && !loading && (
        <div style={{ display: 'flex', justifyContent: 'center', marginTop: 18 }}>
          <button className="k-btn k-btn--primary" onClick={() => load(page + 1, true)}>
            더 보기 ({items.length.toLocaleString()} / {total.toLocaleString()})
          </button>
        </div>
      )}
    </>
  );
}

/**
 * 분류 38개를 드롭다운에 그냥 쏟으면 개수 순으로 섞여 훑기 어렵다.
 * 접두어(국가기술자격 / 어학 / IT …)로 묶고 그 안은 가나다순 — 묶음도 가나다순, 접두어 없는 것은 맨 뒤.
 */
function groupCategories(cats: CategoryItem[]): { label: string; items: CategoryItem[] }[] {
  const groups = new Map<string, CategoryItem[]>();
  for (const c of cats) {
    const dash = c.name.indexOf('-');
    const key = dash > 0 ? c.name.slice(0, dash) : '그 외';
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key)!.push(c);
  }
  const collator = new Intl.Collator('ko');
  return [...groups.entries()]
    .map(([label, items]) => ({ label, items: [...items].sort((a, b) => collator.compare(a.name, b.name)) }))
    .sort((a, b) => (a.label === '그 외' ? 1 : b.label === '그 외' ? -1 : collator.compare(a.label, b.label)));
}
