import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { examApi } from '../api/exams';
import { useAuth, useRequireLogin } from '../auth';
import type { CategoryItem, CertItem } from '../api/types';

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
      <div className="page-header">
        <div className="page-avatar" aria-hidden="true">⌕</div>
        <div>
          <h1>시험 찾기</h1>
          <p>등록해 두면 접수 시작·마감과 시험일을 알려 드립니다.</p>
        </div>
      </div>

      <div className="searchbar">
        <span className="ico" aria-hidden="true">⌕</span>
        <input
          className="k-input"
          placeholder="시험명 검색 (예: 정보처리기사, 토익, 한국사)"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          aria-label="시험명 검색"
        />
      </div>

      {cats.length > 0 && (
        <div className="chip-row">
          <button className="k-chip" aria-pressed={cat === ''} onClick={() => setCat('')}>
            전체 <b>{cats.reduce((a, c) => a + c.count, 0)}</b>
          </button>
          {cats.map((c) => (
            <button
              key={c.name}
              className="k-chip" aria-pressed={c.name === cat}
              onClick={() => setCat(c.name === cat ? '' : c.name)}
            >
              {c.name} <b>{c.count}</b>
            </button>
          ))}
        </div>
      )}

      {err && <div className="k-alert k-alert--err">{err}</div>}

      <div className="section-head">
        <h2>{q.trim() ? `‘${q.trim()}’ 검색 결과` : cat || '전체 시험'}</h2>
        <span className="more">{total.toLocaleString()}개</span>
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
                  {c.favorited ? '★' : '☆'}
                </button>
              </div>
              {c.rolling ? (
                <div className="foot">
                  <span className="k-badge">상시시험</span>
                  <span className="when">원하는 날짜에 신청하는 시험이라 정해진 일정이 없습니다</span>
                </div>
              ) : !c.hasSchedule && (
                <div className="foot">
                  <span className="k-badge">일정 미정</span>
                  <span className="when">등록해 두면 일정이 확인되는 대로 알려 드립니다</span>
                </div>
              )}
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
