import { useEffect, useState } from 'react';
import { examApi } from '../api/exams';
import { fmtAt } from '../lib/format';
import Skeleton from '../components/Skeleton';
import NotificationHealthPanel from '../components/NotificationHealthPanel';
import type { CollectHealthResponse, CollectHealthRow } from '../api/types';

/**
 * 수집 건강 — <b>"지금 뭐가 고장났나"</b>.
 *
 * <p>이 화면이 없을 때가 이 서비스의 가장 큰 위험이었다. 스크래퍼가 깨져도 DB 에는 옛 일정이
 * 남아 있어 <b>사용자 화면은 멀쩡해 보인다.</b> 그 사이 사용자는 지난 날짜를 믿고 준비한다.
 * 수집 결과는 서버 로그에만 남아서 매니저가 볼 방법이 없었다.
 *
 * <p>그래서 조용한 고장까지 센다 — 실패뿐 아니라 <b>오류 없이 0건</b>(시행처가 화면을 바꿈)과
 * <b>오래 안 돔</b>(배치가 멈춤)도 손봐야 할 것으로 본다.
 */
const TONE: Record<string, string> = {
  OK: 'k-badge--ok',
  FAILED: 'k-badge--err',
  EMPTY: 'k-badge--warn',
  STALE: 'k-badge--warn',
  NEVER_RAN: 'k-badge--warn',
};

export default function AdminCollectHealth() {
  const [data, setData] = useState<CollectHealthResponse | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    examApi.adminCollectHealth()
      .then(setData)
      .catch((e) => setErr(e instanceof Error ? e.message : '불러오지 못했습니다.'));
  }, []);

  // 알림과 수집은 파이프의 양끝이라 각자 불러오고 각자 실패한다 —
  // 수집을 못 불러왔다고 알림 상태까지 감추면, 정작 급한 쪽을 못 본다.
  if (err) return <><NotificationHealthPanel /><div className="k-alert k-alert--err" role="alert">{err}</div></>;
  if (!data) return <><NotificationHealthPanel /><Skeleton rows={6} label="수집 상태 불러오는 중…" /></>;

  return (
    <>
      <NotificationHealthPanel />

      <p className="fineprint fineprint--lead">
        매일 05:00 배치가 시행처를 한 번씩 읽습니다. <b>오류 없이 0건</b>이 오면 시행처가 화면을
        바꿨을 수 있어 고장으로 봅니다 — 그대로 두면 옛 일정이 계속 서비스됩니다.
        종목을 지정해 다시 받은 기록(부분 수집)은 0건이 정상이라 여기서 빼고 봅니다.
      </p>

      {data.needsAttention > 0 ? (
        <div className="k-alert k-alert--warn fold-panel-alert" role="alert">
          <span>
            <b>{data.needsAttention}개 소스를 손봐야 합니다.</b> 아래 맨 위에 모아 두었습니다.
            원본 사이트를 열어 화면이 바뀌었는지 보고, 바뀌었으면 개발자에게 알려 주세요.
          </span>
        </div>
      ) : (
        <div className="k-alert fold-panel-alert" role="status">
          <span><b>전부 정상입니다.</b> {data.total}개 소스가 최근 배치에서 일정을 받아왔습니다.</span>
        </div>
      )}

      <div className="k-card k-card--flush k-tablewrap">
        <table className="k-table data-table">
          <thead>
            <tr><th>소스</th><th>상태</th><th>마지막 수집</th><th className="k-num">건수</th><th>메모</th></tr>
          </thead>
          <tbody>
            {data.items.map((r) => <Row key={r.source} row={r} />)}
          </tbody>
        </table>
      </div>
    </>
  );
}

function Row({ row }: { row: CollectHealthRow }) {
  return (
    <tr>
      <td className="round">{row.source}</td>
      <td><span className={`k-badge ${TONE[row.state] ?? ''}`}>{row.stateLabel}</span></td>
      <td className="nowrap">{row.lastRunAt ? fmtAt(row.lastRunAt) : '—'}</td>
      <td className="k-num">{row.lastRunAt ? row.fetched.toLocaleString() : '—'}</td>
      <td className="cell-sub">{row.message}</td>
    </tr>
  );
}
