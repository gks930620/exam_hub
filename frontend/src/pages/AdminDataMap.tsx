import { useEffect, useState } from 'react';
import { examApi } from '../api/exams';
import type { DataMapResponse, DataSourceRow, SourceMode } from '../api/types';
import Icon from '../components/Icon';

/**
 * 데이터 지도 — <b>무엇이 자동으로 들어오고, 무엇을 내가 넣어야 하나.</b>
 *
 * 수기 입력 화면만 있으면 매니저는 무엇을 넣어야 할지 모른다. 더 나쁜 건 자동 수집되는
 * 시험을 손으로 넣다가 다음 수집에 덮어써지는 헛일이다. 그래서 입력 화면과 같은 자리에서
 * "이건 손대지 마세요 / 이건 이 사이트를 열어 보고 넣으세요"를 보여준다.
 */
// 매니저가 손댈 것부터. '대상 아님' 이 마지막인 이유는 할 일이 없어서다.
const MODE_ORDER: SourceMode[] = ['MANUAL', 'AUTO_PENDING', 'AUTO', 'EXCLUDED'];

const MODE_TONE: Record<SourceMode, string> = {
  MANUAL: 'k-badge--warn',   // 내가 할 일
  AUTO_PENDING: 'k-badge--point',   // 곧 자동이 될 것
  AUTO: 'k-badge--ok',       // 손댈 필요 없음
  EXCLUDED: '',              // 넣지 않음 — 기본(조용한) 배지
};

export default function AdminDataMap() {
  const [data, setData] = useState<DataMapResponse | null>(null);
  const [err, setErr] = useState<string | null>(null);
  // 처음엔 전부 접는다 — 첫눈에 들어와야 하는 건 한 갈래의 세부가 아니라 '세 갈래가 있다'는 사실이다
  const [open, setOpen] = useState<SourceMode | null>(null);

  useEffect(() => {
    examApi.adminDataMap()
      .then(setData)
      .catch((e) => setErr(e instanceof Error ? e.message : '불러오지 못했습니다.'));
  }, []);

  if (err) return <div className="k-alert k-alert--err">{err}</div>;
  if (!data) return <div className="k-empty state">데이터 지도를 불러오는 중…</div>;

  const { sources } = data;
  const grouped = MODE_ORDER.map((mode) => ({
    mode,
    rows: sources.filter((s) => s.mode === mode),
  })).filter((g) => g.rows.length > 0);

  return (
    <>
      {grouped.map(({ mode, rows }) => (
        <div key={mode} style={{ marginBottom: 18 }}>
          <button className="k-btn k-btn--secondary" style={{ width: '100%', justifyContent: 'flex-start' }}
                  onClick={() => setOpen(open === mode ? null : mode)}>
            <span className={`k-badge ${MODE_TONE[mode]}`} style={{ marginRight: 10 }}>
              {rows[0].modeLabel}
            </span>
            {rows.length}갈래 <Icon name={open === mode ? 'chevronDown' : 'chevronRight'} size={16} />
          </button>

          {open === mode && (
            <div className="k-card" style={{ padding: 18, marginTop: 10 }}>
              <p className="fineprint" style={{ margin: '0 0 16px' }}>{rows[0].modeGuide}</p>
              {rows.map((r) => <SourceCard key={r.group + r.exams} row={r} />)}
            </div>
          )}
        </div>
      ))}
    </>
  );
}

function SourceCard({ row }: { row: DataSourceRow }) {
  return (
    <div style={{ padding: '14px 0', borderTop: '1px solid var(--border)' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
        <div>
          <b>{row.group}</b>
          <span style={{ color: 'var(--muted2)', fontSize: 13 }}> · {row.exams}</span>
        </div>
        <span className="k-chip">{row.frequency}</span>
      </div>

      <div style={{ fontSize: 13, color: 'var(--muted)', marginTop: 8, lineHeight: 1.8 }}>
        <div>시행처 — {row.sourceName}</div>
        {row.checkPath !== '—' && <div>어디를 보나 — {row.checkPath}</div>}
        {row.note && <div style={{ color: 'var(--muted2)' }}>{row.note}</div>}
      </div>

      {row.mode !== 'EXCLUDED' && (
        <a className="k-btn k-btn--secondary" href={row.sourceUrl} target="_blank" rel="noreferrer"
           style={{ marginTop: 10 }}>
          원본 사이트 열기 <Icon name="external" size={16} />
        </a>
      )}
    </div>
  );
}
