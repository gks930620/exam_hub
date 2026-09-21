import AdminDataMap from './AdminDataMap';

/**
 * 수집 지도 — <b>무엇이 자동으로 들어오고, 무엇을 내가 넣어야 하나.</b>
 *
 * <p>입력 화면만 있으면 매니저는 무엇을 넣어야 할지 모른다. 더 나쁜 건 자동 수집되는 시험을
 * 손으로 넣다가 다음 수집에 덮어써지는 헛일이다.
 */
export default function AdminSources() {
  return (
    <>
      <p className="fineprint fineprint--lead">
        <b>수기 입력</b>으로 표시된 것만 직접 넣습니다. <b>자동 수집</b>은 손대면 다음 수집에 덮어써집니다.
        직접 넣는 건 <b>할 일</b> 탭의 [넣기]에서 합니다. 각 갈래를 펼치면 어느 사이트의 어느 화면을 봐야 하는지와 원본 링크가 있습니다.
      </p>
      <AdminDataMap />
    </>
  );
}
