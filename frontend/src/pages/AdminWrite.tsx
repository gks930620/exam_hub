import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { examApi } from '../api/exams';
import type { AdminScheduleRow, CertItem } from '../api/types';

// 운영자 수기 일정 입력 (설계 07 §4-2 2단계).
// 공무원·JLPT·DELE 처럼 연 1~2회짜리는 스크래퍼보다 사람이 넣는 게 싸다.
// 사용법은 운영/03_일정_직접입력.md.
//
// '할 일' 화면에서 [넣기] 를 누르면 ?cert=<id>&name=<이름> 으로 넘어온다 —
// 주소에 담아야 새로고침해도, 링크를 남에게 줘도 같은 시험이 열린다.
const EMPTY = {
  year: new Date().getFullYear(),
  round: 1,
  examType: 'WRITTEN',
  regStartAt: '',
  regEndAt: '',
  examStartDate: '',
  examEndDate: '',
  resultDate: '',
  sourceUrl: '',
};

export default function AdminWrite() {
  const [params, setParams] = useSearchParams();
  const [q, setQ] = useState(params.get('name') ?? '');
  const [results, setResults] = useState<CertItem[]>([]);
  const [selected, setSelected] = useState<CertItem | null>(null);
  const [rows, setRows] = useState<AdminScheduleRow[]>([]);
  const [form, setForm] = useState({ ...EMPTY });
  const [msg, setMsg] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const reload = useCallback(async (certId: number) => {
    try {
      setRows(await examApi.adminSchedules(certId));
    } catch (e) {
      setErr(e instanceof Error ? e.message : '일정을 불러오지 못했습니다.');
    }
  }, []);

  // 주소로 넘어온 시험을 연다
  const certParam = params.get('cert');
  useEffect(() => {
    if (!certParam) return;
    const id = Number(certParam);
    setSelected({ id, name: params.get('name') ?? `#${id}` } as CertItem);
    void reload(id);
  }, [certParam, params, reload]);

  useEffect(() => {
    if (selected || q.trim().length < 2) { setResults([]); return; }
    const t = window.setTimeout(() => {
      examApi.browse({ query: q.trim(), size: 10 })
        .then((r) => setResults(r.items))
        .catch(() => setResults([]));
    }, 300);
    return () => window.clearTimeout(t);
  }, [q, selected]);

  function pick(c: CertItem) {
    setSelected(c);
    setResults([]);
    setQ(c.name);
    setParams({ cert: String(c.id), name: c.name }, { replace: true });
    void reload(c.id);
  }

  function clear() {
    setSelected(null);
    setRows([]);
    setQ('');
    setParams({}, { replace: true });
  }

  async function save() {
    if (!selected) return;
    setBusy(true);
    setErr(null);
    setMsg(null);
    try {
      await examApi.adminUpsertSchedule({
        certificateId: selected.id,
        year: Number(form.year),
        round: Number(form.round),
        examType: form.examType,
        // 빈 문자열은 보내지 않는다 — 서버에서 null 로 들어가야 "미정"이 된다
        regStartAt: form.regStartAt || null,
        regEndAt: form.regEndAt || null,
        examStartDate: form.examStartDate || null,
        examEndDate: form.examEndDate || form.examStartDate || null,
        resultDate: form.resultDate || null,
        sourceUrl: form.sourceUrl || null,
      });
      setMsg('저장했습니다. 알림 예약도 다시 만들었습니다.');
      setForm({ ...EMPTY, year: form.year });
      await reload(selected.id);
    } catch (e) {
      setErr(e instanceof Error ? e.message : '저장하지 못했습니다.');
    } finally {
      setBusy(false);
    }
  }

  async function cancel(scheduleId: number) {
    if (!confirm('이 회차를 취소 처리할까요? (삭제가 아니라 상태만 CANCELED 로 바뀝니다)')) return;
    try {
      await examApi.adminCancelSchedule(scheduleId);
      if (selected) await reload(selected.id);
    } catch (e) {
      setErr(e instanceof Error ? e.message : '취소하지 못했습니다.');
    }
  }

  const set = (k: keyof typeof EMPTY) => (e: { target: { value: string } }) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  return (
    <>
      <div className="notice warn" style={{ marginBottom: 18 }}>
        <b>자동으로 들어오는 시험은 손대지 마세요.</b> 다음 수집 때 덮어써져 헛일이 됩니다 —
        어느 쪽인지는 <Link to="/admin/sources">수집 지도</Link>에서 확인합니다.
      </div>

      {err && <div className="notice error">{err}</div>}
      {msg && <div className="notice ok">{msg}</div>}

      <div className="step-head"><span className="step-no">1</span><h2>시험 고르기</h2></div>
      <div className="panel" style={{ padding: 20 }}>
        {selected ? (
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
            <b style={{ fontSize: 16 }}>{selected.name}</b>
            <button className="btn" onClick={clear} type="button">다른 시험 고르기</button>
          </div>
        ) : (
          <>
            <div className="searchbar" style={{ marginBottom: 0 }}>
              <span className="ico" aria-hidden="true">⌕</span>
              <input className="input" placeholder="시험명 검색 (2자 이상)"
                     value={q} onChange={(e) => setQ(e.target.value)} />
            </div>
            {results.length > 0 && (
              <div className="pick-list">
                {results.map((c) => (
                  <button key={c.id} className="pick" onClick={() => pick(c)} type="button">
                    <b>{c.name}</b>
                    <span>{[c.category, c.agency].filter(Boolean).join(' · ')}</span>
                  </button>
                ))}
              </div>
            )}
          </>
        )}
      </div>

      {!selected ? (
        <p className="fineprint" style={{ marginTop: 16 }}>
          시험을 고르면 등록된 일정과 입력칸이 나옵니다.
          무엇을 채워야 할지 모르겠으면 <Link to="/admin">할 일</Link>부터 보세요.
        </p>
      ) : (
        <>
          <div className="step-head" style={{ marginTop: 30 }}>
            <span className="step-no">2</span><h2>등록된 일정</h2>
            <span className="more">{rows.length}건</span>
          </div>
          {rows.length === 0 ? (
            <div className="state">아직 등록된 일정이 없습니다.</div>
          ) : (
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr><th>회차</th><th>접수</th><th>시험</th><th>발표</th><th>출처</th><th></th></tr>
                </thead>
                <tbody>
                  {rows.map((r) => (
                    <tr key={r.id}>
                      <td className="round">{r.year}년 {r.round}회 · {r.examType === 'WRITTEN' ? '필기' : '실기'}</td>
                      <td className="nowrap">{r.regStartAt ?? '-'} ~ {r.regEndAt ?? '-'}</td>
                      <td className="nowrap">{r.examStartDate ?? '-'}</td>
                      <td className="nowrap">{r.resultDate ?? '-'}</td>
                      <td className="nowrap">{r.sourceUrl ? '있음' : '-'}</td>
                      <td><button className="link-danger" onClick={() => cancel(r.id)}>취소</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="step-head" style={{ marginTop: 30 }}>
            <span className="step-no">3</span><h2>넣기 / 고치기</h2>
          </div>
          <div className="panel" style={{ padding: 20 }}>
            <p className="fineprint" style={{ margin: '0 0 16px' }}>
              같은 <b>연도·회차·구분</b>이 이미 있으면 덮어씁니다 — 일정이 바뀌었을 때 그대로 다시 넣으면 됩니다.
            </p>

            <div className="form-grid">
              <label className="field"><span>연도</span>
                <input className="input" type="number" value={form.year} onChange={set('year')} /></label>
              <label className="field"><span>회차</span>
                <input className="input" type="number" value={form.round} onChange={set('round')} /></label>
              <label className="field"><span>구분</span>
                <select className="input" value={form.examType} onChange={set('examType')}>
                  <option value="WRITTEN">필기 (구분 없으면 이것)</option>
                  <option value="PRACTICAL">실기</option>
                </select></label>

              <label className="field"><span>접수 시작 (시각까지)</span>
                <input className="input" type="datetime-local" value={form.regStartAt} onChange={set('regStartAt')} /></label>
              <label className="field"><span>접수 마감 (시각까지)</span>
                <input className="input" type="datetime-local" value={form.regEndAt} onChange={set('regEndAt')} /></label>
              <label className="field"><span>시험일</span>
                <input className="input" type="date" value={form.examStartDate} onChange={set('examStartDate')} /></label>
              <label className="field"><span>시험 종료일 (여러 날일 때만)</span>
                <input className="input" type="date" value={form.examEndDate} onChange={set('examEndDate')} /></label>
              <label className="field"><span>발표일</span>
                <input className="input" type="date" value={form.resultDate} onChange={set('resultDate')} /></label>
              <label className="field wide"><span>출처 URL (공고 주소)</span>
                <input className="input" placeholder="https://..." value={form.sourceUrl} onChange={set('sourceUrl')} /></label>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 8 }}>
              <button className="btn primary" onClick={save} disabled={busy} type="button">
                {busy ? '저장 중…' : '저장'}
              </button>
            </div>
          </div>
        </>
      )}
    </>
  );
}
