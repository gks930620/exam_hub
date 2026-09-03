/**
 * 선(stroke) 아이콘 — 이모지 대신 쓴다.
 *
 * 이모지는 색을 못 입힌다. 연보라 타일 위의 ⌕ 는 흐릿하고, 카카오 노랑 위의 💬 는 흰 말풍선이
 * 되어 "아이콘이 흰색이라 안 보인다"는 지적을 받았다(2026-09-02). SVG 는 currentColor 를 따라
 * 타일·버튼·다크 모드 어디서든 글자색과 같이 움직인다 — 킷 extras ①의 "아이콘 타일" 문법에 맞는다.
 *
 * 경로는 24×24 좌표, 1.75px 선. 모양은 Feather/Lucide 계열의 단순한 형태만 골랐다.
 *
 * `as const satisfies` — 키를 리터럴로 좁혀 IconName 이 실제 이름만 받게 한다(오타가 컴파일에서 걸린다).
 */
const PATHS = {
  search: 'M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM20 20l-4-4',
  star: 'M12 3l2.9 5.9 6.5.9-4.7 4.6 1.1 6.5L12 17.8 6.2 20.9l1.1-6.5L2.6 9.8l6.5-.9z',
  bookmark: 'M6 3h12v18l-6-4-6 4z',
  calendar: 'M4 6h16v14H4zM4 10h16M8 3v4M16 3v4',
  chat: 'M4 5h16v11H9l-5 4z',
  user: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21a8 8 0 0 1 16 0',
  settings: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z',
  pencil: 'M4 20h4l11-11-4-4L4 16zM13 7l4 4',
  list: 'M9 6h11M9 12h11M9 18h11M4 6h1M4 12h1M4 18h1',
  layers: 'M12 3l9 5-9 5-9-5zM3 13l9 5 9-5M3 17l9 5 9-5',
  clock: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v5l3 2',
  moon: 'M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z',
  sun: 'M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4',
  chevronLeft: 'M15 5l-7 7 7 7',
  chevronRight: 'M9 5l7 7-7 7',
  chevronDown: 'M5 9l7 7 7-7',
  external: 'M14 4h6v6M20 4l-9 9M18 14v6H4V6h6',
  plus: 'M12 5v14M5 12h14',
} as const satisfies Record<string, string>;

export type IconName = keyof typeof PATHS;

export default function Icon({ name, size = 20, filled = false, className }: {
  name: IconName;
  size?: number;
  /** 별표처럼 채운 상태가 있는 아이콘용 */
  filled?: boolean;
  className?: string;
}) {
  return (
    <svg className={className} width={size} height={size} viewBox="0 0 24 24"
         fill={filled ? 'currentColor' : 'none'} stroke="currentColor"
         strokeWidth={1.75} strokeLinecap="round" strokeLinejoin="round"
         aria-hidden="true" focusable="false">
      <path d={PATHS[name]} />
    </svg>
  );
}
