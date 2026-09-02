import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { examApi } from '../api/exams';
import { useAuth } from '../auth';
import type { BoardItem, PostSummary } from '../api/types';
import Icon from '../components/Icon';

// 커뮤니티 글 목록 — 읽기는 누구나, 쓰기는 로그인(설계 08).
export default function CommunityPage() {
  const [params, setParams] = useSearchParams();
  const board = params.get('board') ?? '';
  const navigate = useNavigate();
  const { me } = useAuth();

  const [boards, setBoards] = useState<BoardItem[]>([]);
  const [items, setItems] = useState<PostSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
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

  function more() {
    const next = page + 1;
    examApi.posts({ board: board || undefined, page: next, size: 20 }).then((r) => {
      setItems((prev) => [...prev, ...r.items]);
      setPage(r.page);
    });
  }

  function write() {
    if (!me) {
      navigate('/login', { state: { from: '/community' } });
      return;
    }
    navigate('/community/write');
  }

  return (
    <>
      <div className="page-header">
        <div className="page-avatar" aria-hidden="true"><Icon name="chat" size={22} /></div>
        <div>
          <h1>커뮤니티</h1>
          <p>같은 시험을 준비하는 사람들과 이야기하세요.</p>
        </div>
        <div style={{ marginLeft: 'auto' }}>
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

      {err && <div className="k-alert k-alert--err">{err}</div>}

      <div className="section-head">
        <h2>{boards.find((b) => b.code === board)?.name ?? '전체 글'}</h2>
        <span className="more">{total.toLocaleString()}개</span>
      </div>

      {loading ? (
        <div className="k-empty state">불러오는 중…</div>
      ) : items.length === 0 ? (
        <div className="k-empty state">
          <span className="big">아직 글이 없습니다</span>
          첫 글을 남겨 보세요.
        </div>
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
                <p className="sub">{p.authorName} · {p.createdAt}</p>
              </div>
              <span className="views">조회 {p.viewCount}</span>
            </Link>
          ))}
        </div>
      )}

      {page + 1 < totalPages && (
        <div style={{ display: 'flex', justifyContent: 'center', marginTop: 18 }}>
          <button className="k-btn k-btn--secondary" onClick={more}>더 보기</button>
        </div>
      )}
    </>
  );
}
