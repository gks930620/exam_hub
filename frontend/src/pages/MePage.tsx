import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { examApi } from '../api/exams';
import { useAuth } from '../auth';

// 내 정보 — 닉네임, 알림 받을 곳(이메일·휴대폰), 탈퇴.
//
// 알림은 지금 **이메일로만** 나간다. 카톡(알림톡)은 사업자등록이 필요해 나중이다.
// 카카오 로그인은 이메일을 주지 않으므로(비즈 앱 전환 후에만 가능) 여기서 직접 받는다 —
// 이게 없으면 카카오로 가입한 사람은 알림을 아예 못 받는다.
function formatPhone(digits: string): string {
  const d = digits.replace(/[^0-9]/g, '');
  if (d.length < 4) return d;
  if (d.length < 8) return `${d.slice(0, 3)}-${d.slice(3)}`;
  return `${d.slice(0, 3)}-${d.slice(3, d.length - 4)}-${d.slice(d.length - 4)}`;
}

export default function MePage() {
  const { me, refresh, logout } = useAuth();
  const navigate = useNavigate();
  const [nickname, setNickname] = useState(me?.nickname ?? '');
  const [email, setEmail] = useState(me?.email ?? '');
  const [phone, setPhone] = useState(formatPhone(me?.phoneNumber ?? ''));
  const [msg, setMsg] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  if (!me) return <div className="state">불러오는 중…</div>;

  async function run(fn: () => Promise<unknown>, ok: string) {
    setSaving(true); setMsg(null); setErr(null);
    try {
      await fn();
      await refresh();
      setMsg(ok);
    } catch (e) {
      setErr(e instanceof Error ? e.message : '저장하지 못했습니다.');
    } finally {
      setSaving(false);
    }
  }

  async function withdraw() {
    if (!confirm('정말 탈퇴할까요? 등록한 시험과 알림 설정이 사라집니다.\n작성한 글과 댓글은 남고 작성자만 "탈퇴한 사용자"로 바뀝니다.')) return;
    try {
      await examApi.withdraw();
      logout();
      navigate('/', { replace: true });
    } catch (e) {
      setErr(e instanceof Error ? e.message : '탈퇴하지 못했습니다.');
    }
  }

  const hasEmail = Boolean(me.email);

  return (
    <>
      <div className="page-header">
        <div className="page-avatar" aria-hidden="true">👤</div>
        <div>
          <h1>내 정보</h1>
          <p>{me.provider === 'KAKAO' ? '카카오' : '구글'} 계정으로 로그인했습니다.</p>
        </div>
      </div>

      {err && <div className="notice error">{err}</div>}
      {msg && <div className="notice ok">{msg}</div>}

      {/* 알림이 실제로 갈 수 있는 상태인지 한눈에 */}
      <div className={`notice ${hasEmail ? 'info' : 'warn'}`}>
        {hasEmail
          ? <>접수 시작·마감과 시험일 알림이 <b>{me.email}</b> 으로 갑니다.</>
          : <><b>알림을 받을 수 없는 상태입니다.</b> 아래에 이메일을 넣어 주세요.
              {me.provider === 'KAKAO' && ' 카카오 계정은 이메일을 알려주지 않아 직접 입력이 필요합니다.'}</>}
      </div>

      <div className="section-head"><h2>알림 받을 곳</h2></div>
      <div className="panel" style={{ padding: 20 }}>
        <div className="field">
          <span>이메일 *</span>
          <input className="input" type="email" value={email} placeholder="name@example.com"
                 onChange={(e) => setEmail(e.target.value)} />
        </div>
        <p className="fineprint" style={{ margin: '0 0 14px' }}>
          지금은 알림이 이메일로만 갑니다. 비우면 알림을 받을 수 없습니다.
        </p>
        <button className="btn primary" onClick={() => run(() => examApi.changeEmail(email.trim()), '이메일을 저장했습니다.')}
                disabled={saving}>
          {saving ? '저장 중…' : '이메일 저장'}
        </button>

        <div className="field" style={{ marginTop: 26 }}>
          <span>휴대폰번호 (지금은 안 씀)</span>
          <input className="input" value={phone} inputMode="numeric" placeholder="010-1234-5678"
                 onChange={(e) => setPhone(formatPhone(e.target.value))} />
        </div>
        <p className="fineprint" style={{ margin: '0 0 14px' }}>
          카카오톡 알림(알림톡)을 붙일 때 쓰려고 미리 받아 둡니다. 지금은 발송하지 않습니다.
        </p>
        <button className="btn" onClick={() => run(() => examApi.changePhone(phone.replace(/[^0-9]/g, '')), '번호를 저장했습니다.')}
                disabled={saving}>
          번호 저장
        </button>
      </div>

      <div className="section-head"><h2>프로필</h2></div>
      <div className="panel" style={{ padding: 20 }}>
        <div className="field">
          <span>닉네임 (커뮤니티 표시명)</span>
          <input className="input" value={nickname} maxLength={30}
                 onChange={(e) => setNickname(e.target.value)} />
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="btn primary"
                  onClick={() => run(() => examApi.changeNickname(nickname.trim()), '저장했습니다.')}
                  disabled={saving || !nickname.trim()}>
            저장
          </button>
          <button className="btn" onClick={logout}>로그아웃</button>
        </div>
      </div>

      <div className="section-head"><h2>계정 삭제</h2></div>
      <div className="panel" style={{ padding: 20 }}>
        <p className="fineprint" style={{ margin: '0 0 14px' }}>
          탈퇴하면 등록한 시험과 알림 설정이 사라집니다. 작성한 글·댓글은 남고 작성자만 가려집니다.
        </p>
        <button className="btn danger" onClick={withdraw}>회원 탈퇴</button>
      </div>
    </>
  );
}
