import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { examApi } from '../api/exams';
import type { BoardItem } from '../api/types';

// 글 쓰기·수정 한 화면. id 가 있으면 수정 모드.
export default function PostEditorPage() {
  const { id } = useParams();
  const editing = Boolean(id);
  const navigate = useNavigate();

  const [boards, setBoards] = useState<BoardItem[]>([]);
  const [boardCode, setBoardCode] = useState('FREE');
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(editing);
  // 남의 글 — 폼을 보여줄 이유가 없다. 빨간 줄 아래 빈 폼은 "그래도 써 보라"는 말처럼 보인다
  const [forbidden, setForbidden] = useState(false);
  // 처음 값 — 여기서 벗어났을 때만 "쓰던 내용"이다(수정 모드에서 안 고치고 나가면 묻지 않는다)
  const initial = useRef({ title: '', content: '' });

  useEffect(() => {
    examApi.boards().then((r) => setBoards(r.items)).catch(() => { /* 기본 FREE 로 진행 */ });
  }, []);

  useEffect(() => {
    if (!editing) return;
    examApi.post(Number(id))
      .then((p) => {
        if (!p.mine) {
          setForbidden(true);
          return;
        }
        setBoardCode(p.boardCode);
        setTitle(p.title);
        setContent(p.content);
        initial.current = { title: p.title, content: p.content };
      })
      .catch((e: Error) => setErr(e.message))
      .finally(() => setLoading(false));
  }, [editing, id]);

  const dirty = title !== initial.current.title || content !== initial.current.content;

  // 탭을 닫거나 새로고침할 때 — 브라우저가 묻는다. 앱 안 이동(메뉴 클릭)은 BrowserRouter 라 막을 수 없다
  useEffect(() => {
    if (!dirty) return;
    const onBeforeUnload = (e: BeforeUnloadEvent) => { e.preventDefault(); e.returnValue = ''; };
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => window.removeEventListener('beforeunload', onBeforeUnload);
  }, [dirty]);

  function cancel() {
    if (dirty && !confirm('작성 중인 내용이 있습니다. 나갈까요?')) return;
    navigate(-1);
  }

  async function submit() {
    if (!title.trim() || !content.trim()) {
      setErr('제목과 내용을 모두 입력하세요.');
      return;
    }
    setBusy(true);
    setErr(null);
    try {
      const saved = editing
        ? await examApi.updatePost(Number(id), { title: title.trim(), content })
        : await examApi.createPost({ boardCode, title: title.trim(), content });
      // 저장했으니 더는 "쓰던 내용"이 아니다 — 이동하면서 beforeunload 가 묻지 않게
      initial.current = { title, content };
      navigate(`/community/posts/${saved.id}`, { replace: true });
    } catch (e) {
      setErr(e instanceof Error ? e.message : '저장하지 못했습니다.');
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <div className="k-empty state" role="status">불러오는 중…</div>;

  if (forbidden) {
    return (
      <div className="k-empty state">
        <span className="big">본인이 작성한 글만 수정할 수 있습니다</span>
        다른 사람의 글은 읽을 수만 있습니다.
        <div style={{ marginTop: 18 }}>
          <Link to={`/community/posts/${id}`} className="k-btn k-btn--secondary">글로 돌아가기</Link>
        </div>
      </div>
    );
  }

  return (
    <>
      <div className="page-header">
        <div className="page-header__text">
          <h1>{editing ? '글 수정' : '글쓰기'}</h1>
          <p>같은 시험을 준비하는 사람들에게 도움이 되는 이야기를 남겨 주세요.</p>
        </div>
      </div>

      {err && <div className="k-alert k-alert--err" role="alert">{err}</div>}

      <div className="k-card" style={{ padding: 20 }}>
        {!editing && (
          <div className="field" role="group" aria-label="게시판">
            <span>게시판</span>
            <div className="chip-row" style={{ marginBottom: 0 }}>
              {boards.map((b) => (
                <button
                  key={b.code}
                  className="k-chip" aria-pressed={b.code === boardCode}
                  onClick={() => setBoardCode(b.code)}
                  type="button"
                >
                  {b.name}
                </button>
              ))}
            </div>
          </div>
        )}

        <label className="field">
          <span>제목</span>
          <input className="k-input" value={title} maxLength={200}
                 placeholder="제목을 입력하세요"
                 onChange={(e) => setTitle(e.target.value)} />
        </label>

        <label className="field">
          <span>내용</span>
          <textarea className="k-textarea" rows={14} maxLength={10000}
                    placeholder="내용을 입력하세요"
                    value={content} onChange={(e) => setContent(e.target.value)} />
        </label>

        <div className="pager-row" style={{ justifyContent: 'flex-end' }}>
          <button className="k-btn k-btn--secondary" onClick={cancel} type="button">취소</button>
          <button className="k-btn k-btn--primary" onClick={submit} disabled={busy} type="button">
            {busy ? '저장 중…' : editing ? '수정하기' : '등록하기'}
          </button>
        </div>
      </div>
    </>
  );
}
