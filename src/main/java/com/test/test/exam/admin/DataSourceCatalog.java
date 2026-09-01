package com.test.test.exam.admin;

import java.util.List;

/**
 * <b>어떤 데이터가 자동으로 들어오고, 어떤 데이터는 사람이 넣어야 하는가</b> — 매니저가 보는 지도.
 *
 * <p>이 내용은 설계문서(07 시험일정_수집방법)에도 있지만, <b>문서는 매니저가 안 읽는다.</b>
 * 일정을 넣으러 화면에 들어온 사람에게 "이건 자동이니 손대지 마세요", "이건 저 사이트를 열어
 * 보고 손으로 넣으세요"를 그 자리에서 보여줘야 실제로 지켜진다. 그래서 API 로 내보낸다.
 *
 * <p>DB 가 아니라 코드에 둔 이유: 수집기(ScheduleSource) 구현과 <b>같이 움직여야</b> 하기 때문이다.
 * 스크래퍼를 하나 추가했는데 화면 안내는 "수기"로 남아 있으면 매니저가 헛일을 한다.
 * 소스를 추가할 때 여기도 같이 고치는 게 규칙이다({@code DataSourceCatalogTest} 가 어긋남을 잡는다).
 */
public final class DataSourceCatalog {

    private DataSourceCatalog() {
    }

    /** 이 데이터가 어떻게 채워지는가. */
    public enum Mode {
        /** 서버가 주기적으로 가져온다. 매니저는 손대지 않는다(덮어써진다). */
        AUTO("자동화 (API·크롤링)", "서버가 매일 05:00 에 가져옵니다. 손으로 고쳐도 다음 수집 때 덮어써집니다."),
        /** 코드는 있는데 키·설정이 없어 지금은 안 도는 것. 손대기 전에 그 사실을 알아야 한다. */
        AUTO_PENDING("자동(대기 중)", "수집기는 만들어져 있지만 아직 안 돕니다. 켜지기 전까지는 수기로 넣어야 합니다."),
        /** 사람이 원본 사이트를 보고 넣는다. */
        MANUAL("수기 입력", "자동 수집이 없습니다. 아래 사이트에서 확인해 직접 넣어 주세요."),
        /** 접수 마감이 없어 이 서비스의 알림 모델과 안 맞는 것. 넣지 않는다. */
        EXCLUDED("대상 아님", "상시·예약제라 '접수 마감'이 없습니다. 알림할 게 없어 넣지 않습니다.");

        private final String label;
        private final String guide;

        Mode(String label, String guide) {
            this.label = label;
            this.guide = guide;
        }

        public String getLabel() {
            return label;
        }

        public String getGuide() {
            return guide;
        }
    }

    /**
     * 한 갈래의 데이터가 어디서 오는지.
     *
     * @param group      묶음(국가기술자격·영어·일본어…)
     * @param exams      해당 시험들
     * @param mode       자동인가 수기인가
     * @param sourceName 시행처
     * @param sourceUrl  <b>원본 사이트</b> — 매니저가 여기를 열어 확인한다
     * @param checkPath  그 사이트 안에서 어디를 봐야 하는지(사이트 개편이 잦아 경로가 아니라 메뉴명으로 적는다)
     * @param frequency  회차 빈도 — 얼마나 자주 확인해야 하는지의 기준
     * @param note       주의사항
     */
    public record Entry(
            String group,
            String exams,
            Mode mode,
            String sourceName,
            String sourceUrl,
            String checkPath,
            String frequency,
            String note
    ) {
    }

