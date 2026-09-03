import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { examApi } from '../api/exams';
import { useAuth } from '../auth';
import type { NotifySettings } from '../api/types';
import Icon from '../components/Icon';

// 알림 설정: 이벤트 유형별 on/off. 계정에 저장된다(설계 08).
// 서버에 저장된 현재값을 먼저 읽어 초기값으로 쓴다(예전엔 무조건 전체 ON 으로 시작해 실제 상태를 잘못 보여줬다).
//
// 발송 시각은 서버 NotificationScheduleService 가 정한다 — 화면 문구는 그 값과 같아야 한다.
// 접수 시작 전날 20:00·당일 09:00, 마감 전날 20:00, 시험 7일 전 09:00·하루 전 20:00.
const ROWS: { key: keyof NotifySettings; title: string; desc: string }[] = [
  { key: 'notifyReg', title: '원서접수 알림', desc: '접수 시작 전날 20:00·당일 09:00, 마감 전날 20:00' },
  { key: 'notifyExam', title: '시험일 알림', desc: '시험 7일 전 09:00, 하루 전 20:00' },
  { key: 'notifyChange', title: '일정 변경 알림', desc: '연기·취소가 확인되면 즉시' },
];

export default function SettingsPage() {
  const { me } = useAuth();
  const [s, setS] = useState<NotifySettings | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let alive = true;
    examApi.notifySettings()
      .then((v) => { if (alive) setS(v); })
      .catch((e: Error) => { if (alive) setLoadError(e.message); });
    return () => { alive = false; };
  }, []);

  async function save(next: NotifySettings) {
    const prev = s;
    setS(next); // 낙관적 반영
    setSaving(true);
    setMsg(null);
    try {
      const saved = await examApi.saveNotifySettings(next);
      setS(saved); // 서버가 확정한 값으로 맞춘다
      setMsg('저장했습니다.');
    } catch (e) {
      setS(prev); // 실패하면 되돌린다 — 껐는데 켜져 보이는 상태를 만들지 않는다
      setMsg(e instanceof Error ? e.message : '저장하지 못했습니다.');
    } finally {
      setSaving(false);
    }
  }

  const email = me?.email ?? null;

  return (
    <>
      <div className="page-header">
        <div className="page-avatar" aria-hidden="true"><Icon name="settings" size={22} /></div>
        <div className="page-header__text">
          <h1>알림 설정</h1>
          <p>받고 싶은 알림만 켜 두세요. 계정에 저장됩니다.</p>
        </div>
      </div>

      {/* 이메일이 없으면 켜 둔 알림이 전부 허공에 간다 — 어디서 넣는지까지 알려 준다 */}
      {me && !email && (
        <div className="k-alert k-alert--warn" role="alert">
          <span>
            <b>알림을 받을 이메일이 없습니다.</b> 켜 둔 알림이 갈 곳이 없습니다.{' '}
            <Link to="/me">내 정보에서 입력하기</Link>
          </span>
        </div>
      )}

      {loadError && <div className="k-alert k-alert--err" role="alert">설정을 불러오지 못했습니다: {loadError}</div>}
      {!s && !loadError && <div className="k-empty state" role="status">불러오는 중…</div>}

      {s && (
        <>
          <div className="k-card">
            <div className="panel-head">
              <span>수신 항목</span>
              <span role="status">{saving ? '저장 중…' : msg}</span>
            </div>
            {ROWS.map((r) => (
              <button
                key={r.key}
                className="switch-row"
                aria-pressed={s[r.key]}
                disabled={saving}
                onClick={() => save({ ...s, [r.key]: !s[r.key] })}
              >
                <span className="label">
                  <b>{r.title}</b>
                  <span>{r.desc}</span>
                </span>
                <span className="switch" aria-hidden="true"><i /></span>
              </button>
            ))}
          </div>

          <p className="fineprint">
            지금은 이메일로 발송합니다{email ? <> — <b>{email}</b> 으로 갑니다.</> : '.'}
            {' '}웹 푸시·카카오 알림톡은 준비 중입니다.
          </p>
        </>
      )}
    </>
  );
}
