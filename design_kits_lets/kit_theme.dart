// =============================================================================
// core/kit_theme.dart — 모든 킷이 공유하는 Flutter 골격.
//
// 웹의 core/components.css 와 같은 위치다: **컴포넌트는 여기, 값은 킷에.**
//   core/kit_theme.dart        ← 이 파일 (모든 킷 공통)
//   kits/<id>/tokens.dart      ← 킷별 값 (자동 생성)
//
// 쓰는 법
//   import 'core/kit_theme.dart';
//   import 'kits/cozy/tokens.dart';
//
//   MaterialApp(
//     theme: CozyKit.light, darkTheme: CozyKit.dark, themeMode: ThemeMode.system,
//   );
//
// 위젯에서 색을 직접 쓰지 말 것. 항상 토큰으로 꺼낸다:
//   final t = KitTokens.of(context);
//   Container(color: t.surface, ...)
//
// 킷을 갈아 끼우려면 import 한 줄과 CozyKit → 다른킷 이름만 바꾸면 된다.
// 의존성 없음 (순수 Flutter / Material 3).
// =============================================================================

import 'package:flutter/material.dart';

/// 색·치수 토큰. CSS 의 `--bg` `--point` … 와 **이름이 1:1로 같다**.
/// (CSS 의 kebab-case → Dart 의 camelCase: `--surface-alt` → `surfaceAlt`)
@immutable
class KitTokens extends ThemeExtension<KitTokens> {
  // 면
  final Color bg, bgSunken, surface, surfaceAlt, surfaceHover;
  // 글자
  final Color text, muted, muted2;
  // 강조
  final Color point, pointHover, pointSoft, pointText, onPoint, point2;
  // 선
  final Color border, borderStrong;
  // 의미색
  final Color ok, okSoft, okText;
  final Color warn, warnSoft, warnText;
  final Color err, errSoft, errText;
  // 기타
  final Color overlay, focus;
  // 형태
  final double radiusSm, radius, radiusLg, borderWidth;
  final String? fontFamily;

  const KitTokens({
    required this.bg,
    required this.bgSunken,
    required this.surface,
    required this.surfaceAlt,
    required this.surfaceHover,
    required this.text,
    required this.muted,
    required this.muted2,
    required this.point,
    required this.pointHover,
    required this.pointSoft,
    required this.pointText,
    required this.onPoint,
    required this.point2,
    required this.border,
    required this.borderStrong,
    required this.ok,
    required this.okSoft,
    required this.okText,
    required this.warn,
    required this.warnSoft,
    required this.warnText,
    required this.err,
    required this.errSoft,
    required this.errText,
    required this.overlay,
    required this.focus,
    required this.radiusSm,
    required this.radius,
    required this.radiusLg,
    required this.borderWidth,
    this.fontFamily,
  });

  /// 어디서든 토큰을 꺼내는 통로. 테마에 KitTokens 가 없으면 예외를 던진다
  /// (조용히 기본색으로 떨어지면 디자인이 어긋난 걸 못 알아채기 때문).
  static KitTokens of(BuildContext context) {
    final t = Theme.of(context).extension<KitTokens>();
    assert(t != null, 'KitTokens 가 테마에 없다. MaterialApp 에 <킷>Kit.light / .dark 를 연결했는지 확인할 것.');
    return t!;
  }

  BorderRadius get brSm => BorderRadius.circular(radiusSm);
  BorderRadius get br => BorderRadius.circular(radius);
  BorderRadius get brLg => BorderRadius.circular(radiusLg);
  BorderSide get side => BorderSide(color: border, width: borderWidth);

  @override
  KitTokens copyWith({Color? bg, Color? surface, Color? point, Color? text}) => KitTokens(
        bg: bg ?? this.bg,
        bgSunken: bgSunken,
        surface: surface ?? this.surface,
        surfaceAlt: surfaceAlt,
        surfaceHover: surfaceHover,
        text: text ?? this.text,
        muted: muted,
        muted2: muted2,
        point: point ?? this.point,
        pointHover: pointHover,
        pointSoft: pointSoft,
        pointText: pointText,
        onPoint: onPoint,
        point2: point2,
        border: border,
        borderStrong: borderStrong,
        ok: ok, okSoft: okSoft, okText: okText,
        warn: warn, warnSoft: warnSoft, warnText: warnText,
        err: err, errSoft: errSoft, errText: errText,
        overlay: overlay,
        focus: focus,
        radiusSm: radiusSm, radius: radius, radiusLg: radiusLg, borderWidth: borderWidth,
        fontFamily: fontFamily,
      );

