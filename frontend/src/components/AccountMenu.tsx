import { useEffect, useId, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import Avatar from './Avatar';
import Icon from './Icon';
import type { MeResponse } from '../api/types';

/**
 * 상단바 계정 메뉴 — 사람 아이콘 하나, 누르면 내 정보·로그아웃이 내려온다.
 *
 * <p>그전엔 닉네임이 적힌 칩이 내 정보 페이지로 가는 링크였고, 로그아웃은 그 페이지 안에 있었다.
 * 이름은 "저기가 내 계정이구나"를 말해 주지 않고, 로그아웃은 두 번 들어가야 보였다(사용자 지적 2026-09-11).
 * 사람 아이콘은 어느 서비스에서나 "내 계정"이고, 로그아웃은 그 아래에서 바로 고른다.
 *
 * <p>사진이 있으면 사진이 곧 "나"라 그대로 쓰고, 없을 때만 사람 아이콘이다 — 닉네임 첫 글자는 이름처럼
 * 읽혀 계정 표식이 못 된다. 닉네임은 메뉴 머리에 적는다.
 *
 * <p>열림은 <b>클릭</b>이다. 마우스를 올리기만 해도 열리면 지나가다 열리고, 터치에는 hover 가 없다.
 * 불투명 패널이고 유리(blur)는 쓰지 않는다(설계 05 §12·§2). 상태는 클래스가 아니라 aria-expanded 다(§7-1).
 */
export default function AccountMenu({ me, onLogout }: { me: MeResponse; onLogout: () => void }) {
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const menuId = useId();
  const location = useLocation();

  // 어디로든 이동하면 닫는다 — 메뉴가 화면을 따라다니면 안 된다
  useEffect(() => { setOpen(false); }, [location.pathname]);

  useEffect(() => {
    if (!open) return;
    function onDocClick(e: MouseEvent) {
      if (!root.current?.contains(e.target as Node)) setOpen(false);
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        setOpen(false);
        trigger.current?.focus();
      }
    }
    document.addEventListener('mousedown', onDocClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDocClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  return (
    <div className="account-menu" ref={root}>
      <button
        ref={trigger}
        type="button"
        className="who"
        aria-label={`내 계정 — ${me.nickname}`}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={menuId}
        onClick={() => setOpen((v) => !v)}
      >
        {me.profileImage
          ? <Avatar src={me.profileImage} nickname={me.nickname} />
          : <span className="avatar-fallback" aria-hidden="true"><Icon name="user" size={16} /></span>}
      </button>

      {open && (
        <div className="account-menu__panel k-card" role="menu" id={menuId} aria-label="계정 메뉴">
          <div className="account-menu__head">
            <b>{me.nickname}</b>
            {me.email && <span>{me.email}</span>}
          </div>
          <Link to="/me" role="menuitem" className="account-menu__item">
            <Icon name="user" size={16} /> 내 정보
          </Link>
          <button type="button" role="menuitem" className="account-menu__item" onClick={() => { setOpen(false); onLogout(); }}>
            <Icon name="external" size={16} /> 로그아웃
          </button>
        </div>
      )}
    </div>
  );
}
