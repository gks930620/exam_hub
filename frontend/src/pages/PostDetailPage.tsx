import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { examApi } from '../api/exams';
import { useAuth } from '../auth';
import type { CommentItem, PostDetail } from '../api/types';

// 글 상세 + 댓글. 수정/삭제 버튼은 서버가 내려주는 mine 플래그로만 띄운다
// (프런트가 작성자 id 를 비교하지 않는다 — 판단 주체는 서버여야 한다).
export default function PostDetailPage() {
  const { id } = useParams();
  const postId = Number(id);
  const navigate = useNavigate();
  const { me } = useAuth();

  const [post, setPost] = useState<PostDetail | null>(null);
  const [comments, setComments] = useState<CommentItem[]>([]);
  const [draft, setDraft] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    examApi.post(postId).then(setPost).catch((e: Error) => setErr(e.message));
    examApi.comments(postId).then((r) => setComments(r.items)).catch(() => { /* 본문은 보여준다 */ });
  }, [postId]);

  async function submitComment() {
    if (!me) {
      navigate('/login', { state: { from: `/community/posts/${postId}` } });
      return;
    }
    const content = draft.trim();
    if (!content) return;
    setBusy(true);
    try {
      const created = await examApi.addComment(postId, content);
      setComments((prev) => [...prev, created]);
      setDraft('');
      setPost((p) => (p ? { ...p, commentCount: p.commentCount + 1 } : p));
      setErr(null);
    } catch (e) {
      setErr(e instanceof Error ? e.message : '댓글을 남기지 못했습니다.');
    } finally {
      setBusy(false);
    }
  }

  async function removeComment(commentId: number) {
    if (!confirm('댓글을 삭제할까요?')) return;
    try {
      await examApi.deleteComment(commentId);
      setComments((prev) => prev.filter((c) => c.id !== commentId));
      setPost((p) => (p ? { ...p, commentCount: Math.max(0, p.commentCount - 1) } : p));
    } catch (e) {
      setErr(e instanceof Error ? e.message : '삭제하지 못했습니다.');
    }
  }

  async function removePost() {
    if (!confirm('글을 삭제할까요? 달린 댓글도 함께 보이지 않게 됩니다.')) return;
    try {
      await examApi.deletePost(postId);
      navigate('/community', { replace: true });
    } catch (e) {
      setErr(e instanceof Error ? e.message : '삭제하지 못했습니다.');
    }
  }

  if (err && !post) return <div className="notice error">{err}</div>;
  if (!post) return <div className="state">불러오는 중…</div>;

  return (
    <>
      <div className="page-header">
        <div style={{ minWidth: 0 }}>
          <span className="badge">{post.boardName}</span>
          <h1 style={{ marginTop: 8 }}>{post.title}</h1>
          <p>{post.authorName} · {post.createdAt} · 조회 {post.viewCount}</p>
        </div>
        {post.mine && (
          <div style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
            <Link to={`/community/posts/${post.id}/edit`} className="btn">수정</Link>
            <button className="btn danger" onClick={removePost}>삭제</button>
          </div>
        )}
      </div>

      {err && <div className="notice error">{err}</div>}

      <div className="panel post-body">{post.content}</div>

      <div className="section-head">
        <h2>댓글</h2>
        <span className="more">{post.commentCount}개</span>
      </div>

      <div className="comment-list">
        {comments.length === 0 && <div className="state">첫 댓글을 남겨 보세요.</div>}
        {comments.map((c) => (
          <div className="card comment" key={c.id}>
            <div className="head">
              <b>{c.authorName}</b>
              <span className="when">{c.createdAt}</span>
              {c.mine && (
                <button className="link-danger" onClick={() => removeComment(c.id)}>삭제</button>
              )}
            </div>
            <p>{c.content}</p>
          </div>
        ))}
      </div>

      <div className="panel comment-form">
        <textarea
          className="area"
          rows={3}
          maxLength={1000}
          placeholder={me ? '댓글을 입력하세요' : '로그인하면 댓글을 남길 수 있습니다'}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
        />
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 10 }}>
          <button className="btn primary" onClick={submitComment} disabled={busy}>
            {me ? '댓글 남기기' : '로그인하고 댓글 남기기'}
          </button>
        </div>
      </div>
    </>
  );
}
