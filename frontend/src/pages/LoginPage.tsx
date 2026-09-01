import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth';

// 로그인 — 소셜 단독(카카오·구글). 자체 계정은 만들지 않는다(설계 08).
export default function LoginPage() {
  const { me, loading, login } = useAuth();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from;

  if (loading) return <div className="state">불러오는 중…</div>;
  if (me) return <Navigate to="/" replace />;

  return (
    <div className="login-wrap">
      <div className="page-header" style={{ justifyContent: 'center' }}>
        <div>
          <h1>로그인</h1>
          <p>
            {from
              ? '이 기능은 로그인이 필요합니다.'
              : '내 시험·알림·커뮤니티 글쓰기를 쓰려면 로그인하세요.'}
          </p>
        </div>
      </div>

      <div className="panel login-card">
        <button className="social kakao" onClick={() => login('kakao')}>
          <span aria-hidden="true">💬</span> 카카오로 시작하기
        </button>
        <button className="social google" onClick={() => login('google')}>
          <span aria-hidden="true">G</span> 구글로 시작하기
        </button>
        <p className="fineprint" style={{ marginTop: 18 }}>
          시험 검색과 커뮤니티 읽기는 로그인 없이도 됩니다.
          <br />처음 로그인하면 자동으로 가입됩니다.
        </p>
      </div>
    </div>
  );
}
