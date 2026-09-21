import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { examApi } from '../api/exams';
import { fmtAt } from '../lib/format';
import { useAuth, useRequireLogin } from '../auth';
import Skeleton from '../components/Skeleton';
import type { CommentItem, PostDetail } from '../api/types';

// 글 상세 + 댓글. 수정/삭제 버튼은 서버가 내려주는 mine 플래그로만 띄운다
// (프런트가 작성자 id 를 비교하지 않는다 — 판단 주체는 서버여야 한다).
export default function PostDetailPage() {
  const { id } = useParams();
  const postId = Number(id);
  const navigate = useNavigate();
  const { me } = useAuth();
  const requireLogin = useRequireLogin();

  const [post, setPost] = useState<PostDetail | null>(null);
  // null = 아직 안 옴. [] 로 시작하면 조회가 죽어도 "첫 댓글을 남겨 보세요"가 뜬다
  const [comments, setComments] = useState<CommentItem[] | null>(null);
  const [commentsErr, setCommentsErr] = useState<string | null>(null);
  const [draft, setDraft] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let alive = true;
    setPost(null); setComments(null); setCommentsErr(null); setErr(null);
    examApi.post(postId)
      .then((p) => { if (alive) setPost(p); })
      .catch((e: Error) => { if (alive) setErr(e.message); });
    examApi.comments(postId)
      .then((r) => { if (alive) setComments(r.items); })
      .catch((e: Error) => { if (alive) setCommentsErr(e.message); });
    return () => { alive = false; };
  }, [postId]);

  async function submitComment() {
    if (!me) {
      requireLogin();
      return;
    }
    const content = draft.trim();
    if (!content) return;
    setBusy(true);
    try {
      const created = await examApi.addComment(postId, content);
      setComments((prev) => [...(prev ?? []), created]);
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
      setComments((prev) => (prev ?? []).filter((c) => c.id !== commentId));
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

  if (err && !post) return <div className="k-alert k-alert--err" role="alert">{err}</div>;
  // 첫 로딩은 스켈레톤 — 본문 카드 자리를 먼저 잡는다(설계 05 §8)
  if (!post) return <Skeleton kind="panel" rows={1} label="글 불러오는 중…" />;

  return (
    <>
      <div className="page-header">
        <div className="page-header__text">
          <span className="k-badge">{post.boardName}</span>
          <h1 className="post-title">{post.title}</h1>
          <p>{post.authorName} · {fmtAt(post.createdAt)} · 조회 {post.viewCount}</p>
        </div>
        {post.mine && (
          <div className="page-header__actions">
            <Link to={`/community/posts/${post.id}/edit`} className="k-btn k-btn--secondary">수정</Link>
            <button className="k-btn k-btn--danger" onClick={removePost}>삭제</button>
          </div>
        )}
      </div>

      {err && <div className="k-alert k-alert--err" role="alert">{err}</div>}

      <div className="k-card post-body">{post.content}</div>

      <div className="section-head">
        <h2>댓글</h2>
        <span className="more">{post.commentCount}개</span>
      </div>

      <div className="comment-list">
        {commentsErr ? (
          <div className="k-alert k-alert--err" role="alert">댓글을 불러오지 못했습니다: {commentsErr}</div>
        ) : comments === null ? (
          <Skeleton rows={2} label="댓글을 불러오는 중…" />
        ) : comments.length === 0 ? (
          <div className="k-empty state">첫 댓글을 남겨 보세요.</div>
        ) : (
          comments.map((c) => (
            <div className="k-card comment" key={c.id}>
              <div className="head">
                <b>{c.authorName}</b>
                <span className="when">{fmtAt(c.createdAt)}</span>
                {c.mine && (
                  <button className="link-danger" onClick={() => removeComment(c.id)}>삭제</button>
                )}
              </div>
              <p>{c.content}</p>
            </div>
          ))
        )}
      </div>

      <div className="k-card comment-form">
        <textarea
          className="k-textarea"
          rows={3}
          maxLength={1000}
          aria-label="댓글"
          placeholder={me ? '댓글을 입력하세요' : '로그인하면 댓글을 남길 수 있습니다'}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
        />
        <div className="pager-row pager-row--right">
          <button className="k-btn k-btn--primary" onClick={submitComment} disabled={busy}>
            {me ? '댓글 남기기' : '로그인하고 댓글 남기기'}
          </button>
        </div>
      </div>
    </>
  );
}
