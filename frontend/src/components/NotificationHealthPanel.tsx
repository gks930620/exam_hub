import { useEffect, useState } from 'react';
import { examApi } from '../api/exams';
import type { NotificationHealthResponse } from '../api/types';

/**
 * 알림 건강 — <b>약속이 지켜지고 있나</b>.
 *
 * <p>이 서비스의 약속은 하나다: 접수 마감을 놓치지 않게 알려 준다. 수집이 잘 돌아도 알림이 안 나가면
 * 서비스는 아무 일도 안 한 것이다. 그래서 수집 상태 위에 둔다 — <b>결과가 먼저고 재료가 다음이다.</b>
 *
 * <p>여기서 보는 건 성공률이 아니라 <b>도달</b>이다. 발송 체인의 마지막 LOG 채널은 서버 로그에
 * 한 줄 찍고 언제나 성공을 돌려주기 때문에, 성공률만 보면 <b>아무도 못 받은 날도 100%</b> 다.
 *
 * <p>수집 표와 따로 불러온다 — 한쪽이 실패해도 다른 쪽은 보여야 한다.
 */
export default function NotificationHealthPanel() {
  const [data, setData] = useState<NotificationHealthResponse | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    examApi.adminNotificationHealth()
      .then(setData)
      .catch((e) => setErr(e instanceof Error ? e.message : '불러오지 못했습니다.'));
  }, []);

  if (err) {
    return (
      <div className="k-alert k-alert--warn fold-panel-alert" role="status">
        <span>알림 상태를 불러오지 못했습니다 — {err}</span>
      </div>
    );
  }
  if (!data) return null;

  const h = data.health;
  // 0 인 숫자는 안 쓴다 — 화면에 남은 숫자는 전부 손댈 거리가 있는 것이다(덜어내기).
  const figures: { label: string; value: number; warn?: boolean }[] = [
    { label: '도달', value: h.delivered },
    { label: '대기', value: h.upcoming },
  ];
  if (h.overdue > 0) figures.push({ label: '밀림', value: h.overdue, warn: true });
  if (h.loggedOnly > 0) figures.push({ label: '로그만', value: h.loggedOnly, warn: true });
  if (h.failed > 0) figures.push({ label: '실패', value: h.failed, warn: true });

  const onlyLog = data.liveChannels.length > 0
    && data.liveChannels.every((c) => c === 'LOG');

  return (
    <section className="k-card nh-panel" aria-label="알림 상태">
      <div className="nh-head">
        <h2 className="nh-title">알림</h2>
        <p className="nh-sub">최근 {data.lookbackDays}일 · 실제로 사람에게 닿은 것만 셉니다</p>
      </div>

      <div className={`k-alert ${h.needsAttention ? 'k-alert--err' : ''} nh-msg`}
           role={h.needsAttention ? 'alert' : 'status'}>
        <span>{h.message}</span>
      </div>

      <dl className="nh-figures">
        {figures.map((f) => (
          <div key={f.label} className={`nh-fig${f.warn ? ' warn' : ''}`}>
            <dt>{f.label}</dt>
            <dd className="k-num">{f.value.toLocaleString()}</dd>
          </div>
        ))}
      </dl>

      <p className="nh-channels">
        <span className="k-dim">켜진 발송 수단</span>
        {data.liveChannels.map((c) => (
          <span key={c} className={`k-badge${c === 'LOG' ? ' k-badge--warn' : ' k-badge--ok'}`}>{c}</span>
        ))}
        {onlyLog && (
          <span className="nh-note">
            LOG 는 서버 로그에 적기만 합니다 — 사람에게 가는 수단이 아직 없습니다.
          </span>
        )}
      </p>
    </section>
  );
}
