import { useEffect, useState } from 'react';

/**
 * 프로필 사진. 못 불러오면 닉네임 첫 글자로 물러난다.
 *
 * 구글 프로필 사진(lh3.googleusercontent.com)은 Referer 헤더가 붙으면 403 을 준다 —
 * 그래서 referrerPolicy="no-referrer" 로 요청한다. 이게 구글 로그인 뒤 사진이 깨지던 원인이다.
 *
 * 그래도 못 받는 경우가 있다(사진을 지웠거나, URL 이 만료됐거나). 사진은 장식이라
 * 깨진 아이콘을 띄우느니 첫 글자로 조용히 물러나는 게 맞다.
 */
export default function Avatar({ src, nickname }: { src: string | null; nickname: string }) {
  const [failed, setFailed] = useState(false);

  // 다른 계정으로 다시 로그인하면 새 사진은 다시 시도해야 한다
  useEffect(() => { setFailed(false); }, [src]);

  if (!src || failed) {
    return (
      <span className="avatar-fallback" aria-hidden="true">
        {nickname.slice(0, 1)}
      </span>
    );
  }

  return (
    <img
      src={src}
      alt=""
      referrerPolicy="no-referrer"
      onError={() => setFailed(true)}
    />
  );
}
