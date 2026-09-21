import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { examApi } from '../api/exams';
import { scheduleStateOf } from '../lib/status';
import { useAuth, useRequireLogin } from '../auth';
import type { BrowseResponse, CategoryItem, CertItem } from '../api/types';
import Icon from '../components/Icon';
import Pagination from '../components/Pagination';
import CardStatus from '../components/CardStatus';

// 시험 찾기: 전체 목록을 기본으로 보여준다. 일정이 아직 없는 시험도 함께 나온다('일정 미정').
//
// 검색어·분류·쪽은 URL(q·cat·page)에 산다 — 상세를 보고 돌아와도, 링크를 공유해도 같은 화면이다.
// 한 쪽 48개(서버 상한 100) + 번호 페이징. 24개씩 "더 보기"를 35번 누르던 것을 바꿨다.
const PAGE_SIZE = 48;
const DEBOUNCE_MS = 300;

type Patch = { q?: string | null; cat?: string | null; page?: number | null };

export default function SearchPage() {
  const { me } = useAuth();
  const requireLogin = useRequireLogin();
  const [params, setParams] = useSearchParams();
  const q = (params.get('q') ?? '').trim();
  const cat = params.get('cat') ?? '';
  // 주소는 사람이 보는 것이라 1부터, 서버는 0부터
  const page = Math.max(0, (Number(params.get('page')) || 1) - 1);

  const [input, setInput] = useState(q);
  const [cats, setCats] = useState<CategoryItem[]>([]);
  const [catsFailed, setCatsFailed] = useState(false);
  const [data, setData] = useState<BrowseResponse | null>(null);
  // 히어로의 "시험 N종"은 전체 개수다 — 조건 없이 조회했을 때만 알 수 있다
  const [grandTotal, setGrandTotal] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const seq = useRef(0);
  const debounce = useRef<number>();
  const listTop = useRef<HTMLDivElement>(null);

  useEffect(() => {
    examApi.categories().then((r) => setCats(r.items)).catch(() => setCatsFailed(true));
  }, []);

  // 뒤로가기 등으로 주소가 바뀌면 입력칸을 맞춘다
  useEffect(() => { setInput(q); }, [q]);
  useEffect(() => () => window.clearTimeout(debounce.current), []);

  useEffect(() => {
    // 요청은 순서대로 돌아오지 않는다 — 먼저 보낸 느린 응답이 나중에 와서 새 결과를 덮으면 안 된다
    const my = ++seq.current;
    setLoading(true);
    examApi.browse({ query: q || undefined, category: cat || undefined, page, size: PAGE_SIZE })
      .then((r) => {
        if (my !== seq.current) return;
        setData(r);
        setErr(null);
        if (!q && !cat) setGrandTotal(r.totalElements);
      })
      .catch((e: unknown) => {
        if (my !== seq.current) return;
        setErr(e instanceof Error ? e.message : '목록을 불러오지 못했습니다.');
      })
      .finally(() => { if (my === seq.current) setLoading(false); });
  }, [q, cat, page]);

  function update(patch: Patch, replace = false) {
    setParams((prev) => {
      const next = new URLSearchParams(prev);
      for (const [k, v] of Object.entries(patch)) {
        if (v == null || v === '') next.delete(k);
        else next.set(k, String(v));
      }
      return next;
    }, { replace });
  }

  // 검색어는 디바운스해서 주소에 쓴다(글자마다 뒤로가기 기록이 쌓이지 않게 replace). 지우면 바로.
  function onInput(value: string) {
    setInput(value);
    window.clearTimeout(debounce.current);
    const next = value.trim();
    debounce.current = window.setTimeout(
      () => update({ q: next || null, page: null }, true),
      next ? DEBOUNCE_MS : 0,
    );
  }

  function goPage(p: number) {
    update({ page: p === 0 ? null : p + 1 });
    listTop.current?.scrollIntoView?.({ block: 'start' });
  }

  async function toggle(c: CertItem) {
    if (!me) {
      requireLogin();
      return;
    }
    try {
      if (c.favorited) await examApi.removeFavorite(c.id);
      else await examApi.addFavorite(c.id);
      setData((prev) => prev && ({
        ...prev, items: prev.items.map((x) => (x.id === c.id ? { ...x, favorited: !x.favorited } : x)),
      }));
      setErr(null);
    } catch (e) {
      setErr(e instanceof Error ? e.message : '관심 등록에 실패했습니다.');
    }
  }

  const total = data?.totalElements ?? 0;

  return (
    <>
      {/* 킷 문법: 히어로 띠에 큰 제목 하나 + 할 일(검색). 숫자는 목록 API 가 준 전체 개수 — 지어내지 않는다 */}
      <section className="k-hero search-hero">
        <h1>
          {grandTotal != null
            ? `시험 ${grandTotal.toLocaleString()}종, 접수 마감을 놓치지 않게`
            : '시험 일정, 접수 마감을 놓치지 않게'}
        </h1>
        {/* 제목이 이미 "접수 마감을 놓치지 않게"라고 말했다 — 같은 말을 두 번 하지 않는다(설계 05 §19-6) */}
        <p>큐넷·국시원·어학까지 한곳에서 찾고, 관심 등록해 두면 알려 드립니다.</p>
        <div className="search-row">
          {/* 분류는 38개 — 칩으로 늘어놓으면 5줄이다. 고르는 건 드롭다운이 깔끔하다 */}
          <select className="k-select" value={cat}
                  onChange={(e) => update({ cat: e.target.value || null, page: null })} aria-label="분류">
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
              value={input}
              onChange={(e) => onInput(e.target.value)}
              aria-label="시험명 검색"
            />
          </div>
          {/* 분류 조회가 죽어도 목록은 나온다 — 대신 드롭다운이 왜 비었는지는 말해 준다 */}
          {catsFailed && (
            <span className="k-help k-help--err" role="alert">분류를 불러오지 못했습니다 — 검색은 됩니다</span>
          )}
        </div>
      </section>

      {err && <div className="k-alert k-alert--err" role="alert">{err}</div>}

      <div className="k-section list-head" ref={listTop}>
        <h2>{q ? `‘${q}’ 검색 결과` : cat || '전체 시험'} <span className="more">{total.toLocaleString()}개</span></h2>
      </div>

      {/* 첫 로딩은 스켈레톤으로 자리를 미리 잡는다 — 한 화면분(6장)만. 48개를 다 깔면 화면이 맥동한다(설계 05 §8) */}
      {loading && !data && (
        <div className="card-grid" role="status" aria-label="시험 목록 불러오는 중">
          {Array.from({ length: 6 }, (_, i) => (
            <div className="k-skeleton card-skeleton" key={i} />
          ))}
        </div>
      )}

      {!loading && data && data.items.length === 0 && <EmptyResult q={q} cat={cat} onReset={update} />}

      {data && data.items.length > 0 && (
        // 다시 불러오는 동안 목록은 남고 흐려진다 — 자리가 흔들리면 어디를 보고 있었는지 잃는다
        <div className="card-grid" aria-busy={loading}>
          {data.items.map((c) => (
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
                  <Icon name="star" size={22} filled={c.favorited} />
                </button>
              </div>
              {/* 목록은 "어떤 시험인지 고르는" 화면이다 — 회차·구분·시각은 상세에서 본다.
                  여기 남는 건 언제쯤인지 한 줄뿐이고, 그래서 카드 841장의 줄이 전부 맞는다. */}
              <div className="foot">
                <CardStatus state={scheduleStateOf(c)} badge={c.nextBadge} label={c.nextLabel} at={c.nextAt}
                            dday={c.nextDday} lastExamDate={c.lastExamDate} confirmed={c.nextConfirmed} inlineDday compact />
              </div>
            </div>
          ))}
        </div>
      )}

      {data && (
        <div className="pager-row">
          <Pagination page={page} totalPages={data.totalPages} onChange={goPage} />
        </div>
      )}
    </>
  );
}

