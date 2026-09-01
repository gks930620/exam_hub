import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { examApi } from '../api/exams';
import { useAuth } from '../auth';

/**
 * 매니저(운영자) 로그인 — 아이디/비밀번호만.
 *
 * 여기에 소셜 버튼을 두지 않는 것이 핵심이다. 운영 계정이 카카오·구글에 묶이면
 * 그쪽이 막히는 날 운영 자체가 멈추고, 인수인계도 개인 SNS 계정을 넘기는 꼴이 된다.
 * 계정은 가입이 아니라 서버 환경변수(MANAGER_USERNAME/PASSWORD)로만 만들어진다.
 */
export default function ManagerLoginPage() {
  const { me, loginWithToken } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [configured, setConfigured] = useState<boolean | null>(null);

  useEffect(() => {
    examApi.managerAvailable()
      .then((r) => setConfigured(r.configured))
      .catch(() => setConfigured(null));   // 못 물어봐도 로그인 시도는 막지 않는다
  }, []);

  // 이미 매니저로 로그인돼 있으면 바로 운영 화면으로
  useEffect(() => {
    if (me?.role === 'ADMIN') navigate('/admin', { replace: true });
  }, [me, navigate]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true); setErr(null);
    try {
      const { token } = await examApi.managerLogin(username.trim(), password);
      await loginWithToken(token);
      navigate('/admin', { replace: true });
    } catch (e2) {
      setErr(e2 instanceof Error ? e2.message : '로그인하지 못했습니다.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login-wrap">
      <div className="login-card">
        <h1>매니저 로그인</h1>
        <p className="fineprint" style={{ margin: '6px 0 22px' }}>
          운영자 전용입니다. 일반 이용자는 <a href="/login">이쪽</a>에서 카카오로 로그인하세요.
        </p>

        {configured === false && (
          <div className="notice warn" style={{ marginBottom: 16 }}>
            <b>매니저 계정이 아직 없습니다.</b> 서버의 <code>.env</code> 에
            <code> MANAGER_USERNAME</code> · <code>MANAGER_PASSWORD</code> 를 넣고 재기동하세요.
          </div>
        )}
        {err && <div className="notice error" style={{ marginBottom: 16 }}>{err}</div>}

        <form onSubmit={submit}>
          <div className="field">
            <span>아이디</span>
            <input className="input" value={username} autoComplete="username"
                   onChange={(e) => setUsername(e.target.value)} />
          </div>
          <div className="field">
            <span>비밀번호</span>
            <input className="input" type="password" value={password} autoComplete="current-password"
                   onChange={(e) => setPassword(e.target.value)} />
          </div>
          <button className="btn primary lg" type="submit" style={{ width: '100%' }}
                  disabled={busy || !username.trim() || !password}>
            {busy ? '확인 중…' : '로그인'}
          </button>
        </form>

        <p className="fineprint">
          비밀번호를 잊었으면 서버 <code>.env</code> 의 <code>MANAGER_PASSWORD</code> 를 바꾸고
          재기동하면 그 값으로 갱신됩니다.
        </p>
      </div>
    </div>
  );
}
