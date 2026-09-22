import type { ExamInfo as ExamInfoData } from '../api/types';

/**
 * 시험의 <b>날짜가 아닌 정보</b> — 응시료·시험과목·검정방법·합격기준.
 *
 * <p>그전에는 상세 화면에 회차 일정표밖에 없었다. 취준생이 "이 시험 볼까"를 여기서 못 정하고
 * 결국 큐넷으로 나갔다 — 관심 등록까지 오는 길의 가장 큰 누수였다.
 *
 * <p><b>없는 칸은 만들지 않는다.</b> 큐넷은 이 정보를 덩어리 글로 주고 시험마다 표기가 달라서
 * 못 가르는 것이 있다. 빈 칸을 늘어놓으면 화면만 길어지고 "이 서비스는 정보가 없구나"로 읽힌다.
 * 조각을 하나도 못 갈랐을 때만 원문을 접어서 보여준다.
 *
 * <p>관련학과를 <b>응시자격이라고 부르지 않는다</b> — 큐넷 API 에 응시자격은 없다.
 * 다른 말을 같은 말로 쓰면 사용자가 그 말을 믿고 원서를 낸다.
 */
export default function ExamInfo({ info }: { info: ExamInfoData | null }) {
  if (!info) return null;

  const fee = feeText(info);
  const rows: { label: string; value: string }[] = [];
  if (fee) rows.push({ label: '응시료', value: fee });
  if (info.subjects) rows.push({ label: '시험과목', value: info.subjects });
  if (info.examMethod) rows.push({ label: '검정방법', value: info.examMethod });
  if (info.passStandard) rows.push({ label: '합격기준', value: info.passStandard });
  if (info.relatedMajor) rows.push({ label: '관련학과', value: info.relatedMajor });

  // 조각을 하나도 못 갈랐으면 원문이라도 보여준다 — 있는 정보를 감추지 않는다
  const fallback = rows.length === 0 ? info.acquisitionRaw : null;
  if (rows.length === 0 && !fallback) return null;

  return (
    <section className="exam-info">
      <h2>시험 정보</h2>
      {fallback ? (
        <p className="raw">{fallback}</p>
      ) : (
        <dl>
          {rows.map((r) => (
            <div key={r.label}>
              <dt>{r.label}</dt>
              <dd>{r.value}</dd>
            </div>
          ))}
        </dl>
      )}
      {/* 언제 받아온 값인지 밝힌다 — 오래된 응시료를 지금 값처럼 보여주면 안 된다 */}
      <p className="fineprint">
        시행처 공고 기준입니다{info.collectedAt ? ` (${info.collectedAt.slice(0, 10)} 확인)` : ''}.
        {' '}접수 전에 시행처에서 한 번 더 확인해 주세요.
      </p>
    </section>
  );
}

/**
 * 응시료 한 줄. 숫자로 가른 것이 있으면 그걸 쓰고, 없으면 원문을 그대로 쓴다.
 * 없는 쪽을 0원으로 채우지 않는다 — 무료로 보인다.
 */
function feeText(info: ExamInfoData): string | null {
  const won = (n: number) => `${n.toLocaleString()}원`;
  const [first, second] = roundLabels(info);
  if (info.feeWritten != null && info.feePractical != null) {
    return `${first} ${won(info.feeWritten)} · ${second} ${won(info.feePractical)}`;
  }
  if (info.feeWritten != null) return `${first} ${won(info.feeWritten)}`;
  if (info.feePractical != null) return `${second} ${won(info.feePractical)}`;
  return info.feeRaw || null;
}

/**
 * 1차·2차를 <b>시행처가 부른 말</b>로 바꾼다.
 *
 * <p>큐넷이 주는 응시료는 "1차 : 67800, 2차 : 87100" 뿐이다. 2차를 "실기"라고 부른 건
 * 우리 추측이었는데, <b>기술사 2차는 실기가 아니라 면접이다</b> — 정보가 채워진 50종이 전부
 * 기술사라 화면이 100% "실기"라고 썼고, 그중 48종은 바로 아래 검정방법 칸이 "면접"이라고
 * 말했다. 한 화면 안에서 두 말을 한 것이다(2026-09-23 QA).
 *
 * <p>그래서 검정방법에 <b>시행처가 실제로 쓴 말</b>이 있으면 그걸 쓰고, 없으면 지어내지 않고
 * 큐넷이 부른 대로 1차·2차라고 쓴다. 모르는 것을 아는 척하지 않는다.
 */
function roundLabels(info: ExamInfoData): [string, string] {
  const m = info.examMethod ?? '';
  const first = m.includes('필기') ? '필기' : '1차';
  const second = m.includes('면접') && !m.includes('실기') ? '면접'
    : m.includes('실기') ? '실기'
      : '2차';
  return [first, second];
}