/**
 * 0건 문구는 3분기다(설계 05 §8). 한 문구로 뭉치면 <b>사용자가 고칠 수 있는 게 있는지</b>를 알 수 없다.
 *
 * <p>검색어가 있으면 오타를 스스로 발견하도록 작은따옴표로 되읽어 주고, 분류만 걸렸으면 그 분류를 말한다.
 * 조건이 하나도 없는데 0건인 것은 <b>사용자가 고칠 수 있는 게 없는 상태</b>라 "초기화"를 권하면 거짓 안내가 된다.
 */
function EmptyResult({ q, cat, onReset }: {
  q: string;
  cat: string;
  onReset: (patch: Patch) => void;
}) {
  if (!q && !cat) {
    return (
      <div className="k-empty state">
        <span className="big">아직 준비 중이에요</span>
        시험 목록을 불러왔지만 보여 드릴 것이 없습니다. 잠시 뒤 다시 열어 보세요.
      </div>
    );
  }
  return (
    <div className="k-empty state">
      <span className="big">{q ? `‘${q}’ 검색 결과가 없어요` : '조건에 맞는 시험이 없어요'}</span>
      {q && cat ? `분류 ‘${cat}’ 안에서 찾았습니다.` : cat ? `분류 ‘${cat}’ 에는 아직 없습니다.` : '다른 이름으로 찾아보세요.'}
      <div className="empty-actions">
        {q && (
          <button className="k-btn k-btn--secondary k-btn--sm"
                  onClick={() => onReset({ q: null, page: null })}>검색어 지우기</button>
        )}
        {cat && (
          <button className="k-btn k-btn--secondary k-btn--sm"
                  onClick={() => onReset({ cat: null, page: null })}>분류 초기화</button>
        )}
      </div>
    </div>
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
