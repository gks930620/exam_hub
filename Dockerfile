# =====================================================================
# 단일 서버 이미지 — 이 컨테이너 하나가 React 화면 + REST API 를 모두 서빙한다.
#   1) node 스테이지에서 frontend(React CSR)를 빌드해 dist 생성
#   2) gradle 스테이지에서 dist 를 정적 리소스로 넣고 bootJar
#      (Gradle 의 프런트 빌드 태스크는 -PskipFrontend 로 끈다 — 이미 1)에서 만들었고
#       JDK 이미지에는 npm 이 없다)
#   3) JRE 스테이지에서 실행
# =====================================================================

# ===== Stage 1: 프런트 빌드 =====
FROM node:20-alpine AS web

# 이미지 안에서도 저장소와 같은 모양으로 둔다 — <루트>/frontend 와 <루트>/design_kits_lets.
# main.tsx 가 디자인 킷을 ../../design_kits_lets 로 읽기 때문에(킷 문서가 정한 위치),
# 프런트만 복사하면 컨테이너 안에서 그 경로가 비어 빌드가 이렇게 죽는다:
#   Could not resolve "../../design_kits_lets/base.css" from "src/main.tsx"
# 로컬에서는 폴더가 제자리에 있어 아무 증상이 없다 — 배포를 눌러야 아는 부류다(2026-09-22 실측).
# DockerBuildContextTest 가 이 관계를 지킨다.
WORKDIR /src/frontend

# 의존성 먼저 (캐싱)
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund

# 디자인 킷은 프런트의 형제 자리에 — 소스보다 먼저 두어 캐시를 덜 깬다
COPY design_kits_lets/ /src/design_kits_lets/

# 소스 복사 후 빌드
COPY frontend/ ./
RUN npm run build

# ===== Stage 2: 백엔드 빌드 =====
FROM gradle:8-jdk17 AS builder

WORKDIR /build

# Gradle 파일 먼저 복사 (캐싱 활용)
COPY build.gradle settings.gradle ./
COPY gradle ./gradle

# 의존성 먼저 다운로드 (캐싱)
RUN gradle dependencies --no-daemon || true

# 소스 복사
COPY src ./src

# 1)에서 만든 프런트 번들을 정적 리소스로 주입
COPY --from=web /src/frontend/dist ./src/main/resources/static

# 프런트 태스크는 건너뛰고(JDK 이미지에 npm 없음) JAR 만 만든다
RUN gradle bootJar --no-daemon -x test -PskipFrontend

# ===== Stage 3: 실행 =====
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 시간대 설정 및 curl 설치 (헬스체크용)
ENV TZ=Asia/Seoul
RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

COPY --from=builder /build/build/libs/*.jar app.jar

# 플랫폼(Railway 등)이 PORT 를 주입한다. EXPOSE 는 문서용 기본값.
EXPOSE 8081

# 헬스체크도 주입된 PORT 를 따라가야 한다 — 8080 하드코딩이면 포트가 다를 때 항상 실패한다.
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -fsS "http://localhost:${PORT:-8081}/healthz" || exit 1

ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "app.jar"]
