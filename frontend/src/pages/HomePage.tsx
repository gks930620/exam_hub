import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { examApi } from '../api/exams';
import { ddayLabel } from '../lib/format';
import { eventAtLabel } from '../lib/status';
import type { FavoriteCard, FavoriteListResponse } from '../api/types';
import Icon from '../components/Icon';
import CardStatus from '../components/CardStatus';

// 내 시험: 등록한 시험의 D-day. 히어로(가장 급한 것 하나) → 카드.
// 지표 타일(등록 N · 가장 가까운 D-n)은 뺐다 — 히어로와 목록 머리가 같은 숫자를 이미 말한다(설계 05 §19-6).
//
// 카드는 scheduleState 로 갈린다. D-day 숫자는 <b>다가오는 일정이 있을 때만</b> 있다 —
// 일정 없는 관심 시험이 빨간 "D-0" 으로 보이던 결함(2026-09-03)이 그 반대였다.
const SOON_DAYS = 7;

/** 다가오는 일정이 있는 카드 — 서버가 dday 를 준 것만. 이 좁힘이 곧 "D-day 를 그려도 되는가"다. */
function hasUpcoming(c: FavoriteCard): c is FavoriteCard & { dday: number } {
  return c.scheduleState === 'UPCOMING' && c.dday != null;
}

export default function HomePage() {
  const [data, setData] = useState<FavoriteListResponse | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    examApi.favorites().then(setData).catch((e: Error) => setErr(e.message));
  }, []);

  if (err) return <div className="k-alert k-alert--err" role="alert">{err}</div>;
  // 첫 로딩은 스켈레톤 — 자리를 먼저 잡아 카드가 뜰 때 화면이 튀지 않게 한다(설계 05 §8)
  if (!data) return <div className="card-grid" role="status" aria-label="내 시험 불러오는 중">{Array.from({ length: 6 }, (_, i) => <div className="k-skeleton card-skeleton" key={i} />)}</div>;

  if (data.items.length === 0) {
    return (
      <>
        <div className="page-header">
          <div className="page-avatar" aria-hidden="true"><Icon name="bookmark" size={22} /></div>
          <div className="page-header__text">
            <h1>내 시험</h1>
            <p>관심 시험을 등록하면 접수 시작·마감과 시험일을 챙겨 드립니다.</p>
          </div>
        </div>
        <div className="k-empty state">
          <span className="big">아직 등록한 시험이 없어요</span>
          시험을 등록하면 여기에 D-day가 표시됩니다.
          <div className="empty-cta">
            <Link to="/" className="k-btn k-btn--primary k-btn--lg">시험 찾으러 가기</Link>
          </div>
        </div>
      </>
    );
  }

  // 서버는 급한 순으로 준다 — 다가오는 일정이 있는 첫 카드가 히어로다. 하나도 없으면 그렇다고 말한다.
  // 접수 중·이번 주 같은 지표는 뺐다 — 지금은 헷갈린다(사용자 결정 2026-09-02). 익숙해지면 다시.
  const lead = data.items.find(hasUpcoming) ?? null;

  return (
    <>
      {/* 킷 데모 구조: 히어로에 큰 제목 하나 + 가장 급한 일 + 행동 */}
      <section className="k-hero my-hero">
        {/* 개수는 아래 목록 머리가 말한다 — 히어로 제목까지 같은 숫자를 적지 않는다(§19-6) */}
        <h1>내 시험</h1>
        {lead ? (
          <p>
            <strong>{lead.name}</strong> — {lead.eventLabel} {eventAtLabel(lead.badge, lead.eventAt)} ({ddayLabel(lead.dday)})
          </p>
        ) : (
          <p>
            <strong>다가오는 일정 없음</strong> — 등록한 시험에 아직 다가오는 접수·시험 일정이 없습니다.
            {/* 등록한 게 전부 폐지·개칭된 시험이면 오지 않을 일정을 약속하지 않는다 */}
            {data.items.some((c) => !c.hiddenReason)
              ? ' 일정이 확인되면 알려 드립니다.'
              : ' 등록한 시험이 모두 폐지·개칭되었습니다. 새 이름으로 다시 등록해 주세요.'}
          </p>
        )}
        <div className="hero-actions">
          {lead && <Link to={`/cert/${lead.certificateId}`} className="k-btn k-btn--primary">일정 자세히 보기</Link>}
          <Link to="/" className="k-btn k-btn--secondary">시험 더 찾기</Link>
        </div>
      </section>

      <div className="k-section list-head">
        <h2>등록한 시험 <span className="more">{data.items.length}개</span></h2>
      </div>
      <div className="card-grid">
        {data.items.map((c) => <ExamCard key={c.certificateId} card={c} />)}
      </div>

      <p className="fineprint">
        본 서비스는 공공데이터·각 시행처 정보를 모은 참고용입니다. 최종 일정은 각 시행처에서 확인하세요.
      </p>
    </>
  );
}

function ExamCard({ card }: { card: FavoriteCard }) {
  return (
    <Link to={`/cert/${card.certificateId}`} className="k-card k-card--hover exam-card my-card">
      <div className="top">
        <h3>{card.name}</h3>
        {/* 빨간 D-day 는 7일 이내에만 — 전부 빨갛면 아무것도 급하지 않다 */}
        {hasUpcoming(card) && (
          <span className={`dday${card.dday >= 0 && card.dday <= SOON_DAYS ? ' soon' : ''}`}>
            {ddayLabel(card.dday)}
          </span>
        )}
      </div>
      <div className="foot">
        {/* 폐지·개칭된 시험은 사유를 그대로 말한다 — "일정이 확인되면 알려 드립니다"는 지킬 수 없는 약속이다 */}
        {/* 배지 없이 문장 한 줄 — 상태는 D-day(윗줄)와 문장 속 말이 이미 담고 있다(§19-6).
            배지 옆에서 두 줄로 접히던 문장이 눈높이를 어긋나게 하던 것도 같이 없어진다. */}
        {card.hiddenReason ? (
          <span className="when">더 이상 없음 · {card.hiddenReason}</span>
        ) : (
          <CardStatus state={card.scheduleState} badge={card.badge} badgeFallback={card.badgeLabel}
                      label={card.eventLabel} at={card.eventAt} dday={card.dday} lastExamDate={card.lastExamDate} confirmed={card.nextConfirmed} plain />
        )}
      </div>
    </Link>
  );
}
