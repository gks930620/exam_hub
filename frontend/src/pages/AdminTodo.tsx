import { useEffect, useState } from 'react';
import { examApi } from '../api/exams';
import AdminOverview from './AdminOverview';
import type { DataMapResponse } from '../api/types';

/**
 * 운영 첫 화면 — 위에는 "얼마나 채워졌나"(채움률), 아래는 "무엇을 해야 하나"(현황).
 *
 * <p>채움률의 숫자는 모두 같은 모집단(화면에 보이는 시험, 상시 제외)을 센다.
 * 할 일의 개수와 행동별 내역은 현황 표가 스스로 보여준다(같은 API 한 번으로).
 */
export default function AdminTodo() {
  const [map, setMap] = useState<DataMapResponse | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    // 실패하면 띠를 숨기지 않고 말한다 — 조용히 사라지면 "원래 없는 화면인가" 하고 넘어간다
    examApi.adminDataMap().then(setMap).catch((e: Error) => setErr(e.message));
  }, []);

  const c = map?.coverage;
  const pct = c && c.totalExams > 0 ? Math.round((c.withSchedule / c.totalExams) * 100) : null;

  return (
    <>
      {err && <div className="k-alert k-alert--err" role="alert">채움률을 불러오지 못했습니다: {err}</div>}

      {c && (
        <div className="k-card cover-strip">
          <div className="cover-nums">
            <b>{c.withSchedule.toLocaleString()}</b>
            <span> / {c.totalExams.toLocaleString()}종에 일정이 있습니다</span>
            {pct != null && <em>{pct}%</em>}
          </div>
          <div className="k-bar cover-bar" aria-hidden="true">
            <span style={{ width: `${pct ?? 0}%` }} />
          </div>
          <p className="fineprint" style={{ margin: '10px 0 0' }}>
            일정이 없는 {c.withoutSchedule.toLocaleString()}종은 사용자에게 <b>일정 미정</b>으로 보입니다.
            {c.rolling > 0 && <> 상시·예약제 {c.rolling.toLocaleString()}종은 일정이라는 것이 없어 뺐습니다.</>}
            {' '}무엇을 해야 하는지는 아래 <b>할 일</b>에 있습니다.
          </p>
        </div>
      )}

      <AdminOverview />
    </>
  );
}
