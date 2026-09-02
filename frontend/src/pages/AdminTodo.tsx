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
          <div className="cover-breakdown">
            <span className="k-badge k-badge--warn">수기 필수 {c.manualNeeded.toLocaleString()}</span>
            <span className="k-badge">자동 · 공고 전 {c.announcementPending.toLocaleString()}</span>
            <span className="k-badge k-badge--point">자동 · 크롤링 예정 {c.crawlPlanned.toLocaleString()}</span>
            <span className="k-dim">상시 {c.rolling.toLocaleString()}종은 일정 대상 아님</span>
          </div>
          <p className="fineprint" style={{ margin: '10px 0 0' }}>
            일정 없는 {c.withoutSchedule.toLocaleString()}종 가운데 <b>사람이 넣어야 하는 건 수기 필수 {c.manualNeeded.toLocaleString()}종</b>뿐입니다.
            나머지는 공고가 나거나 스크래퍼가 붙으면 자동으로 들어옵니다.
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
