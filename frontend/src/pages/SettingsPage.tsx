import { useEffect, useState } from 'react';
import { examApi } from '../api/exams';
import type { NotifySettings } from '../api/types';

// 알림 설정: 이벤트 유형별 on/off.
// 서버에 저장된 현재값을 먼저 읽어 초기값으로 쓴다(예전엔 무조건 전체 ON 으로 시작해 실제 상태를 잘못 보여줬다).
const ROWS: { key: keyof NotifySettings; title: string; desc: string }[] = [
  { key: 'notifyReg', title: '원서접수 알림', desc: '접수 시작 전날·당일, 마감 전날 오전 9시' },
  { key: 'notifyExam', title: '시험일 알림', desc: '시험 7일 전과 하루 전 오전 9시' },
  { key: 'notifyChange', title: '일정 변경 알림', desc: '연기·취소가 확인되면 즉시' },
];

export default function SettingsPage() {
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

  return (
    <>
      <div className="page-header">
        <div className="page-avatar" aria-hidden="true">⚙</div>
        <div>
          <h1>알림 설정</h1>
          <p>받고 싶은 알림만 켜 두세요. 이 브라우저에만 저장됩니다.</p>
        </div>
      </div>

      {loadError && <div className="k-alert k-alert--err">설정을 불러오지 못했습니다: {loadError}</div>}
      {!s && !loadError && <div className="k-empty state">불러오는 중…</div>}

      {s && (
        <>
          <div className="k-card">
            <div className="panel-head">
              <span>수신 항목</span>
              <span>{saving ? '저장 중…' : msg}</span>
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
            알림은 웹 푸시 또는 이메일로 보낼 예정입니다. 지금은 서버가 발송 기록만 남기는 단계라 실제로 도착하지 않습니다.
            <br />계정 없이 기기 식별자로만 구분하므로, 다른 브라우저에서는 설정이 따로 관리됩니다.
          </p>
        </>
      )}
    </>
  );
}
