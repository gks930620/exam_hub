import { useEffect, useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth';
import { api } from '../api/client';

// 로그인 — 소셜 단독(카카오·구글). 자체 계정은 만들지 않는다(설계 08).
//
// 버튼은 서버에 실제로 등록된 제공자만 그린다. 키를 안 넣은 제공자는 등록 자체가 없어서
// 누르면 흰 화면 500(Invalid Client Registration)이 뜬다 — 키를 하나만 넣은 상태는
// 이 서비스의 정상 상태라(구글은 Client Secret 대기 중) 그대로 두면 반드시 밟는다.

const ALL = [
  { id: 'kakao', icon: '💬', label: '카카오로 시작하기' },
  { id: 'google', icon: 'G', label: '구글로 시작하기' },
] as const;

export default function LoginPage() {
  const { me, loading, login } = useAuth();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from;

  // null = 아직 모름. 조회에 실패해도 null 로 두고 전부 그린다 —
  // 눌러서 실패하는 편이, 멀쩡한 제공자까지 막아 아무도 못 들어오는 것보다 낫다.
  const [available, setAvailable] = useState<string[] | null>(null);
  const [asked, setAsked] = useState(false);

  useEffect(() => {
    let alive = true;
    api.get<{ providers: string[] }>('/api/auth/providers')
      .then((res) => { if (alive) setAvailable(res.providers); })
      .catch(() => { if (alive) setAvailable(null); })
      .finally(() => { if (alive) setAsked(true); });
    return () => { alive = false; };
  }, []);

  if (loading || !asked) return <div className="k-empty state">불러오는 중…</div>;
  if (me) return <Navigate to="/" replace />;

  const usable = available === null ? ALL : ALL.filter((p) => available.includes(p.id));

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

      <div className="k-card login-card">
        {usable.length === 0 ? (
          <p className="k-alert k-alert--warn">
            지금은 로그인을 쓸 수 없습니다. 소셜 로그인 키가 설정되지 않았습니다.
            <br />시험 검색과 커뮤니티 읽기는 그대로 이용하실 수 있습니다.
          </p>
        ) : (
          usable.map((p) => (
            <button key={p.id} className={`social ${p.id}`} onClick={() => login(p.id, from)}>
              <span aria-hidden="true">{p.icon}</span> {p.label}
            </button>
          ))
        )}
        <p className="fineprint" style={{ marginTop: 18 }}>
          시험 검색과 커뮤니티 읽기는 로그인 없이도 됩니다.
          <br />처음 로그인하면 자동으로 가입됩니다.
        </p>
      </div>
    </div>
  );
}
