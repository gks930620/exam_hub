import { api } from './client';
import type {
  DetailResponse, FavoriteListResponse,
  NotifySettings, CalendarResponse, BrowseResponse, CategoryResponse,
  MeResponse, BoardItem, PostListResponse, PostDetail, CommentItem, AdminScheduleRow,
  ManagerLoginResponse, DataMapResponse, OverviewResponse, LifecycleResponse, StatsResponse,
} from './types';

export const examApi = {
  /** 전체 둘러보기 — 검색어 없이도, 일정 없는 시험도 포함해 페이지 단위로 가져온다(서버 상한 100). */
  browse: (params: { query?: string; category?: string; page?: number; size?: number }) => {
    const q = new URLSearchParams();
    if (params.query) q.set('query', params.query);
    if (params.category) q.set('category', params.category);
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
