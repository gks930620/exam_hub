// =============================================================================
// Lets — Flutter 토큰
// "밝고 반듯한 서비스 화면"
//
// ⚠ 자동 생성 파일이다. kits/design_kits_lets/tokens.json 을 고치고 node tools/build.mjs.
//
// 쓰는 법:
//   import 'kit_theme.dart';
//   import 'tokens.dart';
//
//   MaterialApp(
//     theme:     LetsKit.light,
//     darkTheme: LetsKit.dark,
//     themeMode: ThemeMode.system,
//   );
//
//   final t = KitTokens.of(context);   // 위젯에서 색 꺼내기
// =============================================================================

import 'package:flutter/material.dart';
import 'kit_theme.dart';

class LetsKit {
  const LetsKit._();

  static const String id = 'lets';
  static const String label = 'Lets';
  static const List<String> modes = ['light', 'dark'];

  /// 치수 — CSS 의 --r / --sp-4 … 와 이름·값이 같다
  static const double rSm = 8;
  static const double r = 10;
  static const double rLg = 16;
  static const double rPill = 999;
  static const double bw = 1;
  static const double sp1 = 4;
  static const double sp2 = 8;
  static const double sp3 = 12;
  static const double sp4 = 16;
  static const double sp5 = 20;
  static const double sp6 = 24;
  static const double sp8 = 32;
  static const double sp10 = 40;
  static const double navH = 62;
  static const double sidebarW = 210;
  static const double contentMax = 1200;
  static const double tabH = 62;
  static const double touch = 46;

  /// 타이포 크기·행간 — CSS 의 --t-md / --lh … 와 같다
  static const double tXs = 12;
  static const double tSm = 13.5;
  static const double tMd = 15;
  static const double tLg = 17;
  static const double tXl = 22;
  static const double t2xl = 30;
  static const double t3xl = 42;
  static const double lhTight = 1.35;
  static const double lh = 1.65;
  static const double lhRelaxed = 1.8;

  /// 굵기 — CSS 의 --w-bold … 와 같다
  static const FontWeight wNormal = FontWeight.w400;
  static const FontWeight wMedium = FontWeight.w500;
  static const FontWeight wBold = FontWeight.w700;
  static const FontWeight wHeavy = FontWeight.w800;

  static const BorderRadius brSm = BorderRadius.all(Radius.circular(rSm));
  static const BorderRadius br = BorderRadius.all(Radius.circular(r));
  static const BorderRadius brLg = BorderRadius.all(Radius.circular(rLg));
  static const BorderRadius brPill = BorderRadius.all(Radius.circular(999));

  static const KitTokens lightTokens = KitTokens(
    bg: Color(0xFFFFFFFF),
    bgSunken: Color(0xFFF2F3FB),
    surface: Color(0xFFFFFFFF),
    surfaceAlt: Color(0xFFF5F6FA),
    surfaceHover: Color(0xFFEEF0F9),
    text: Color(0xFF191B23),
    muted: Color(0xFF5F6473),
    muted2: Color(0xFF9AA0AE),
    point: Color(0xFF4B56E8),
    pointHover: Color(0xFF3B45D4),
    pointSoft: Color(0xFFECEEFD),
    pointText: Color(0xFF3B45D4),
    onPoint: Color(0xFFFFFFFF),
    point2: Color(0xFFF26A21),
    border: Color(0xFFE8E9F0),
    borderStrong: Color(0xFFD4D6E3),
    ok: Color(0xFF1F8F4E),
    okSoft: Color(0xFFE3F5EA),
    okText: Color(0xFF177040),
    warn: Color(0xFFB8710E),
    warnSoft: Color(0xFFFDF0DD),
    warnText: Color(0xFF96590A),
    err: Color(0xFFDC3A3A),
    errSoft: Color(0xFFFDEAEA),
    errText: Color(0xFFB32424),
    overlay: Color(0x80191B23),
    focus: Color(0xFF4B56E8),
    radiusSm: rSm, radius: r, radiusLg: rLg, borderWidth: bw,
    fontFamily: null,
  );

  static const KitTokens darkTokens = KitTokens(
    bg: Color(0xFF14162B),
    bgSunken: Color(0xFF0F1122),
    surface: Color(0xFF1C1F38),
    surfaceAlt: Color(0xFF242844),
    surfaceHover: Color(0xFF2D3252),
    text: Color(0xFFECEEFA),
    muted: Color(0xFFA3A8C4),
    muted2: Color(0xFF767C9C),
    point: Color(0xFF7B84FF),
    pointHover: Color(0xFF949BFF),
    pointSoft: Color(0x2E7B84FF),
    pointText: Color(0xFF9AA1FF),
    onPoint: Color(0xFF12142A),
    point2: Color(0xFFFF8A4C),
    border: Color(0xFF2C3152),
    borderStrong: Color(0xFF3D4370),
    ok: Color(0xFF4ECB84),
    okSoft: Color(0x294ECB84),
    okText: Color(0xFF6FD89C),
    warn: Color(0xFFE8A94A),
    warnSoft: Color(0x29E8A94A),
    warnText: Color(0xFFF0BD72),
    err: Color(0xFFF2686A),
    errSoft: Color(0x29F2686A),
    errText: Color(0xFFF68C8E),
    overlay: Color(0x99000000),
    focus: Color(0xFF7B84FF),
    radiusSm: rSm, radius: r, radiusLg: rLg, borderWidth: bw,
    fontFamily: null,
  );

  static ThemeData get light => buildKitTheme(lightTokens, Brightness.light);
  static ThemeData get dark => buildKitTheme(darkTokens, Brightness.dark);
}
