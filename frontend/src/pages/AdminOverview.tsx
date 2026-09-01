import { useCallback, useEffect, useState } from 'react';
import { examApi } from '../api/exams';
import type { OverviewResponse, OverviewRow, ScheduleStatusKind } from '../api/types';

/**
 * 시험 일정 현황 — <b>여러 시험의 상태를 한 화면에서</b>.
 *
 * 수기 입력 화면만 있으면 매니저는 "무엇이 비어 있는지" 알 방법이 없다. 480종을 하나씩
 * 검색해 볼 수는 없으니, 결국 기억나는 시험만 채우게 된다. 그래서 <b>급한 것부터</b> 세운다:
 * 일정 없음 → 지난 것만 남음 → 접수 중 → 예정.
 *
 * 행의 [넣기] 를 누르면 아래 입력 폼이 그 시험으로 바로 열린다(다시 검색하지 않는다).
 */
const TABS: { key: ScheduleStatusKind | ''; label: string; hint: string }[] = [
  { key: 'NONE', label: '일정 없음', hint: '한 번도 안 들어온 시험. 여기부터 채웁니다' },
  { key: 'PAST', label: '지난 것만', hint: '끝난 날짜가 화면에 그대로 걸려 있습니다 — 다음 회차를 넣으세요' },
  { key: 'OPEN', label: '접수 중', hint: '지금 접수 기간입니다' },
  { key: 'UPCOMING', label: '예정', hint: '앞으로 있을 일정이 있습니다 — 할 일 없음' },
  { key: '', label: '전체', hint: '' },
];

export default function AdminOverview({ onPick }: { onPick: (id: number, name: string) => void }) {
  const [tab, setTab] = useState<ScheduleStatusKind | ''>('NONE');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<OverviewResponse | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true); setErr(null);
    try {
      setData(await examApi.adminOverview({ status: tab || undefined, query: q.trim() || undefined, page }));
    } catch (e) {
      setErr(e instanceof Error ? e.message : '불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [tab, q, page]);

  useEffect(() => { void load(); }, [load]);

  const counts = data?.counts ?? {};
  const hint = TABS.find((t) => t.key === tab)?.hint ?? '';

  return (
    <>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 12 }}>
        {TABS.map((t) => (
          <button key={t.key || 'ALL'}
                  className={`btn ${tab === t.key ? 'primary' : ''}`}
                  onClick={() => { setTab(t.key); setPage(0); }}>
            {t.label}
            {t.key && counts[t.key] != null && (
              <span style={{ marginLeft: 6, opacity: 0.75 }}>{counts[t.key]}</span>
            )}
          </button>
        ))}
      </div>
      {hint && <p className="fineprint" style={{ margin: '0 0 14px' }}>{hint}</p>}

      <div className="searchbar" style={{ marginBottom: 14 }}>
        <span className="ico" aria-hidden="true">⌕</span>
        <input className="input" placeholder="시험명으로 좁히기"
               value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
      </div>

      {err && <div className="notice error">{err}</div>}
      {loading && !data && <div className="state">불러오는 중…</div>}

      {data && data.items.length === 0 && (
        <div className="state">
          <span className="big">해당하는 시험이 없습니다</span>
          {tab === 'NONE' ? '일정 없는 시험을 다 채웠다는 뜻입니다.' : '다른 탭을 보세요.'}
        </div>
      )}

      {data && data.items.length > 0 && (
        <>
          <div className="panel" style={{ padding: 0, overflowX: 'auto' }}>
            <table className="data-table">
              <thead>
                <tr>
                  <th>시험</th><th>분류</th><th>등록</th><th>다음 일정</th>
                  <th>접수</th><th>시험일</th><th></th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((r) => <OverviewRowView key={r.certificateId} row={r} onPick={onPick} />)}
              </tbody>
            </table>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 12 }}>
            <button className="btn" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>이전</button>
            <span className="fineprint" style={{ margin: 0 }}>
              {data.totalElements.toLocaleString()}종 중 {page * 30 + 1}–{page * 30 + data.items.length}
            </span>
            <button className="btn" disabled={(page + 1) * 30 >= data.totalElements}
                    onClick={() => setPage((p) => p + 1)}>다음</button>
          </div>
        </>
      )}
    </>
  );
}

function OverviewRowView({ row, onPick }: { row: OverviewRow; onPick: (id: number, name: string) => void }) {
  return (
    <tr>
      <td>
        <b>{row.certificateName}</b>
        {row.agency && <div style={{ fontSize: 12, color: 'var(--muted2)' }}>{row.agency}</div>}
      </td>
      <td style={{ fontSize: 12.5, color: 'var(--muted)' }}>{row.category ?? '—'}</td>
      <td><StatusBadge row={row} /></td>
      <td style={{ fontSize: 13 }}>{row.nextLabel ?? '—'}</td>
      <td style={{ fontSize: 13 }}>
        {row.nextRegStartAt ? `${row.nextRegStartAt} ~ ${row.nextRegEndAt ?? ''}` : '—'}
      </td>
      <td style={{ fontSize: 13 }}>{row.nextExamDate ?? '—'}</td>
      <td>
        <button className="btn" onClick={() => onPick(row.certificateId, row.certificateName)}>
          넣기
        </button>
      </td>
    </tr>
  );
}

function StatusBadge({ row }: { row: OverviewRow }) {
  if (row.status === 'NONE') return <span className="badge todo">없음</span>;
  if (row.status === 'PAST') return <span className="badge todo">지남 · {row.scheduleCount}건</span>;
  if (row.status === 'OPEN') {
    return <span className="badge open">접수중{row.regDDay != null && ` D-${row.regDDay}`}</span>;
  }
  return <span className="badge">{row.scheduleCount}건</span>;
}