  @override
  KitTokens lerp(ThemeExtension<KitTokens>? other, double v) {
    if (other is! KitTokens) return this;
    Color c(Color a, Color b) => Color.lerp(a, b, v)!;
    double d(double a, double b) => a + (b - a) * v;
    return KitTokens(
      bg: c(bg, other.bg),
      bgSunken: c(bgSunken, other.bgSunken),
      surface: c(surface, other.surface),
      surfaceAlt: c(surfaceAlt, other.surfaceAlt),
      surfaceHover: c(surfaceHover, other.surfaceHover),
      text: c(text, other.text),
      muted: c(muted, other.muted),
      muted2: c(muted2, other.muted2),
      point: c(point, other.point),
      pointHover: c(pointHover, other.pointHover),
      pointSoft: c(pointSoft, other.pointSoft),
      pointText: c(pointText, other.pointText),
      onPoint: c(onPoint, other.onPoint),
      point2: c(point2, other.point2),
      border: c(border, other.border),
      borderStrong: c(borderStrong, other.borderStrong),
      ok: c(ok, other.ok), okSoft: c(okSoft, other.okSoft), okText: c(okText, other.okText),
      warn: c(warn, other.warn), warnSoft: c(warnSoft, other.warnSoft), warnText: c(warnText, other.warnText),
      err: c(err, other.err), errSoft: c(errSoft, other.errSoft), errText: c(errText, other.errText),
      overlay: c(overlay, other.overlay),
      focus: c(focus, other.focus),
      radiusSm: d(radiusSm, other.radiusSm),
      radius: d(radius, other.radius),
      radiusLg: d(radiusLg, other.radiusLg),
      borderWidth: d(borderWidth, other.borderWidth),
      fontFamily: v < .5 ? fontFamily : other.fontFamily,
    );
  }
}

/// 토큰 → ThemeData. 킷별 tokens.dart 가 이 함수를 부른다.
/// Material 기본 위젯(Button·TextField·Card…)이 토큰을 따르게 만드는 곳이다.
ThemeData buildKitTheme(KitTokens t, Brightness brightness) {
  final scheme = ColorScheme(
    brightness: brightness,
    primary: t.point,
    onPrimary: t.onPoint,
    primaryContainer: t.pointSoft,
    onPrimaryContainer: t.pointText,
    secondary: t.point2,
    onSecondary: t.onPoint,
    error: t.err,
    onError: t.onPoint,
    errorContainer: t.errSoft,
    onErrorContainer: t.errText,
    surface: t.surface,
    onSurface: t.text,
    surfaceContainerHighest: t.surfaceAlt,
    onSurfaceVariant: t.muted,
    outline: t.border,
    outlineVariant: t.borderStrong,
    shadow: t.overlay,
  );

  return ThemeData(
    useMaterial3: true,
    brightness: brightness,
    colorScheme: scheme,
    fontFamily: t.fontFamily,
    scaffoldBackgroundColor: t.bg,
    canvasColor: t.surface,
    dividerColor: t.border,
    extensions: <ThemeExtension<dynamic>>[t],

    appBarTheme: AppBarTheme(
      backgroundColor: t.surface,
      foregroundColor: t.text,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      scrolledUnderElevation: 0,
      shape: Border(bottom: t.side),
    ),

    cardTheme: CardThemeData(
      color: t.surface,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(borderRadius: t.brLg, side: t.side),
    ),

    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: t.point,
        foregroundColor: t.onPoint,
        elevation: 0,
        minimumSize: const Size(0, 44), // --touch
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
        shape: RoundedRectangleBorder(borderRadius: t.br),
        textStyle: const TextStyle(fontWeight: FontWeight.w700),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: t.text,
        backgroundColor: t.surface,
        minimumSize: const Size(0, 44),
        side: BorderSide(color: t.borderStrong, width: t.borderWidth),
        shape: RoundedRectangleBorder(borderRadius: t.br),
        textStyle: const TextStyle(fontWeight: FontWeight.w700),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(foregroundColor: t.muted, minimumSize: const Size(0, 44)),
    ),

    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: t.surfaceAlt,
      hintStyle: TextStyle(color: t.muted2),
      labelStyle: TextStyle(color: t.muted),
      contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
      border: OutlineInputBorder(borderRadius: t.br, borderSide: BorderSide(color: t.borderStrong, width: t.borderWidth)),
      enabledBorder: OutlineInputBorder(borderRadius: t.br, borderSide: BorderSide(color: t.borderStrong, width: t.borderWidth)),
      focusedBorder: OutlineInputBorder(borderRadius: t.br, borderSide: BorderSide(color: t.point, width: t.borderWidth + 1)),
      errorBorder: OutlineInputBorder(borderRadius: t.br, borderSide: BorderSide(color: t.err, width: t.borderWidth)),
    ),

    chipTheme: ChipThemeData(
      backgroundColor: t.surface,
      selectedColor: t.pointSoft,
      side: BorderSide(color: t.borderStrong, width: t.borderWidth),
      labelStyle: TextStyle(color: t.text),
      shape: const StadiumBorder(),
    ),

    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: t.surface,
      indicatorColor: t.pointSoft,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      labelTextStyle: WidgetStatePropertyAll(TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: t.muted2)),
    ),

    dialogTheme: DialogThemeData(
      backgroundColor: t.surface,
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(borderRadius: t.brLg, side: t.side),
    ),
    bottomSheetTheme: BottomSheetThemeData(
      backgroundColor: t.surface,
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(t.radiusLg))),
    ),
    dividerTheme: DividerThemeData(color: t.border, thickness: t.borderWidth, space: t.borderWidth),
    snackBarTheme: SnackBarThemeData(backgroundColor: t.text, contentTextStyle: TextStyle(color: t.bg)),
    progressIndicatorTheme: ProgressIndicatorThemeData(color: t.point, linearTrackColor: t.surfaceAlt),
    switchTheme: SwitchThemeData(
      thumbColor: WidgetStateProperty.resolveWith((s) => s.contains(WidgetState.selected) ? t.surface : t.surface),
      trackColor: WidgetStateProperty.resolveWith((s) => s.contains(WidgetState.selected) ? t.point : t.borderStrong),
    ),
  );
}

