import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { examApi } from '../api/exams';
import { fmtAt as fmt } from '../lib/format';
import { roundLabel } from '../lib/status';
import { useAuth, useRequireLogin } from '../auth';
import type { DetailResponse, ScheduleStatus } from '../api/types';
import Icon from '../components/Icon';

const TYPE_LABEL: Record<string, string> = { WRITTEN: '필기', PRACTICAL: '실기' };

/** 회차 상태 배지 — 원문(CANCELED)을 그대로 내지 않는다. ACTIVE·DONE 은 배지가 없다. */
const STATUS_BADGE: Partial<Record<ScheduleStatus, { label: string; tone: string }>> = {
  CANCELED: { label: '취소됨', tone: 'k-badge--err' },
  PENDING_REVIEW: { label: '확인 중', tone: 'k-badge--warn' },
};

// 시험 상세: 다음 이벤트를 히어로로(페이지당 하나) + 연간 회차 표 + 관심 토글.
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

  if (err && !d) return <div className="k-alert k-alert--err" role="alert">{err}</div>;
  // 첫 로딩은 스켈레톤 — 제목·히어로·표 자리를 먼저 잡아 내용이 뜰 때 화면이 튀지 않게 한다(설계 05 §8)
  if (!d) {
    return (
      <div className="detail-skeleton" role="status" aria-label="시험 정보 불러오는 중">
        <div className="k-skeleton sk-title" />
        <div className="k-skeleton sk-hero" />
        {Array.from({ length: 4 }, (_, i) => <div className="k-skeleton sk-row" key={i} />)}
      </div>
    );
  }

  // "다음 회차 미정"은 살아 있는 회차가 전부 지났을 때다 — 취소된 회차만 있으면 "지났다"가 아니라 "없다"
  const active = d.schedules.filter((s) => s.status === 'ACTIVE');
  const pastOnly = active.length > 0 && !d.nextEvent && !d.rolling;

  return (
    <>
      {/* 좁은 화면에서는 버튼이 제목 아래 줄로 내려간다 — 잘리지 않게 */}
      <div className="page-header">
        <div className="page-header__text">
          <h1>{d.name}</h1>
          <p>{[d.category, d.agency].filter(Boolean).join(' · ')}</p>
        </div>
        <div className="page-header__actions">
          <button className={`k-btn ${d.favorited ? 'k-btn--secondary' : 'k-btn--primary'}`} onClick={toggle}
                  aria-pressed={me ? d.favorited : undefined}>
            <Icon name="star" size={18} filled={!!me && d.favorited} />
            {!me ? '로그인하고 등록' : d.favorited ? '등록됨' : '관심 등록'}
          </button>
        </div>
      </div>

      {err && <div className="k-alert k-alert--err" role="alert">{err}</div>}

      {/* 지키지 못할 약속을 하지 않는다 — 상시시험엔 알릴 마감이 없고, 일정 없는 시험은 확인돼야 알릴 수 있다 */}
      {!d.favorited && (
        <p className="fineprint detail-note">
          {d.rolling
            ? <>등록해 두면 <b>내 시험</b>에 모아 볼 수 있습니다.</>
            : active.length === 0
              ? <>등록해 두면 <b>내 시험</b>에 모아 두고, 일정이 확인되면 알려 드립니다.</>
              // 지난 회차뿐이면 "접수 시작·마감 알림"은 아직 없는 접수를 약속하는 셈이다 — 다음 회차를 약속한다
              : pastOnly
                ? <>등록해 두면 <b>내 시험</b>에 모아 두고, 다음 회차가 확인되는 대로 알려 드립니다.</>
                : <>등록해 두면 <b>내 시험</b>과 캘린더에 뜨고, 원서접수 시작·마감에 알림을 보내 드립니다.</>}
        </p>
      )}

      {pastOnly && (
        <div className="k-alert k-alert--warn">
          {/* 약속("확인되는 대로 알려 드립니다")은 위 안내문이 한다 — 여기는 사실만(§19-6) */}
          <b>다음 회차 미정</b> — 등록된 일정은 모두 지났습니다.
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
            {/* "등록해 두면 알려 드립니다"는 위 안내문이 이미 말했다 — 같은 약속을 두 번 하지 않는다(§19-6) */}
            시행처 공고가 나오면 채워집니다.
          </div>
        )
      ) : (
        <>
          {d.schedules.some((s) => s.confirmed === false) && (
            <div className="k-alert k-alert--warn detail-alert">
              <b>일부 날짜는 추정치입니다.</b> 회차 패턴으로 계산한 값이라 실제와 다를 수 있으니,
              접수 전에 시행처 공고를 꼭 확인하세요.
            </div>
          )}
          {/* 표는 가로 스크롤 래퍼 안에만 둔다 — 390px 에서 표가 화면 밖으로 나갔다.
              래퍼는 회색 판(카드)이다 — 흰 바탕에 선만 있는 표는 면이 안 갈린다(설계 05 §19-3) */}
          <div className="k-card k-card--flush k-tablewrap">
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
                {d.schedules.map((s) => {
                  const badge = STATUS_BADGE[s.status];
                  return (
                    <tr key={s.id} className={s.status === 'CANCELED' ? 'is-canceled' : undefined}>
                      <td className="round">
                        {roundLabel(s.year, s.round)} · {TYPE_LABEL[s.examType] ?? s.examType}
                        {badge && <> <span className={`k-badge ${badge.tone}`}>{badge.label}</span></>}
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
                  );
                })}
              </tbody>
            </table>
          </div>
        </>
      )}

      {d.sourceUrl && (
        <p className="fineprint">
          출처: <a href={d.sourceUrl} target="_blank" rel="noreferrer">{d.sourceUrl}</a>
          {d.collectedAt && <> · 수집 {fmt(d.collectedAt)}</>}
          <br />최종 일정은 시행처에서 확인하세요.
        </p>
      )}
    </>
  );
}
