import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { examApi } from '../api/exams';
import type { DetailResponse } from '../api/types';

const TYPE_LABEL: Record<string, string> = { WRITTEN: '필기', PRACTICAL: '실기' };

// 자격증 상세: 다음 이벤트를 히어로로(페이지당 하나) + 연간 회차 표 + 관심 토글.
export default function DetailPage() {
  const { id } = useParams();
  const [d, setD] = useState<DetailResponse | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    examApi.detail(Number(id)).then(setD).catch((e: Error) => setErr(e.message));
  }, [id]);

  async function toggle() {
    if (!d) return;
    try {
      if (d.favorited) await examApi.removeFavorite(d.id);
      else await examApi.addFavorite(d.id);
      setD({ ...d, favorited: !d.favorited });
      setErr(null);
    } catch (e) {
      setErr(e instanceof Error ? e.message : '관심 등록에 실패했습니다.');
    }
  }

  if (err && !d) return <div className="notice error">{err}</div>;
  if (!d) return <div className="state">불러오는 중…</div>;

  return (
    <>
      <div className="page-header">
        <div>
          <h1>{d.name}</h1>
          <p>{[d.category, d.agency].filter(Boolean).join(' · ')}</p>
        </div>
        <div style={{ marginLeft: 'auto' }}>
          <button className={`btn${d.favorited ? '' : ' primary'}`} onClick={toggle} aria-pressed={d.favorited}>
            {d.favorited ? '★ 등록됨' : '☆ 관심 등록'}
          </button>
        </div>
      </div>

      {err && <div className="notice error">{err}</div>}

      {d.nextEvent && (
        <section className="hero">
          <div className="hero-dday">
            <span className="num">
              {d.nextEvent.dday >= 0 ? d.nextEvent.dday : `+${-d.nextEvent.dday}`}
              <small>일</small>
            </span>
            <span className="what">
              <strong>{d.nextEvent.label}</strong>
              <span>{d.nextEvent.at}</span>
            </span>
          </div>
        </section>
      )}

      <div className="section-head">
        <h2>연간 일정</h2>
        <span className="more">{d.schedules.length}회차</span>
      </div>

      {d.schedules.length === 0 ? (
        <div className="state">
          <span className="big">아직 일정이 확인되지 않았습니다</span>
          시행처 공고가 나오면 채워집니다. 관심 등록해 두면 그때 알려 드립니다.
        </div>
      ) : (
        <div className="table-wrap">
          {d.schedules.some((s) => s.confirmed === false) && (
            <div className="notice warn" style={{ marginBottom: 14 }}>
              <b>일부 날짜는 추정치입니다.</b> 회차 패턴으로 계산한 값이라 실제와 다를 수 있으니,
              접수 전에 시행처 공고를 꼭 확인하세요.
            </div>
          )}
          <table className="data-table">
            <thead>
              <tr>
                <th>회차</th>
                <th>접수</th>
                <th>시험</th>
                <th>발표</th>
              </tr>
            </thead>
            <tbody>
              {d.schedules.map((s) => (
                <tr key={s.id}>
                  <td className="round">
                    {s.year}년 {s.round}회 · {TYPE_LABEL[s.examType] ?? s.examType}
                    {s.status !== 'ACTIVE' && <> <span className="badge">{s.status}</span></>}
                    {/* 추정치를 확정처럼 보여주면 "마감을 놓치지 않게 해준다"는 약속을 스스로 깬다 */}
                    {s.confirmed === false && (
                      <> <span className="badge todo" title={s.provenanceLabel ?? undefined}>시행처 확인 필요</span></>
                    )}
                  </td>
                  <td className="nowrap">{s.regStartAt ? `${fmt(s.regStartAt)} ~ ${fmt(s.regEndAt)}` : '-'}</td>
                  <td className="nowrap">
                    {s.examStartDate
                      ? s.examStartDate + (s.examEndDate && s.examEndDate !== s.examStartDate ? ` ~ ${s.examEndDate}` : '')
                      : '-'}
                  </td>
                  <td className="nowrap">{s.resultDate ?? '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {d.sourceUrl && (
        <p className="fineprint">
          출처: <a href={d.sourceUrl} target="_blank" rel="noreferrer">{d.sourceUrl}</a>
          {d.collectedAt && <> · 수집 {d.collectedAt}</>}
          <br />최종 일정은 시행처에서 확인하세요.
        </p>
      )}
    </>
  );
}

function fmt(iso: string | null): string {
  if (!iso) return '-';
  return iso.replace('T', ' ').slice(0, 16);
}
