import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
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

  useEffect(() => {
    examApi.boards().then((r) => setBoards(r.items)).catch(() => { /* 기본 FREE 로 진행 */ });
  }, []);

  useEffect(() => {
    if (!editing) return;
    examApi.post(Number(id))
      .then((p) => {
        if (!p.mine) {
          setErr('본인이 작성한 글만 수정할 수 있습니다.');
          return;
        }
        setBoardCode(p.boardCode);
        setTitle(p.title);
        setContent(p.content);
      })
      .catch((e: Error) => setErr(e.message))
      .finally(() => setLoading(false));
  }, [editing, id]);

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
      navigate(`/community/posts/${saved.id}`, { replace: true });
    } catch (e) {
      setErr(e instanceof Error ? e.message : '저장하지 못했습니다.');
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <div className="state">불러오는 중…</div>;

  return (
    <>
      <div className="page-header">
        <div>
          <h1>{editing ? '글 수정' : '글쓰기'}</h1>
          <p>같은 시험을 준비하는 사람들에게 도움이 되는 이야기를 남겨 주세요.</p>
        </div>
      </div>

      {err && <div className="notice error">{err}</div>}

      <div className="panel" style={{ padding: 20 }}>
        {!editing && (
          <div className="field">
            <span>게시판</span>
            <div className="chip-row" style={{ marginBottom: 0 }}>
              {boards.map((b) => (
                <button
                  key={b.code}
                  className={`chip${b.code === boardCode ? ' on' : ''}`}
                  onClick={() => setBoardCode(b.code)}
                  type="button"
                >
                  {b.name}
                </button>
              ))}
            </div>
          </div>
        )}

        <div className="field">
          <span>제목</span>
          <input className="input" value={title} maxLength={200}
                 placeholder="제목을 입력하세요"
                 onChange={(e) => setTitle(e.target.value)} />
        </div>

        <div className="field">
          <span>내용</span>
          <textarea className="area" rows={14} maxLength={10000}
                    placeholder="내용을 입력하세요"
                    value={content} onChange={(e) => setContent(e.target.value)} />
        </div>

        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <button className="btn" onClick={() => navigate(-1)} type="button">취소</button>
          <button className="btn primary" onClick={submit} disabled={busy} type="button">
            {busy ? '저장 중…' : editing ? '수정하기' : '등록하기'}
          </button>
        </div>
      </div>
    </>
  );
}
