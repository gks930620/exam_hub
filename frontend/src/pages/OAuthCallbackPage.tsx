import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { consumeTokenFromHash, useAuth } from '../auth';

// 소셜 로그인 콜백 — 서버가 #token=... 으로 돌려보낸다(쿼리가 아닌 이유는 auth.tsx 주석 참고).
export default function OAuthCallbackPage() {
  const navigate = useNavigate();
  const { refresh } = useAuth();
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const ok = consumeTokenFromHash();
    if (!ok) {
      setFailed(true);
      return;
    }
    void refresh().then(() => {
      const back = sessionStorage.getItem('afterLogin') || '/';
      sessionStorage.removeItem('afterLogin');
      navigate(back, { replace: true });
    });
  }, [navigate, refresh]);

  if (failed) {
    return (
      <div className="k-empty state">
        <span className="big">로그인을 마치지 못했습니다</span>
        토큰을 받지 못했습니다. 다시 시도해 주세요.
        <div className="empty-cta">
          <button className="k-btn k-btn--primary" onClick={() => navigate('/login', { replace: true })}>
            로그인으로 돌아가기
          </button>
        </div>
      </div>
    );
  }
  return <div className="k-empty state">로그인 중…</div>;
}
