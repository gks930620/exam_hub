// 백엔드 DTO(설계 05 / CertificateDtos·FavoriteDtos·MeDtos)와 1:1 대응.

/** 카드가 어느 상태인가 — UPCOMING | PAST_ONLY(다음 회차 미정) | NONE | ROLLING(상시) */
export type ScheduleState = 'UPCOMING' | 'PAST_ONLY' | 'NONE' | 'ROLLING';

/**
 * 서버 CardBadge 코드 — REG_OPEN | REG_UPCOMING | EXAM_UPCOMING | EXAM_ONGOING | NONE.
 * 늘 수 있는 값이라 string 으로 받고, 화면 어휘는 lib/status.ts 가 정한다.
 * EXAM_ONGOING 은 dday 0, 시각은 시험 <b>종료일</b> 00:00 이다.
 */
export type CardBadge = string;

/** 회차 상태(도메인 ScheduleStatus). 공개 상세에는 ACTIVE·CANCELED 만 오고, 매니저 목록에는 PENDING_REVIEW 도 온다 */
export type ScheduleStatus = 'ACTIVE' | 'PENDING_REVIEW' | 'CANCELED' | 'DONE';

export interface CertItem {
  id: number;
  name: string;
  slug: string;
  series: string;
  seriesLabel: string;
  category: string | null;
  agency: string;
  favorited: boolean;
  /** false = 시험은 등록돼 있으나 일정이 아직 없음 (화면에 '일정 미정' 표기) */
  hasSchedule: boolean;
  /** 상시·예약제 — '일정'이 없는 시험. '일정 미정'이 아니라 '상시시험'으로 보여준다 */
  rolling: boolean;
  scheduleState: ScheduleState | null;
  nextLabel: string | null;
  nextAt: string | null;
  nextDday: number | null;
  nextBadge: CardBadge | null;
  lastExamDate: string | null;
}