    /**
     * 출처 목록. 근거는 설계문서 07(수집방법)·09(전수목록)와 운영/04(어학 스크래퍼 실측)다.
     * 순서는 매니저가 손댈 일이 많은 순 — 수기가 위로 온다.
     */
    public static List<Entry> entries() {
        return List.of(
                // ── 수기: 매니저가 실제로 할 일 ──
                new Entry("국가기술자격(큐넷 아님)", "유통관리사 1·2·3급, 전자상거래관리사 1·2급, 전자상거래운용사, 한글속기 1·2·3급",
                        Mode.AUTO, "대한상공회의소",
                        "https://license.korcham.net/co/examguide03.do?cd=0201&mm=31",
                        "종목소개 → 시험일정 (종목마다 페이지가 따로다)",
                        "정기 연 2~3회",
                        "스크래핑 구현·검증 완료(9종 19건). 국가기술자격인데 시행처가 상의라 큐넷 API 에는 없다."),
                new Entry("상시시험(대상 아님)", "컴퓨터활용능력 1·2급, 워드프로세서, 비서, 전산회계운용사, 무역영어",
                        Mode.EXCLUDED, "대한상공회의소",
                        "https://license.korcham.net/co/examschedule.do",
                        "—",
                        "상시 — 원하는 날짜 선택",
                        "⚠️ 컴활은 2021년부터 정기검정이 폐지되고 상시검정만 한다. 응시자가 날짜·시험장을 직접 골라 접수해서 '접수 마감'이 없다 → 알릴 것이 없다. 시행처 일정 페이지도 비어 있다(TOEFL·IELTS 와 같은 부류)."),
                new Entry("국가기술자격(큐넷 아님)", "정보보안기사·산업기사, 정보통신기사·산업기사, 무선설비, 전파전자통신",
                        Mode.AUTO, "한국방송통신전파진흥원(KCA)",
                        "https://www.cq.or.kr/qh_quagm03_001.do",
                        "국가기술자격검정 시험일정 (기능장·기사·산업기사·기능사 표)",
                        "연 3~4회",
                        "스크래핑 구현·검증 완료(18건). ⚠️ 비고 칸에 분야를 안 밝힌 회차는 넣지 않는다 — 추측으로 붙이면 접수일이 틀린 채 서비스된다."),
                new Entry("IT·데이터", "빅데이터분석기사, ADP·ADsP, SQLP·SQLD, DAP·DAsP",
                        Mode.AUTO, "한국데이터산업진흥원",
                        "https://www.dataq.or.kr/www/accept/schedule.do",
                        "자격검정 → 시험일정 (연간 일정표 한 장에 7종이 다 있다)",
                        "종목별 연 2~4회",
                        "스크래핑 구현·검증 완료(7종 22건). ⚠️ 빅데이터분석기사는 국가기술자격인데 시행처가 큐넷이 아니라 여기서만 들어온다."),
                new Entry("사무·IT(생산성본부)", "ERP정보관리사(회계·인사·생산·물류), ITQ, GTQ 등 9종",
                        Mode.MANUAL, "한국생산성본부",
                        "https://license.kpc.or.kr/",
                        "자격시험 일정 (연간 일정표)",
                        "월 1회 내외",
                        "연간 일정이 한 번에 공고됩니다. 스크래퍼 후보 1순위(9종이 표 하나에)."),
                new Entry("금융(정기시험)", "금융투자분석사·재무위험관리사 등 6종 / 은행FP·여신심사역 등 6종 / 보험심사역",
                        Mode.MANUAL, "금융투자협회 / 한국금융연수원 / 보험연수원",
                        "https://license.kofia.or.kr/",
                        "자격시험 접수 → 시험일정",
                        "연 1~4회",
                        "세 기관 모두 연간 일정을 미리 공고합니다. 연초에 몰아 넣으면 편합니다."),
                new Entry("체육", "생활·전문·유소년·노인 스포츠지도사",
                        Mode.MANUAL, "국민체육진흥공단",
                        "https://sqms.kspo.or.kr/",
                        "체육지도자 자격검정 → 시험일정",
                        "연 1회",
                        "연 1회(필기 4~5월)라 한 번 넣으면 1년이 갑니다."),
                new Entry("한국어·한자", "TOPIK / 한자능력검정(어문회) / 대한검정회 한자",
                        Mode.MANUAL, "국립국제교육원 / 한국어문회 / 대한검정회",
                        "https://www.topik.go.kr/",
                        "시험일정 (TOPIK 은 연 6회 일괄 공고)",
                        "연 4~6회",
                        "셋 다 연간 일정을 미리 공고합니다. TOPIK 은 스크래퍼 후보."),
                new Entry("어학(FLEX)", "FLEX 영어·중국어·일본어·독일어 등 7종",
                        Mode.MANUAL, "한국외국어대학교",
                        "https://flex.hufs.ac.kr/",
                        "시험일정",
                        "연 4회", ""),
                new Entry("IT 벤더·국제자격(대상 아님)", "AWS, Azure, GCP, Cisco, Oracle, CompTIA, PMP, MOS",
                        Mode.EXCLUDED, "각 벤더 (피어슨뷰·PSI 시험센터)",
                        "https://www.pearsonvue.com/",
                        "—",
                        "상시 예약제",
                        "시험센터에서 원하는 날짜에 예약하는 방식이라 '접수 마감'이 없습니다. TOEFL·IELTS 와 같은 부류."),
                new Entry("운전·운송(대상 아님)", "운전면허 1·2종, 택시·버스·화물 운송자격",
                        Mode.EXCLUDED, "도로교통공단 / TS한국교통안전공단",
                        "https://www.safedriving.or.kr/",
                        "—",
                        "상시",
                        "상시 접수라 마감이 없습니다."),
                new Entry("공무원", "9급·7급 국가직/지방직, 경찰, 소방",
                        Mode.MANUAL, "인사혁신처 / 각 시도 / 경찰청 / 소방청",
                        "https://www.gosi.kr/",
                        "사이버국가고시센터 → 시험일정 (지방직은 각 시도 인재개발원 공고)",
                        "연 1~2회",
                        "연초에 한 해 일정이 한꺼번에 공고됩니다. 1~2월에 몰아서 넣으면 1년이 편합니다."),
                new Entry("보건·의료", "간호사, 물리치료사, 임상병리사, 방사선사, 영양사 등 16종",
                        Mode.AUTO, "한국보건의료인국가시험원(국시원)",
                        "https://www.kuksiwon.or.kr/",
                        "직종별 시험정보 → 시험일정 (직종마다 페이지가 따로다)",
                        "연 1회",
                        "스크래핑 구현·검증 완료(직종 페이지 15개 → 16종). ⚠️ 요양보호사는 상시(기간제) 컴퓨터시험이라 대상이 아니다 — 파서도 상시 문구를 만나면 아무것도 만들지 않는다."),
                new Entry("소방", "소방시설관리사, 소방안전관리자(특급·1·2·3급)",
                        Mode.MANUAL, "한국소방안전원",
                        "https://www.kfsi.or.kr/",
                        "교육/시험 → 자격시험 → 시험일정",
                        "연 3~6회", ""),
                new Entry("금융", "보험계리사, 손해사정사, 보험중개사",
                        Mode.MANUAL, "금융감독원",
                        "https://www.fss.or.kr/",
                        "업무자료 → 자격시험 공고",
                        "연 1~2회", ""),
                new Entry("한국사", "한국사능력검정시험(심화·기본)",
                        Mode.MANUAL, "국사편찬위원회",
                        "https://www.historyexam.go.kr/",
                        "시험일정 안내",
                        "연 6회",
                        "취준생 수요가 큽니다. 연간 일정이 미리 공고되니 한 번에 넣어 두세요."),
                new Entry("중국어", "HSK, HSKK",
                        Mode.MANUAL, "HSK한국사무국",
                        "https://www.hsk.or.kr/",
                        "시험일정",
                        "월 1회 내외",
                        "스크래퍼 예정(작업큐). 만들어지면 이 항목은 자동으로 바뀝니다."),
                new Entry("일본어", "JPT, SJPT",
                        Mode.MANUAL, "YBM",
                        "https://www.jpt.co.kr/",
                        "시험일정",
                        "월 1회", ""),
                new Entry("영어(기타)", "G-TELP, TOEIC Speaking/Writing",
                        Mode.MANUAL, "G-TELP KOREA / YBM",
                        "https://www.g-telp.co.kr/",
                        "시험일정",
                        "월 다회",
                        "G-TELP 는 스크래핑이 막혀 있어(봇 차단) 당분간 수기입니다."),
                new Entry("사무·회계", "컴퓨터활용능력, 워드프로세서, 전산회계",
                        Mode.MANUAL, "대한상공회의소 / 한국세무사회",
                        "https://license.korcham.net/",
                        "시험일정",
                        "상시·월 다회",
                        "상시시험이라 회차 개념이 약합니다. 정기시험 위주로만 넣습니다."),

                // ── 자동: 손대면 덮어써진다 ──
                new Entry("국가기술자격", "613종 (국가기술자격 513 + 국가전문자격 100)",
                        Mode.AUTO, "한국산업인력공단(큐넷)",
                        "https://www.q-net.or.kr/",
                        "공공데이터포털 API (자격 시험일정 조회 서비스)",
                        "연 3~4회",
                        "매일 05:00 종목별 613콜(일 한도 1,000 안). 일부 종목이 일시 오류로 빌 수 있는데, 그때는 '할 일'에서 빈 종목을 보고 재수집 API 로 채웁니다. ⚠️ 국가기술자격이라고 전부 여기 있는 건 아닙니다 — 위 '큐넷 아님' 항목을 보세요."),
                new Entry("영어", "TOEIC",
                        Mode.AUTO, "YBM",
                        "https://m.exam.toeic.co.kr/receipt/examSchList.php",
                        "모바일 접수일정 페이지 (PC 페이지는 에러가 납니다)",
                        "월 2~3회",
                        "스크래핑 검증 완료(12회차)."),
                new Entry("영어", "TEPS, TEPS-S&W",
                        Mode.AUTO, "서울대 TEPS관리위원회",
                        "https://www.teps.or.kr/",
                        "첫 화면의 일정 표 (일정 전용 페이지는 JS 라 못 읽습니다)",
                        "월 1~2회",
                        "스크래핑 검증 완료(3회차)."),
                new Entry("일본어", "JLPT",
                        Mode.AUTO, "국제교류기금·JEES",
                        "https://www.jlpt.or.kr/html/",
                        "첫 화면 공지",
                        "연 2회(7월·12월)",
                        "스크래핑 검증 완료(1회차)."),

                // ── 대상 아님 ──
                new Entry("영어", "TOEFL iBT, IELTS, OPIc",
                        Mode.EXCLUDED, "ETS / British Council·IDP / 크레듀",
                        "https://www.ets.org/toefl",
                        "—",
                        "상시 예약제",
                        "원하는 날짜에 예약하는 방식이라 '접수 마감일'이 없습니다. 알릴 것이 없어 넣지 않습니다.")
        );
    }
}
