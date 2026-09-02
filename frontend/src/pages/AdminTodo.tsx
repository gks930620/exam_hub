import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { examApi } from '../api/exams';
import AdminOverview from './AdminOverview';
import type { DataMapResponse } from '../api/types';

/**
 * 운영 첫 화면 — <b>무엇을 채워야 하나.</b>
 *
 * <p>맨 위 숫자 세 개는 모두 <b>같은 모집단</b>(화면에 보이는 시험)을 센다. 예전에는 여기 수치가
 * 폐지·개칭까지 포함한 전체였고 아래 목록은 뺀 값이라, 두 숫자가 24 만큼 어긋났다.
 */
export default function AdminTodo() {
  const navigate = useNavigate();
  const [map, setMap] = useState<DataMapResponse | null>(null);

  useEffect(() => {
    examApi.adminDataMap().then(setMap).catch(() => setMap(null));
  }, []);

  const c = map?.coverage;
  const pct = c && c.totalExams > 0 ? Math.round((c.withSchedule / c.totalExams) * 100) : null;

  return (
    <>
      {c && (
        <div className="k-card cover-strip">
          <div className="cover-nums">
            <b>{c.withSchedule.toLocaleString()}</b>
            <span> / {c.totalExams.toLocaleString()}종에 일정이 있습니다</span>
            {pct != null && <em>{pct}%</em>}
          </div>
          <div className="cover-bar" aria-hidden="true">
            <span style={{ width: `${pct ?? 0}%` }} />
          </div>
          <p className="fineprint" style={{ margin: '10px 0 0' }}>
            일정이 없는 <b>{c.withoutSchedule.toLocaleString()}종</b>은 화면에 <b>일정 미정</b>으로 뜹니다.
            검색·관심등록은 되지만 <b>D-day 와 알림은 못 갑니다</b> — 이 숫자를 줄이는 게 이 화면의 목적입니다.
            {c.rolling > 0 && <> 상시·예약제 <b>{c.rolling.toLocaleString()}종</b>은 일정이라는 것이 없어 여기서 뺐습니다.</>}
          </p>
        </div>
      )}

      <AdminOverview
        onPick={(id, name) =>
          navigate(`/admin/write?cert=${id}&name=${encodeURIComponent(name)}`)}
      />
    </>
  );
}
