import { useCallback, useEffect, useState } from 'react';
import { examApi } from '../api/exams';
import { fmtAt } from '../lib/format';
import { badgeLabel, badgeTone } from '../lib/status';
import Pagination from '../components/Pagination';
import ScheduleEditorModal from '../components/ScheduleEditorModal';
import Icon from '../components/Icon';
import type { OverviewAction, OverviewBucket, OverviewResponse, OverviewRow } from '../api/types';

/**
 * 시험 일정 현황 — <b>무엇을 해야 하는지</b>가 먼저 보이는 화면.
 *
 * <p>"일정 있음/없음"만으로는 수기 시험의 회차가 지나 다음 회차를 넣어야 하는 상황이 안 보였다
 * (사용자 지적 2026-09-02). 서버가 시험마다 <b>행동</b>을 판정해 준다 — 첫 일정 입력 / 다음 회차 입력 /
 * 시행처 확인 / 회차 끊김 확인 / 일정 이동 확인. 행동이 없는 시험은 대기(자동으로 올 것)·정상·상시다.
 *
 * <p>[넣기]는 그 자리에서 모달로 연다. 탭을 옮겨 다시 검색하지 않는다.
 */
const PAGE_SIZE = 30;
const DEBOUNCE_MS = 300;

const BUCKETS: { key: OverviewBucket; label: string; hint: string }[] = [
  { key: 'TODO', label: '할 일', hint: '지금 사람이 손대야 하는 시험. 행동별로 나뉩니다' },
  { key: 'WAITING', label: '대기', hint: '기다리면 자동으로 들어옵니다 — 공고 전 · 다음 회차 수집 대기 · 크롤링 예정' },
  { key: 'OK', label: '정상', hint: '앞으로의 일정이 들어와 있습니다. 사용자에게 D-day 와 알림이 갑니다' },
  { key: 'ROLLING', label: '상시', hint: '원하는 날짜에 신청하는 시험 — 일정이라는 것이 없어 대상이 아닙니다' },
];

const ACTIONS: { key: OverviewAction; label: string; hint: string; tone: string }[] = [
  { key: 'FIRST_INPUT', label: '첫 일정 입력', hint: '수기 대상인데 일정이 하나도 없습니다', tone: 'k-badge--warn' },
  { key: 'NEXT_ROUND', label: '다음 회차 입력', hint: '수기 대상인데 남은 일정이 전부 지났습니다 — 오래 지난 것부터', tone: 'k-badge--warn' },
  { key: 'VERIFY', label: '시행처 확인', hint: '앞으로의 일정이 추정치(회차 패턴 계산)입니다 — 임박한 것부터. 공고와 대조해 저장하면 빠집니다', tone: 'k-badge--point' },
  { key: 'REVIEW_MOVE', label: '일정 이동 확인', hint: '수집된 일정이 30일 넘게 움직여 보류 중입니다 — 공고와 대조해 저장하면 풀립니다', tone: 'k-badge--warn' },
  { key: 'CHECK_SOURCE', label: '회차 끊김 확인', hint: '자동 소스인데 1년 넘게 새 회차가 없습니다 — 폐지·개칭됐거나 수집이 빠졌을 수 있습니다', tone: 'k-badge--err' },
];

/** [넣기]가 뜨는 행 — 수기 대상이거나, 자동이라도 사람이 공고와 대조해 저장해야 풀리는 것 */
function canEdit(row: OverviewRow): boolean {
  if (row.bucket === 'ROLLING') return false;
  return row.source === 'MANUAL' || row.action === 'VERIFY' || row.action === 'REVIEW_MOVE';
}

