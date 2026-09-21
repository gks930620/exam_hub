import AdminLifecycle from './AdminLifecycle';

/**
 * 시험 변천사 — 폐지·개칭된 시험이 어디로 갔나.
 *
 * <p>사용자 화면에서는 빠지지만 기록은 남긴다. 그냥 지우면 <b>있었다는 사실까지 사라져</b>
 * "웹디자인기능사 왜 없죠?"에 답할 근거가 없어진다.
 *
 * <p>별도 주소인 이유: 이건 <b>할 일이 아니라 참고 자료</b>다. 채워야 할 목록 옆에 두면
 * 매니저가 처리해야 할 것처럼 보인다(사용자 지적).
 */
export default function AdminLifecyclePage() {
  return (
    <>
      <p className="fineprint fineprint--lead">
        폐지·개칭이 <b>확정된</b> 시험은 검색에 나오지 않습니다. <b>확인 필요</b>는 판단 전이라
        검색에서 빠지지 않았습니다 — 시행처를 보고 판정하는 것 말고는 손댈 일이 없고,
        "그 시험 왜 없냐"는 물음에 답할 때 봅니다.
      </p>
      <AdminLifecycle />
    </>
  );
}