// =============================================================================
// 본보기 위젯 — 전체 라이브러리가 아니라 "토큰을 이렇게 쓴다"는 예시다.
// 나머지 위젯도 같은 방식으로 만든다: 색·치수는 반드시 KitTokens.of(context) 에서.
// =============================================================================

/// 웹의 `.k-badge` 대응
class KitBadge extends StatelessWidget {
  final String label;
  final KitStatus status;
  const KitBadge(this.label, {super.key, this.status = KitStatus.neutral});

  @override
  Widget build(BuildContext context) {
    final t = KitTokens.of(context);
    final (bgc, fg) = switch (status) {
      KitStatus.ok => (t.okSoft, t.okText),
      KitStatus.warn => (t.warnSoft, t.warnText),
      KitStatus.err => (t.errSoft, t.errText),
      KitStatus.point => (t.pointSoft, t.pointText),
      KitStatus.neutral => (t.surfaceAlt, t.muted),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(color: bgc, borderRadius: t.brSm),
      child: Text(label, style: TextStyle(color: fg, fontSize: 11, fontWeight: FontWeight.w800)),
    );
  }
}

enum KitStatus { neutral, point, ok, warn, err }

/// 웹의 `.k-card` 대응
class KitCard extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry padding;
  const KitCard({super.key, required this.child, this.padding = const EdgeInsets.all(20)});

  @override
  Widget build(BuildContext context) {
    final t = KitTokens.of(context);
    return Container(
      padding: padding,
      decoration: BoxDecoration(color: t.surface, borderRadius: t.brLg, border: Border.fromBorderSide(t.side)),
      child: child,
    );
  }
}

/// 웹의 `.k-stat` 대응
class KitStat extends StatelessWidget {
  final String label, value;
  final bool highlight;
  const KitStat({super.key, required this.label, required this.value, this.highlight = false});

  @override
  Widget build(BuildContext context) {
    final t = KitTokens.of(context);
    return KitCard(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label.toUpperCase(),
              style: TextStyle(fontSize: 11, letterSpacing: 1.2, color: t.muted, fontWeight: FontWeight.w700)),
          const SizedBox(height: 4),
          Text(value,
              style: TextStyle(
                  fontSize: 27, height: 1.25, fontWeight: FontWeight.w800,
                  letterSpacing: -.5, color: highlight ? t.point : t.text)),
        ],
      ),
    );
  }
}

/// 웹의 `.k-alert` 대응
class KitAlert extends StatelessWidget {
  final String message;
  final KitStatus status;
  const KitAlert(this.message, {super.key, this.status = KitStatus.neutral});

  @override
  Widget build(BuildContext context) {
    final t = KitTokens.of(context);
    final (bgc, fg) = switch (status) {
      KitStatus.ok => (t.okSoft, t.okText),
      KitStatus.warn => (t.warnSoft, t.warnText),
      KitStatus.err => (t.errSoft, t.errText),
      KitStatus.point => (t.pointSoft, t.pointText),
      KitStatus.neutral => (t.surfaceAlt, t.muted),
    };
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(color: bgc, borderRadius: t.br),
      child: Text(message, style: TextStyle(color: fg, fontSize: 13)),
    );
  }
}
