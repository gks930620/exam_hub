// 백엔드 DTO(설계 05 / CertificateDtos·FavoriteDtos·MeDtos)와 1:1 대응.

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
}

export interface SearchResponse {
  items: CertItem[];
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
  status: string;
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
  badge: string;
  badgeLabel: string;
  eventLabel: string;
  eventAt: string;
  dday: number;
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
  provider: 'KAKAO' | 'GOOGLE';
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
  status: string;
  sourceUrl: string | null;
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
  coverage: { totalExams: number; withSchedule: number; withoutSchedule: number; rolling: number };
  sources: DataSourceRow[];
}

/** 매니저 일정 현황 — 시험 하나의 상태 한 줄 */
export type ScheduleStatusKind = 'NONE' | 'PAST' | 'OPEN' | 'UPCOMING' | 'ROLLING';

export interface OverviewRow {
  certificateId: number;
  certificateName: string;
  category: string | null;
  agency: string | null;
  scheduleCount: number;
  status: ScheduleStatusKind;
  nextLabel: string | null;
  nextRegStartAt: string | null;
  nextRegEndAt: string | null;
  nextExamDate: string | null;
  /** 접수 마감까지 남은 날. 접수 중일 때만 */
  regDDay: number | null;
}

export interface OverviewResponse {
  items: OverviewRow[];
  totalElements: number;
  page: number;
  /** 상태별 개수 — 필터를 걸어도 안 변한다 */
  counts: Partial<Record<ScheduleStatusKind, number>>;
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
}
