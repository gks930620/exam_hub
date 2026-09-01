import { useEffect, useState } from 'react';
import { examApi } from '../api/exams';
import type { DataMapResponse, DataSourceRow, SourceMode } from '../api/types';

/**
 * 데이터 지도 — <b>무엇이 자동으로 들어오고, 무엇을 내가 넣어야 하나.</b>
 *
 * 수기 입력 화면만 있으면 매니저는 무엇을 넣어야 할지 모른다. 더 나쁜 건 자동 수집되는
 * 시험을 손으로 넣다가 다음 수집에 덮어써지는 헛일이다. 그래서 입력 화면과 같은 자리에서
 * "이건 손대지 마세요 / 이건 이 사이트를 열어 보고 넣으세요"를 보여준다.
 */
const MODE_ORDER: SourceMode[] = ['MANUAL', 'AUTO_PENDING', 'AUTO', 'EXCLUDED'];

const MODE_TONE: Record<SourceMode, string> = {
  MANUAL: 'warn',        // 내가 할 일
  AUTO_PENDING: 'info',  // 곧 자동이 될 것
  AUTO: 'ok',            // 손댈 필요 없음
  EXCLUDED: 'muted',     // 넣지 않음
};

export default function AdminDataMap() {
  const [data, setData] = useState<DataMapResponse | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [open, setOpen] = useState<SourceMode | null>('MANUAL');

  useEffect(() => {
    examApi.adminDataMap()
      .then(setData)
      .catch((e) => setErr(e instanceof Error ? e.message : '불러오지 못했습니다.'));
  }, []);

  if (err) return <div className="notice error">{err}</div>;
  if (!data) return <div className="state">데이터 지도를 불러오는 중…</div>;

  const { coverage, sources } = data;
  const grouped = MODE_ORDER.map((mode) => ({
    mode,
    rows: sources.filter((s) => s.mode === mode),
  })).filter((g) => g.rows.length > 0);

  return (
    <>
      {/* 지금 얼마나 채워졌나 — 일정 없는 시험 수가 곧 할 일의 크기다 */}
      <div className="panel" style={{ padding: 20, marginBottom: 22 }}>
        <div style={{ display: 'flex', gap: 28, flexWrap: 'wrap' }}>
          <Stat label="등록된 시험" value={coverage.totalExams} />
          <Stat label="일정이 있는 시험" value={coverage.withSchedule} tone="ok" />
          <Stat label="일정이 없는 시험" value={coverage.withoutSchedule} tone="warn" />
        </div>
        <p className="fineprint" style={{ margin: '14px 0 0' }}>
          일정이 없는 시험은 화면에 <b>일정 미정</b> 으로 뜹니다. 검색·관심등록은 되지만
          <b> D-day 와 알림은 못 갑니다</b> — 이 서비스의 핵심이 그거라, 이 숫자를 줄이는 게 매니저의 일입니다.
        </p>
      </div>

      {grouped.map(({ mode, rows }) => (
        <div key={mode} style={{ marginBottom: 18 }}>
          <button className="btn" style={{ width: '100%', justifyContent: 'flex-start' }}
                  onClick={() => setOpen(open === mode ? null : mode)}>
            <span className={`badge ${MODE_TONE[mode]}`} style={{ marginRight: 10 }}>
              {rows[0].modeLabel}
            </span>
            {rows.length}갈래 {open === mode ? '▾' : '▸'}
          </button>

          {open === mode && (
            <div className="panel" style={{ padding: 18, marginTop: 10 }}>
              <p className="fineprint" style={{ margin: '0 0 16px' }}>{rows[0].modeGuide}</p>
              {rows.map((r) => <SourceCard key={r.group + r.exams} row={r} />)}
            </div>
          )}
        </div>
      ))}
    </>
  );
}

function Stat({ label, value, tone }: { label: string; value: number; tone?: string }) {
  return (
    <div>
      <div style={{ fontSize: 12.5, color: 'var(--muted2)', marginBottom: 4 }}>{label}</div>
      <div style={{ fontSize: 26, fontWeight: 750, letterSpacing: '-.02em' }}
           className={tone === 'warn' ? 'dday' : undefined}>
        {value.toLocaleString()}
      </div>
    </div>
  );
}

function SourceCard({ row }: { row: DataSourceRow }) {
  return (
    <div style={{ padding: '14px 0', borderTop: '1px solid var(--line)' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
        <div>
          <b>{row.group}</b>
          <span style={{ color: 'var(--muted2)', fontSize: 13 }}> · {row.exams}</span>
        </div>
        <span className="chip">{row.frequency}</span>
      </div>

      <div style={{ fontSize: 13, color: 'var(--muted)', marginTop: 8, lineHeight: 1.8 }}>
        <div>시행처 — {row.sourceName}</div>
        {row.checkPath !== '—' && <div>어디를 보나 — {row.checkPath}</div>}
        {row.note && <div style={{ color: 'var(--muted2)' }}>{row.note}</div>}
      </div>

      {row.mode !== 'EXCLUDED' && (
        <a className="btn" href={row.sourceUrl} target="_blank" rel="noreferrer"
           style={{ marginTop: 10 }}>
          원본 사이트 열기 ↗
        </a>
      )}
    </div>
  );
}
