import { useCallback, useEffect, useState } from 'react';
import { examApi } from '../api/exams';
import Pagination from '../components/Pagination';
import type { OverviewResponse, OverviewRow, ScheduleStatusKind } from '../api/types';

/**
 * 시험 일정 현황 — 기준은 <b>일정이 있냐 없냐</b> 딱 둘이다.
 *
 * <p>처음엔 일정 없음/지난 것만/접수 중/예정 네 상태로 나눴는데, 매니저(사용자)가 보기에
 * "뭘 저렇게 나눴는지" 알 수 없었다 — "오로지 시험일정이 있냐 없냐로만 판단하자.
 * 접수중은 나중일로"(2026-09-01). 세부 상태는 행의 배지로만 남긴다.
 *
 * <p>서버는 세부 상태를 그대로 주고, 여기서 둘로 묶는다('있음' = PAST,OPEN,UPCOMING).
 */
const PAGE_SIZE = 30;

const TABS = [
  {
    key: 'NONE',
    label: '일정 없음',
    hint: '한 번도 안 들어온 시험 — 여기부터 채웁니다',
    counts: ['NONE'] as readonly ScheduleStatusKind[],
  },
  {
    key: 'PAST,OPEN,UPCOMING',
    label: '일정 있음',
    hint: '일정이 들어와 있는 시험. ‘지남’ 배지가 붙은 것은 다음 회차를 넣을 때가 된 것입니다',
    counts: ['PAST', 'OPEN', 'UPCOMING'] as readonly ScheduleStatusKind[],
  },
  {
    key: 'ROLLING',
    label: '상시시험',
    hint: 'AWS·컴활·운전면허처럼 원하는 날짜에 신청하는 시험 — "일정"이라는 것이 없어서 채울 것도 없습니다',
    counts: ['ROLLING'] as readonly ScheduleStatusKind[],
  },
] as const;

export default function AdminOverview({ onPick }: { onPick: (id: number, name: string) => void }) {
  const [tab, setTab] = useState<string>('NONE');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<OverviewResponse | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true); setErr(null);
    try {
      setData(await examApi.adminOverview({ status: tab, query: q.trim() || undefined, page }));
    } catch (e) {
      setErr(e instanceof Error ? e.message : '불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [tab, q, page]);

  useEffect(() => { void load(); }, [load]);

  const counts = data?.counts ?? {};
  const countOf = (keys: readonly ScheduleStatusKind[]) => keys.reduce((sum, k) => sum + (counts[k] ?? 0), 0);
  const hint = TABS.find((t) => t.key === tab)?.hint ?? '';
  const totalPages = data ? Math.ceil(data.totalElements / PAGE_SIZE) : 0;

  return (
    <>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 12 }}>
        {TABS.map((t) => (
          <button key={t.key}
                  className={`btn ${tab === t.key ? 'primary' : ''}`}
                  onClick={() => { setTab(t.key); setPage(0); }}>
            {t.label}
            {data && <span style={{ marginLeft: 6, opacity: 0.75 }}>{countOf(t.counts)}</span>}
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
          {tab === 'NONE' && !q.trim() ? '일정 없는 시험을 다 채웠다는 뜻입니다.' : '검색어나 탭을 바꿔 보세요.'}
        </div>
      )}

      {data && data.items.length > 0 && (
        <>
          <div className="panel" style={{ padding: 0, overflowX: 'auto' }}>
            <table className="data-table">
              <thead>
                <tr>
                  <th>시험</th><th>분류</th><th>일정</th><th>다음 일정</th>
                  <th>접수</th><th>시험일</th><th></th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((r) => <OverviewRowView key={r.certificateId} row={r} onPick={onPick} />)}
              </tbody>
            </table>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginTop: 14, flexWrap: 'wrap' }}>
            <Pagination page={page} totalPages={totalPages} onChange={setPage} />
            <span className="fineprint" style={{ margin: 0 }}>
              {data.totalElements.toLocaleString()}종 중 {page * PAGE_SIZE + 1}–{page * PAGE_SIZE + data.items.length}
            </span>
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
      <td><ScheduleBadge row={row} /></td>
      <td style={{ fontSize: 13 }}>{row.nextLabel ?? '—'}</td>
      <td style={{ fontSize: 13 }}>
        {row.nextRegStartAt ? `${row.nextRegStartAt} ~ ${row.nextRegEndAt ?? ''}` : '—'}
      </td>
      <td style={{ fontSize: 13 }}>{row.nextExamDate ?? '—'}</td>
      <td>
        {row.status === 'ROLLING' ? (
          <span className="fineprint" style={{ margin: 0 }}>대상 아님</span>
        ) : (
          <button className="btn" onClick={() => onPick(row.certificateId, row.certificateName)}>
            넣기
          </button>
        )}
      </td>
    </tr>
  );
}

/** 세부 상태는 행에서만 — '지남'만 경고로 띄운다(다음 회차를 넣을 때가 됐다는 뜻). */
function ScheduleBadge({ row }: { row: OverviewRow }) {
  if (row.status === 'ROLLING') return <span className="badge">상시</span>;
  if (row.status === 'NONE') return <span className="badge todo">없음</span>;
  if (row.status === 'PAST') return <span className="badge todo">지남 · {row.scheduleCount}건</span>;
  return <span className="badge">{row.scheduleCount}건</span>;
}
