import { api } from './client';
import type {
  DetailResponse, FavoriteListResponse,
  NotifySettings, CalendarResponse, BrowseResponse, CategoryResponse,
  MeResponse, BoardItem, PostListResponse, PostDetail, CommentItem, AdminScheduleRow,
  ManagerLoginResponse, DataMapResponse, OverviewResponse, LifecycleResponse, StatsResponse,
  CollectHealthResponse, NotificationHealthResponse, TestNotificationResult, NotificationHistoryItem,
} from './types';

export const examApi = {
  /** 전체 둘러보기 — 검색어 없이도, 일정 없는 시험도 포함해 페이지 단위로 가져온다(서버 상한 100). */
  browse: (params: { query?: string; category?: string; state?: string; page?: number; size?: number }) => {
    const q = new URLSearchParams();
    if (params.query) q.set('query', params.query);
    if (params.category) q.set('category', params.category);
    // OPEN(지금 접수 중) · SOON(곧 접수). 서버가 그 상태만 걸러 주고 급한 것부터 정렬한다.
    if (params.state) q.set('state', params.state);
    q.set('page', String(params.page ?? 0));
    q.set('size', String(params.size ?? 24));
    return api.get<BrowseResponse>(`/api/certificates/browse?${q.toString()}`);
  },

  categories: () => api.get<CategoryResponse>('/api/certificates/categories'),

  stats: () => api.get<StatsResponse>('/api/certificates/stats'),

  detail: (id: number) => api.get<DetailResponse>(`/api/certificates/${id}`),

  favorites: () => api.get<FavoriteListResponse>('/api/me/favorites'),

  addFavorite: (certificateId: number) =>
    api.post<{ certificateId: number; favoriteCount: number }>('/api/me/favorites', { certificateId }),

  removeFavorite: (certificateId: number) =>
    api.del<void>(`/api/me/favorites/${certificateId}`),

  notifySettings: () => api.get<NotifySettings>('/api/me/notify-settings'),

  saveNotifySettings: (s: NotifySettings) =>
    api.put<NotifySettings>('/api/me/notify-settings', s),

  /** 내가 받은 알림 목록 — "나한테 뭘 보냈다는 건지" */
  notificationHistory: () =>
    api.get<{ items: NotificationHistoryItem[] }>('/api/me/notifications'),

  /** 확인 메일 한 통 — 내 주소로 진짜 오는지 지금 눌러 본다 */
  sendTestNotification: () =>
    api.post<TestNotificationResult>('/api/me/notify-settings/test', {}),

  calendar: (year: number, month: number) =>
    api.get<CalendarResponse>(`/api/me/calendar?year=${year}&month=${month}`),

  // ===== 계정 (설계 08) =====

  me: () => api.get<MeResponse>('/api/me'),

  changeNickname: (nickname: string) => api.patch<MeResponse>('/api/me', { nickname }),

  /** 매니저 폼 로그인 — 소셜을 타지 않는다. */
  managerLogin: (username: string, password: string) =>
    api.post<ManagerLoginResponse>('/api/manager/login', { username, password }),

  managerAvailable: () =>
    api.get<{ configured: boolean }>('/api/manager/available'),

  /** 매니저용 데이터 지도 — 뭐가 자동이고 뭐가 수기인지 + 원본 사이트 */
  adminDataMap: () => api.get<DataMapResponse>('/api/admin/data-map'),

  /** 수집 건강 — 지금 어느 소스가 고장났나(실패·0건·멈춤) */
  adminCollectHealth: () => api.get<CollectHealthResponse>('/api/admin/collect-health'),

  /** 알림 건강 — 약속이 지켜지고 있나(막힌 예약·도달 채널). 성공률이 아니라 도달을 본다 */
  adminNotificationHealth: () => api.get<NotificationHealthResponse>("/api/admin/notification-health"),

  /** 시험 변천사 — 폐지·개칭된 시험이 어디로 갔나 */
  adminLifecycle: () => api.get<LifecycleResponse>('/api/admin/lifecycle'),

  /** 시험별 일정 현황 — 급한 것(일정 없음·지난 것)이 위로 온다 */
  adminOverview: (params: { bucket?: string; action?: string; query?: string; page?: number }) => {
    const q = new URLSearchParams();
    if (params.bucket) q.set('bucket', params.bucket);
    if (params.action) q.set('action', params.action);
    if (params.query) q.set('query', params.query);
    q.set('page', String(params.page ?? 0));
    return api.get<OverviewResponse>(`/api/admin/overview?${q.toString()}`);
  },

  changeEmail: (email: string) =>
    api.put<MeResponse>('/api/me/email', { email }),

  changePhone: (phoneNumber: string) =>
    api.put<MeResponse>('/api/me/phone', { phoneNumber }),

  withdraw: () => api.del<void>('/api/me'),

  // ===== 커뮤니티 =====

  boards: () => api.get<{ items: BoardItem[] }>('/api/community/boards'),

  posts: (params: { board?: string; page?: number; size?: number }) => {
    const q = new URLSearchParams();
    if (params.board) q.set('board', params.board);
    q.set('page', String(params.page ?? 0));
    q.set('size', String(params.size ?? 20));
    return api.get<PostListResponse>(`/api/community/posts?${q.toString()}`);
  },

  post: (id: number) => api.get<PostDetail>(`/api/community/posts/${id}`),

  createPost: (body: { boardCode: string; title: string; content: string }) =>
    api.post<PostDetail>('/api/community/posts', body),

  updatePost: (id: number, body: { title: string; content: string }) =>
    api.put<PostDetail>(`/api/community/posts/${id}`, body),

  deletePost: (id: number) => api.del<void>(`/api/community/posts/${id}`),

  comments: (postId: number) =>
    api.get<{ items: CommentItem[] }>(`/api/community/posts/${postId}/comments`),

  addComment: (postId: number, content: string) =>
    api.post<CommentItem>(`/api/community/posts/${postId}/comments`, { content }),

  deleteComment: (id: number) => api.del<void>(`/api/community/comments/${id}`),

  // ===== 관리자 (수기 일정 입력) =====

  /** 시험 하나의 회차 목록 — ACTIVE 와 함께 보류(PENDING_REVIEW) 행도 온다 */
  adminSchedules: (certificateId: number) =>
    api.get<AdminScheduleRow[]>(`/api/admin/schedules?certificateId=${certificateId}`),

  adminUpsertSchedule: (body: Record<string, unknown>) =>
    api.post<AdminScheduleRow>('/api/admin/schedules', body),

  adminCancelSchedule: (scheduleId: number) =>
    api.del<void>(`/api/admin/schedules/${scheduleId}`),
};