export default function AdminOverview() {
  const [bucket, setBucket] = useState<OverviewBucket>('TODO');
  const [action, setAction] = useState<OverviewAction | ''>('');
  const [q, setQ] = useState('');
  // 글자마다 서버를 부르지 않는다 — 300ms 멈추면 그때 한 번
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<OverviewResponse | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<{ id: number; name: string } | null>(null);

  useEffect(() => {
    if (q.trim() === query) return;
    const t = window.setTimeout(() => { setQuery(q.trim()); setPage(0); }, DEBOUNCE_MS);
    return () => window.clearTimeout(t);
  }, [q, query]);

  const load = useCallback(async () => {
    setLoading(true); setErr(null);
    try {
      setData(await examApi.adminOverview({
        bucket, action: bucket === 'TODO' && action ? action : undefined,
        query: query || undefined, page,
      }));
    } catch (e) {
      setErr(e instanceof Error ? e.message : '불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [bucket, action, query, page]);

  useEffect(() => { void load(); }, [load]);

  const bc = data?.bucketCounts ?? {};
  const ac = data?.actionCounts ?? {};
  const todo = Object.values(ac).reduce((a, b) => a + (b ?? 0), 0);
  const totalPages = data ? Math.ceil(data.totalElements / PAGE_SIZE) : 0;
  const hint = bucket === 'TODO' && action
    ? ACTIONS.find((a) => a.key === action)?.hint
    : BUCKETS.find((b) => b.key === bucket)?.hint;

  return (
    <>
      {/* 요약 — 이 화면에서 제일 먼저 읽혀야 하는 한 줄. 0건인 행동은 흐리게 */}
      {data && (
        <div className="todo-strip">
          <div className="todo-strip__lead">
            <span className="todo-strip__num">{todo}</span>
            <span className="todo-strip__label">지금 할 일</span>
          </div>
          <div className="todo-strip__parts">
            {ACTIONS.map((a) => {
              const n = ac[a.key] ?? 0;
              return (
                <button key={a.key} className={`k-chip${n === 0 ? ' k-dim' : ''}`}
                        aria-pressed={bucket === 'TODO' && action === a.key}
                        onClick={() => { setBucket('TODO'); setAction(action === a.key ? '' : a.key); setPage(0); }}>
                  {a.label} <b>{n}</b>
                </button>
              );
            })}
          </div>
        </div>
      )}

      <div className="k-tabs" role="tablist" style={{ marginBottom: 10 }}>
        {BUCKETS.map((b) => (
          <button key={b.key} role="tab" aria-selected={bucket === b.key}
                  onClick={() => { setBucket(b.key); setAction(''); setPage(0); }}>
            {b.label}{data && <span style={{ marginLeft: 6, opacity: .7 }}>{bc[b.key] ?? 0}</span>}
          </button>
        ))}
      </div>
      {hint && <p className="fineprint" style={{ margin: '0 0 14px' }}>{hint}</p>}

      <div className="searchbar" style={{ marginBottom: 14 }}>
        <span className="ico" aria-hidden="true"><Icon name="search" size={18} /></span>
        <input className="k-input" placeholder="시험명으로 좁히기" aria-label="시험명으로 좁히기"
               value={q} onChange={(e) => setQ(e.target.value)} />
      </div>

      {err && <div className="k-alert k-alert--err" role="alert">{err}</div>}
      {loading && !data && <div className="k-empty state" role="status">불러오는 중…</div>}

      {data && data.items.length === 0 && (
        <div className="k-empty state">
          <span className="big">{bucket === 'TODO' && !query ? '지금 할 일이 없습니다' : '해당하는 시험이 없습니다'}</span>
          {bucket === 'TODO' && !query ? '수기 대상 시험이 전부 앞으로의 일정을 갖고 있습니다.' : '검색어나 탭을 바꿔 보세요.'}
        </div>
      )}

      {data && data.items.length > 0 && (
        <>
          {/* 다시 불러오는 동안 표는 남고 흐려진다 — 자리가 흔들리면 어디를 보고 있었는지 잃는다 */}
          <div className="k-card k-tablewrap" style={{ padding: 0 }} aria-busy={loading}>
            <table className="k-table data-table">
              <thead>
                <tr>
                  <th>시험</th><th>출처</th><th>{bucket === 'TODO' ? '해야 할 것' : '상태'}</th>
                  <th>{bucket === 'OK' ? '다음 일정' : '마지막 회차'}</th><th></th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((r) => (
                  <RowView key={r.certificateId} row={r}
                           onEdit={() => setEditing({ id: r.certificateId, name: r.certificateName })} />
                ))}
              </tbody>
            </table>
          </div>

          <div className="pager-row" style={{ justifyContent: 'flex-start' }}>
            <Pagination page={page} totalPages={totalPages} onChange={setPage} />
            <span className="fineprint" style={{ margin: 0 }}>
              {data.totalElements.toLocaleString()}종 중 {page * PAGE_SIZE + 1}–{page * PAGE_SIZE + data.items.length}
            </span>
          </div>
        </>
      )}

      {editing && (
        <ScheduleEditorModal certificateId={editing.id} name={editing.name}
                             onClose={() => setEditing(null)} onSaved={() => void load()} />
      )}
    </>
  );
}

function RowView({ row, onEdit }: { row: OverviewRow; onEdit: () => void }) {
  const act = ACTIONS.find((a) => a.key === row.action);
  return (
    <tr>
      <td>
        <b>{row.certificateName}</b>
        {row.agency && <div style={{ fontSize: 12, color: 'var(--muted2)' }}>{row.agency}</div>}
      </td>
      <td><span className="k-badge">{row.sourceLabel}</span></td>
      <td>
        {row.action && act ? (
          <span className={`k-badge ${act.tone}`}>{row.actionLabel ?? act.label}</span>
        ) : row.waitingLabel ? (
          <span className="k-dim">{row.waitingLabel}</span>
        ) : row.bucket === 'ROLLING' ? (
          <span className="k-dim">대상 아님</span>
        ) : (
          <span className={`k-badge ${badgeTone(row.nextBadge)}`}>{badgeLabel(row.nextBadge, '예정')}</span>
        )}
      </td>
      <td style={{ fontSize: 13 }}>
        {row.freshness === 'UPCOMING' && row.nextLabel ? (
          <>
            {row.nextLabel} <span className="k-muted">{fmtAt(row.nextAt)}</span>
            {row.nextDday != null && <b style={{ marginLeft: 6 }}>D-{row.nextDday}</b>}
          </>
        ) : row.lastLabel ? (
          <>
            {row.lastLabel} <span className="k-muted">{row.lastExamDate}</span>
            {row.daysSinceLast != null && <span className="k-dim" style={{ marginLeft: 6 }}>{row.daysSinceLast}일 지남</span>}
          </>
        ) : (
          <span className="k-dim">—</span>
        )}
      </td>
      <td>
        {row.bucket === 'ROLLING' ? null
          : canEdit(row)
            ? <button className="k-btn k-btn--secondary k-btn--sm" onClick={onEdit}>넣기</button>
            : <span className="k-dim" title="자동으로 들어옵니다 — 손으로 넣으면 다음 수집 때 덮어써집니다">자동</span>}
      </td>
    </tr>
  );
}