export interface BrowseResponse {
  items: CertItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CategoryItem {
  name: string;
  count: number;
}

export interface CategoryResponse {
  items: CategoryItem[];
}

export interface ScheduleDto {
  id: number;
  year: number;
  round: number;
  examType: string; // WRITTEN | PRACTICAL
  regStartAt: string | null;
  regEndAt: string | null;
  examStartDate: string | null;
  examEndDate: string | null;
  resultDate: string | null;
  status: ScheduleStatus;
  /** 이 날짜를 어디서 얻었나 */
  provenance?: string | null;
  provenanceLabel?: string | null;
  /** 시행처에서 확인된 값인가. false 면 추정치라 경고해야 한다 */
  confirmed?: boolean;
}

export interface EventDto {
  type: string;
  label: string;
  dday: number;
  at: string;
}

export interface DetailResponse {
  id: number;
  name: string;
  category: string | null;
  agency: string;
  sourceUrl: string | null;
  collectedAt: string | null;
  favorited: boolean;
  /** 상시·예약제 — 일정 표 대신 "원하는 날짜에 신청" 안내를 보여준다 */
  rolling: boolean;
  nextEvent: EventDto | null;
  schedules: ScheduleDto[];
}

export interface FavoriteCard {
  certificateId: number;
  name: string;
  badge: CardBadge;
  badgeLabel: string;
  eventLabel: string;
  /** 다가오는 이벤트가 없으면 null (EXAM_ONGOING 이면 시험 종료일 00:00) */
  eventAt: string | null;
  /** 다가오는 이벤트가 없으면 null — 그때 D-0 을 그리면 거짓말이다 */
  dday: number | null;
  scheduleState: ScheduleState;
  /** PAST_ONLY 일 때 "마지막 시험 {날짜}" 로 보여준다 */
  lastExamDate: string | null;
  /**
   * 폐지·개칭으로 숨겨진 시험이면 그 사유(예: "폐지된 시험입니다", "이름이 바뀌었습니다 → 새 이름").
   * 카드는 남는다 — 사라지면 해제할 길이 없다. 이때 "일정이 확인되면 알려 드립니다"는 거짓말이다.
   */
  hiddenReason: string | null;
}

export interface FavoriteListResponse {
  items: FavoriteCard[];
}

export interface NotifySettings {
  notifyReg: boolean;
  notifyExam: boolean;
  notifyChange: boolean;
}

export interface CalendarEvent {
  date: string;
  type: string;
  certificateId: number;
  name: string;
  label: string;
}

export interface CalendarResponse {
  events: CalendarEvent[];
}

// ===== 계정 (설계 08) =====

export interface MeResponse {
  id: number;
  nickname: string;
  email: string | null;
  profileImage: string | null;
  /** 알림톡 수신 번호(숫자만). 없으면 카톡 알림을 못 받고 이메일로만 간다 */
  phoneNumber: string | null;
  /** 소셜 둘 + 매니저(LOCAL). 매니저는 소셜이 아니라 아이디·비밀번호로 들어온다 */
  provider: 'KAKAO' | 'GOOGLE' | 'LOCAL';
  role: 'USER' | 'ADMIN';
}

// ===== 커뮤니티 =====

export interface BoardItem {
  code: string;
  name: string;
  description: string;
}

export interface PostSummary {
  id: number;
  boardCode: string;
  boardName: string;
  title: string;
  authorName: string;
  authorId: number;
  viewCount: number;
  commentCount: number;
  createdAt: string;
}

export interface PostListResponse {
  items: PostSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PostDetail {
  id: number;
  boardCode: string;
  boardName: string;
  title: string;
  content: string;
  authorName: string;
  authorImage: string | null;
  authorId: number;
  viewCount: number;
  commentCount: number;
  createdAt: string;
  updatedAt: string;
  /** 지금 로그인한 사람이 이 글의 주인인지 — 수정/삭제 버튼 노출 근거 */
  mine: boolean;
}

export interface CommentItem {
  id: number;
  content: string;
  authorName: string;
  authorImage: string | null;
  authorId: number;
  createdAt: string;
  mine: boolean;
}

// ===== 관리자 =====

export interface AdminScheduleRow {
  id: number;
  certificateId: number;
  certificateName: string;
  year: number;
  round: number;
  examType: string;
  regStartAt: string | null;
  regEndAt: string | null;
  examStartDate: string | null;
  examEndDate: string | null;
  resultDate: string | null;
  /** PENDING_REVIEW = 수집된 일정이 30일 넘게 움직여 보류 — 공고와 대조해 저장하면 풀린다 */
  status: ScheduleStatus;
  sourceUrl: string | null;
  /**
   * 수집이 본 날짜가 이 행과 다르면 그 내용. 매니저 값은 덮지 않되 시행처가 뭐라고 하는지는 보여 준다.
   * 다시 저장하면 풀린다(수집값이 여전히 다르면 다음 수집이 다시 세운다).
   */
  sourceConflict: string | null;
}

// ===== 매니저(운영자) =====

export interface ManagerLoginResponse {
  token: string;
}

/** 데이터가 어떻게 채워지는지 — 자동/수기 구분(매니저 화면). */
export type SourceMode = 'AUTO' | 'AUTO_PENDING' | 'MANUAL' | 'EXCLUDED';

export interface DataSourceRow {
  group: string;
  exams: string;
  mode: SourceMode;
  modeLabel: string;
  modeGuide: string;
  sourceName: string;
  /** 원본 사이트 — 매니저가 여기를 열어 확인한다 */
  sourceUrl: string;
  /** 그 사이트 안에서 어디를 봐야 하는지 */
  checkPath: string;
  frequency: string;
  note: string;
}

export interface DataMapResponse {
  coverage: {
    totalExams: number; withSchedule: number; withoutSchedule: number; rolling: number;
    /** 일정 없음의 이유별 — 수기 필수 / 큐넷 공고 전(자동) / 크롤링 예정(자동) */
    manualNeeded: number; announcementPending: number; crawlPlanned: number;
  };
  sources: DataSourceRow[];
}

/** 매니저 일정 현황 — 시험 하나의 상태 한 줄 */
/** 매니저 현황 — 시험을 어디에 둘 것인가 */
export type OverviewBucket = 'TODO' | 'WAITING' | 'OK' | 'ROLLING';
/** 매니저가 지금 해야 하는 일. REVIEW_MOVE = 수집된 일정이 30일 넘게 움직여 보류(PENDING_REVIEW)된 회차가 있음 */
export type OverviewAction =
  'FIRST_INPUT' | 'REVIEW_MOVE' | 'SOURCE_MISMATCH' | 'NEXT_ROUND' | 'VERIFY' | 'CHECK_SOURCE';

export interface OverviewRow {
  certificateId: number;
  certificateName: string;
  category: string | null;
  agency: string | null;
  scheduleCount: number;
  source: 'AUTO' | 'CRAWL_PLANNED' | 'MANUAL' | 'ROLLING';
  sourceLabel: string;
  freshness: 'NONE' | 'PAST_ONLY' | 'UPCOMING';
  needsReview: boolean;
  action: OverviewAction | null;
  actionLabel: string | null;
  waitingReason: string | null;
  waitingLabel: string | null;
  bucket: OverviewBucket;
  lastExamDate: string | null;
  lastLabel: string | null;
  daysSinceLast: number | null;
  nextLabel: string | null;
  nextAt: string | null;
  nextDday: number | null;
  nextBadge: CardBadge | null;
}

export interface OverviewResponse {
  items: OverviewRow[];
  totalElements: number;
  page: number;
  bucketCounts: Partial<Record<OverviewBucket, number>>;
  actionCounts: Partial<Record<OverviewAction, number>>;
  waitingCounts: Partial<Record<string, number>>;
}

/** 시험 변천사 — 폐지·개칭된 시험 */
export interface LifecycleRow {
  certificateId: number;
  name: string;
  category: string | null;
  lifecycle: 'RENAMED' | 'ABOLISHED' | 'UNVERIFIED';
  lifecycleLabel: string;
  supersededBy: string | null;
  note: string | null;
}

export interface LifecycleResponse {
  items: LifecycleRow[];
  /** 확인이 필요한 건수 — "큐넷에 없다"만으로 폐지라 단정할 수 없다 */
  needsCheck: number;
}

/** GET /api/certificates/stats — 첫 화면 지표 타일 */
export interface StatsResponse {
  totalExams: number;
  withSchedule: number;
  registrationOpen: number;
  openingWithin7Days: number;
  /** 상시·예약제 — 일정 없음 안에 포함 */
  rolling: number;
}
