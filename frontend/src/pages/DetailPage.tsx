import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { examApi } from '../api/exams';
import { fmtAt as fmt } from '../lib/format';
import { useAuth, useRequireLogin } from '../auth';
import type { DetailResponse } from '../api/types';
import Icon from '../components/Icon';

const TYPE_LABEL: Record<string, string> = { WRITTEN: '필기', PRACTICAL: '실기' };

// 자격증 상세: 다음 이벤트를 히어로로(페이지당 하나) + 연간 회차 표 + 관심 토글.
export default function DetailPage() {
  const { id } = useParams();
  const { me } = useAuth();
  const requireLogin = useRequireLogin();
  const [d, setD] = useState<DetailResponse | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    examApi.detail(Number(id)).then(setD).catch((e: Error) => setErr(e.message));
  }, [id]);

  async function toggle() {
    if (!d) return;
    // 비로그인이면 부르지 않는다 — 어차피 401 이고, 그 401 은 화면에 빨간 줄로만 남는다
    if (!me) {
      requireLogin();
      return;
    }
    try {
      if (d.favorited) await examApi.removeFavorite(d.id);
      else await examApi.addFavorite(d.id);
      setD({ ...d, favorited: !d.favorited });
      setErr(null);
    } catch (e) {
      setErr(e instanceof Error ? e.message : '관심 등록에 실패했습니다.');
    }
  }

  if (err && !d) return <div className="k-alert k-alert--err">{err}</div>;
  if (!d) return <div className="k-empty state">불러오는 중…</div>;

  return (
    <>
      <div className="page-header">
        <div>
          <h1>{d.name}</h1>
          <p>{[d.category, d.agency].filter(Boolean).join(' · ')}</p>
        </div>
        <div style={{ marginLeft: 'auto' }}>
          <button className={`k-btn ${d.favorited ? 'k-btn--secondary' : 'k-btn--primary'}`} onClick={toggle}
                  aria-pressed={me ? d.favorited : undefined}>
            <Icon name="star" size={18} filled={!!me && d.favorited} />
            {!me ? '로그인하고 등록' : d.favorited ? '등록됨' : '관심 등록'}
          </button>
        </div>
      </div>

      {err && <div className="k-alert k-alert--err">{err}</div>}

      {!d.favorited && (
        <p className="fineprint" style={{ marginTop: -6, marginBottom: 16 }}>
          등록해 두면 <b>내 시험</b>과 캘린더에 뜨고, 원서접수 시작·마감에 알림을 보내 드립니다.
        </p>
      )}

      {!d.nextEvent && !d.rolling && d.schedules.length > 0 && (
        <div className="k-alert k-alert--warn">
          <b>다음 회차 미정</b> — 등록된 일정은 모두 지났습니다. 등록해 두면 다음 회차가 확인되는 대로 알려 드립니다.
        </div>
      )}

      {d.nextEvent && (
        <section className="k-hero hero">
          <div className="hero-dday">
            <span className="num">
              {d.nextEvent.dday >= 0 ? d.nextEvent.dday : `+${-d.nextEvent.dday}`}
              <small>일</small>
            </span>
            <span className="what">
              <strong>{d.nextEvent.label}</strong>
              <span>{fmt(d.nextEvent.at)}</span>
            </span>
          </div>
        </section>
      )}

      <div className="section-head">
        <h2>연간 일정</h2>
        <span className="more">{d.schedules.length}회차</span>
      </div>

      {d.schedules.length === 0 ? (
        d.rolling ? (
          <div className="k-empty state">
            <span className="big">상시시험입니다</span>
            원하는 날짜를 골라 신청하는 방식이라 정해진 회차·접수 마감이 없습니다.
            시행처에서 바로 예약하세요.
          </div>
        ) : (
          <div className="k-empty state">
            <span className="big">아직 일정이 확인되지 않았습니다</span>
            시행처 공고가 나오면 채워집니다. 관심 등록해 두면 그때 알려 드립니다.
          </div>
        )
      ) : (
        <div className="k-tablewrap">
          {d.schedules.some((s) => s.confirmed === false) && (
            <div className="k-alert k-alert--warn" style={{ marginBottom: 14 }}>
              <b>일부 날짜는 추정치입니다.</b> 회차 패턴으로 계산한 값이라 실제와 다를 수 있으니,
              접수 전에 시행처 공고를 꼭 확인하세요.
            </div>
          )}
          <table className="k-table data-table">
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
                    {s.status !== 'ACTIVE' && <> <span className="k-badge">{s.status}</span></>}
                    {/* 추정치를 확정처럼 보여주면 "마감을 놓치지 않게 해준다"는 약속을 스스로 깬다 */}
                    {s.confirmed === false && (
                      <> <span className="k-badge k-badge--warn" title={s.provenanceLabel ?? undefined}>시행처 확인 필요</span></>
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
