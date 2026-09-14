import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { examApi } from '../api/exams';
import { fmtAt } from '../lib/format';
import { useAuth, useRequireLogin } from '../auth';
import type { BoardItem, PostSummary } from '../api/types';
import Icon from '../components/Icon';

// 커뮤니티 글 목록 — 읽기는 누구나, 쓰기는 로그인(설계 08).
export default function CommunityPage() {
  const [params, setParams] = useSearchParams();
  const board = params.get('board') ?? '';
  const navigate = useNavigate();
  const { me } = useAuth();
  const requireLogin = useRequireLogin();

  const [boards, setBoards] = useState<BoardItem[]>([]);
  const [items, setItems] = useState<PostSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    examApi.boards().then((r) => setBoards(r.items)).catch(() => { /* 필터 없이도 목록은 보여준다 */ });
  }, []);

  useEffect(() => {
    setLoading(true);
    examApi.posts({ board: board || undefined, page: 0, size: 20 })
      .then((r) => {
        setItems(r.items);
        setTotal(r.totalElements);
        setTotalPages(r.totalPages);
        setPage(0);
        setErr(null);
      })
      .catch((e: Error) => setErr(e.message))
      .finally(() => setLoading(false));
  }, [board]);

  async function more() {
    setLoadingMore(true);
    try {
      const r = await examApi.posts({ board: board || undefined, page: page + 1, size: 20 });
      setItems((prev) => [...prev, ...r.items]);
      setPage(r.page);
      setErr(null);
    } catch (e) {
      // 조용히 삼키면 "더 보기"를 눌러도 아무 일이 없는 것처럼 보인다
      setErr(e instanceof Error ? e.message : '더 불러오지 못했습니다.');
    } finally {
      setLoadingMore(false);
    }
  }

  function write() {
    if (!me) {
      requireLogin();
      return;
    }
    navigate('/community/write');
  }

  return (
    <>
      <div className="page-header">
        <div className="page-avatar" aria-hidden="true"><Icon name="chat" size={22} /></div>
        <div className="page-header__text">
          <h1>커뮤니티</h1>
          <p>같은 시험을 준비하는 사람들과 이야기하세요.</p>
        </div>
        <div className="page-header__actions">
          <button className="k-btn k-btn--primary" onClick={write}>글쓰기</button>
        </div>
      </div>

      <div className="chip-row">
        <button className="k-chip" aria-pressed={board === ''} onClick={() => setParams({})}>
          전체
        </button>
        {boards.map((b) => (
          <button
            key={b.code}
            className="k-chip" aria-pressed={b.code === board}
            onClick={() => setParams(b.code === board ? {} : { board: b.code })}
          >
            {b.name}
          </button>
        ))}
      </div>

      {err && <div className="k-alert k-alert--err" role="alert">{err}</div>}

      <div className="section-head">
        <h2>{boards.find((b) => b.code === board)?.name ?? '전체 글'}</h2>
        <span className="more">{total.toLocaleString()}개</span>
      </div>

      {loading ? (
        // 첫 로딩은 스켈레톤으로 자리를 먼저 잡는다 — 한 화면분만(설계 05 §8)
        <div className="post-list" role="status" aria-label="글 목록 불러오는 중">
          {Array.from({ length: 5 }, (_, i) => <div className="k-skeleton row-skeleton" key={i} />)}
        </div>
      ) : items.length === 0 ? (
        // 0건 문구는 조건이 있느냐로 갈린다(§8) — 게시판을 고른 탓인지, 정말 아무 글도 없는지
        board ? (
          <div className="k-empty state">
            <span className="big">이 게시판에는 아직 글이 없어요</span>
            다른 게시판에는 글이 있을 수 있습니다.
            <div className="empty-actions">
              <button className="k-btn k-btn--secondary k-btn--sm" onClick={() => setParams({})}>전체 글 보기</button>
            </div>
          </div>
        ) : (
          <div className="k-empty state">
            <span className="big">아직 글이 없어요</span>
            첫 글을 남겨 보세요.
          </div>
        )
      ) : (
        <div className="post-list">
          {items.map((p) => (
            <Link key={p.id} to={`/community/posts/${p.id}`} className="k-card k-card--hover post-row">
              <div className="post-main">
                <span className="k-badge">{p.boardName}</span>
                <h3>
                  {p.title}
                  {p.commentCount > 0 && <em className="cnt">[{p.commentCount}]</em>}
                </h3>
                <p className="sub">{p.authorName} · {fmtAt(p.createdAt)}</p>
              </div>
              <span className="views">조회 {p.viewCount}</span>
            </Link>
          ))}
        </div>
      )}

      {page + 1 < totalPages && (
        <div className="pager-row">
          <button className="k-btn k-btn--secondary" onClick={more} disabled={loadingMore}>
            {loadingMore ? '불러오는 중…' : '더 보기'}
          </button>
        </div>
      )}
    </>
  );
}
