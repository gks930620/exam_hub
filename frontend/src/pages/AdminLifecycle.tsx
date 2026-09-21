import { useEffect, useState } from 'react';
import { examApi } from '../api/exams';
import type { LifecycleResponse } from '../api/types';
import Icon from '../components/Icon';

/**
 * 시험 변천사 — 폐지·개칭된 시험이 어디로 갔나.
 *
 * 폐지된 시험을 그냥 지우면 <b>있었다는 사실까지 사라진다.</b> "웹디자인기능사 왜 없죠?"에
 * 답할 근거가 없고, 관심 등록해 둔 사람에게 알림이 왜 안 가는지도 설명하지 못한다.
 * 사용자 화면에서만 빼고 기록은 여기 남긴다.
 */
export default function AdminLifecycle() {
  const [data, setData] = useState<LifecycleResponse | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    examApi.adminLifecycle()
      .then(setData)
      .catch((e) => setErr(e instanceof Error ? e.message : '불러오지 못했습니다.'));
  }, []);

  if (err) return <div className="k-alert k-alert--err" role="alert">{err}</div>;
  if (!data) return <div className="day-list" role="status" aria-label="변천사 불러오는 중">{Array.from({ length: 4 }, (_, i) => <div className="k-skeleton row-skeleton" key={i} />)}</div>;
  if (data.items.length === 0) {
    return <p className="fineprint fineprint--flush">목록에서 뺀 시험이 없습니다.</p>;
  }

  return (
    <>
      <button className="k-btn k-btn--secondary fold-toggle"
              onClick={() => setOpen((v) => !v)}>
        목록에서 뺀 시험 {data.items.length}건
        {data.needsCheck > 0 && (
          <span className="k-badge k-badge--warn">확인 필요 {data.needsCheck}</span>
        )}
        <span className="caret"><Icon name={open ? 'chevronDown' : 'chevronRight'} size={16} /></span>
      </button>

      {open && (
        <div className="k-card fold-panel">
          <p className="fineprint">
            폐지·개칭이 확정됐거나 <b>우리가 다루지 못하는</b> 시험은 <b>검색·목록에서 빠져 있습니다</b>
            (오지 않을 접수를 기다리게 두지 않으려고). 기록은 여기 남아 있어서 "왜 없어졌는지" 답할 수 있습니다.
          </p>
          <div className="k-alert">
            <span>
              <b>다루지 않음</b>은 <b>폐지가 아닙니다</b>. 시험은 지금도 치르는데 시행처가 일정을 공개하는
              곳을 못 찾아 뺀 것입니다. 근거는 오른쪽 칸에 있고, 시행처 사이트가 확인되면 되돌릴 수 있습니다.
            </span>
          </div>
          <div className="k-alert k-alert--warn">
            <span>
              <b>확인 필요</b>가 붙은 것은 <b>큐넷 목록에 없다는 것만</b> 확인된 상태라 <b>아직 검색에 나옵니다</b>.
              시행처가 큐넷이 아니라서 없는 것일 수도 있습니다 —
              컴퓨터활용능력은 대한상공회의소 시행이라 큐넷에 없지만 멀쩡히 살아 있습니다.
              시행처 사이트를 보고 판단해 주세요.
            </span>
          </div>

          <div className="k-tablewrap">
            <table className="k-table data-table">
              <thead>
                <tr><th>시험</th><th>상태</th><th>지금은</th><th>근거</th></tr>
              </thead>
              <tbody>
                {data.items.map((r) => (
                  <tr key={r.certificateId}>
                    <td>
                      <b>{r.name}</b>
                      {r.category && <div className="cell-sub">{r.category}</div>}
                    </td>
                    <td>
                      <span className={`k-badge${r.lifecycle === 'UNVERIFIED' ? ' k-badge--warn' : r.lifecycle === 'EXCLUDED' ? ' k-badge--point' : ''}`}>
                        {r.lifecycleLabel}
                      </span>
                    </td>
                    <td className="cell-detail">{r.supersededBy ?? '—'}</td>
                    <td className="cell-sub">{r.note ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </>
  );
}
