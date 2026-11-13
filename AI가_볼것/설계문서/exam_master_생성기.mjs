// 시험 마스터 시드 생성기 — 09_시험_전수목록.md 를 기계가 읽는 형태로 옮긴다.
// 출력: src/main/resources/seed/exam_master.json
import { writeFileSync } from 'node:fs';

const QNET = { agency: '한국산업인력공단', url: 'https://www.q-net.or.kr/' };

/** [카테고리, 계열, 시행처, URL, 종목명...] */
const GROUPS = [
  // ── 국가기술자격 ────────────────────────────────────────────
  ['국가기술자격-정보통신', 'TECHNICIAN', QNET, [
    '정보처리기사', '정보보안기사', '빅데이터분석기사', '전자계산기기사', '전자계산기조직응용기사',
    '정보통신기사', '무선설비기사', '방송통신기사', '전파전자통신기사',
  ]],
  ['국가기술자격-정보통신', 'INDUSTRIAL', QNET, [
    '정보처리산업기사', '정보보안산업기사', '사무자동화산업기사', '정보통신산업기사',
    '무선설비산업기사', '방송통신산업기사', '전파전자통신산업기사',
  ]],
  ['국가기술자격-정보통신', 'CRAFTSMAN', QNET, [
    '정보처리기능사', '정보기기운용기능사', '전자계산기기능사', '방송통신기능사',
  ]],
  ['국가기술자격-정보통신', 'PROFESSIONAL', QNET, [
    '정보관리기술사', '컴퓨터시스템응용기술사', '정보통신기술사',
  ]],

  ['국가기술자격-경영사무', 'SERVICE', QNET, [
    '컴퓨터활용능력 1급', '컴퓨터활용능력 2급', '워드프로세서',
    '전산회계운용사 1급', '전산회계운용사 2급', '전산회계운용사 3급',
    '사회조사분석사 1급', '사회조사분석사 2급',
    '소비자전문상담사 1급', '소비자전문상담사 2급',
    '비서 1급', '비서 2급', '비서 3급',
    '한글속기 1급', '한글속기 2급', '한글속기 3급',
    '전자상거래관리사 1급', '전자상거래관리사 2급', '전자상거래운용사',
    '유통관리사 1급', '유통관리사 2급', '유통관리사 3급',
    '텔레마케팅관리사', '컨벤션기획사 1급', '컨벤션기획사 2급',
    '직업상담사 1급', '직업상담사 2급', '스포츠경영관리사',
  ]],
  ['국가기술자격-경영사무', 'TECHNICIAN', QNET, [
    '품질경영기사', '포장기사',
  ]],
  ['국가기술자격-경영사무', 'INDUSTRIAL', QNET, [
    '품질경영산업기사', '포장산업기사',
  ]],

  ['국가기술자격-전기전자', 'TECHNICIAN', QNET, [
    '전기기사', '전기공사기사', '전자기사', '전기철도기사', '철도신호기사', '승강기기사',
  ]],
  ['국가기술자격-전기전자', 'INDUSTRIAL', QNET, [
    '전기산업기사', '전기공사산업기사', '전자산업기사', '전기철도산업기사',
    '철도신호산업기사', '승강기산업기사',
  ]],
  ['국가기술자격-전기전자', 'CRAFTSMAN', QNET, [
    '전기기능사', '전자기능사', '전자캐드기능사', '승강기기능사', '철도전기신호기능사',
  ]],
  ['국가기술자격-전기전자', 'MASTER', QNET, ['전기기능장', '전자기기기능장']],
  ['국가기술자격-전기전자', 'PROFESSIONAL', QNET, ['건축전기설비기술사', '발송배전기술사', '전기응용기술사']],

  ['국가기술자격-기계', 'TECHNICIAN', QNET, [
    '일반기계기사', '건설기계설비기사', '공조냉동기계기사', '에너지관리기사',
    '자동차정비기사', '설비보전기사', '메카트로닉스기사', '항공기사', '조선기사', '철도차량기사',
  ]],
  ['국가기술자격-기계', 'INDUSTRIAL', QNET, [
    '기계설계산업기사', '건설기계설비산업기사', '공조냉동기계산업기사', '에너지관리산업기사',
    '자동차정비산업기사', '생산자동화산업기사', '컴퓨터응용가공산업기사', '항공산업기사', '조선산업기사',
  ]],
  ['국가기술자격-기계', 'CRAFTSMAN', QNET, [
    '공조냉동기계기능사', '자동차정비기능사', '전산응용기계제도기능사', '컴퓨터응용선반기능사',
    '컴퓨터응용밀링기능사', '기계가공조립기능사', '에너지관리기능사', '항공기체정비기능사',
  ]],
  ['국가기술자격-기계', 'MASTER', QNET, ['기계가공기능장', '자동차정비기능장', '에너지관리기능장']],

  ['국가기술자격-건설', 'TECHNICIAN', QNET, [
    '건축기사', '토목기사', '실내건축기사', '건설재료시험기사', '조경기사',
    '측량 및 지형공간정보기사', '지적기사', '도시계획기사', '교통기사', '철도토목기사',
  ]],
  ['국가기술자격-건설', 'INDUSTRIAL', QNET, [
    '건축산업기사', '토목산업기사', '실내건축산업기사', '건설재료시험산업기사', '조경산업기사',
    '측량 및 지형공간정보산업기사', '지적산업기사', '철도토목산업기사',
  ]],
  ['국가기술자격-건설', 'CRAFTSMAN', QNET, [
    '전산응용건축제도기능사', '전산응용토목제도기능사', '조경기능사', '지적기능사',
    '거푸집기능사', '철근기능사', '미장기능사', '타일기능사', '방수기능사', '건축도장기능사',
    '실내건축기능사', '측량기능사',
  ]],
  ['국가기술자격-건설', 'PROFESSIONAL', QNET, ['건축시공기술사', '토목시공기술사', '건축구조기술사', '토질및기초기술사']],

  ['국가기술자격-안전', 'TECHNICIAN', QNET, [
    '산업안전기사', '건설안전기사', '인간공학기사', '소방설비기사(기계분야)', '소방설비기사(전기분야)',
    '가스기사', '화재감식평가기사', '방재기사',
  ]],
  ['국가기술자격-안전', 'INDUSTRIAL', QNET, [
    '산업안전산업기사', '건설안전산업기사', '소방설비산업기사(기계분야)', '소방설비산업기사(전기분야)',
    '가스산업기사', '위험물산업기사', '산업위생관리산업기사',
  ]],
  ['국가기술자격-안전', 'CRAFTSMAN', QNET, ['위험물기능사', '가스기능사', '승강기기능사']],
  ['국가기술자격-안전', 'PROFESSIONAL', QNET, ['산업안전지도사', '건설안전기술사', '소방기술사', '가스기술사']],

  ['국가기술자격-화학환경', 'TECHNICIAN', QNET, [
    '화공기사', '화학분석기사', '대기환경기사', '수질환경기사', '폐기물처리기사',
    '소음진동기사', '토양환경기사', '자연생태복원기사', '온실가스관리기사',
    '신재생에너지발전설비기사(태양광)',
  ]],
  ['국가기술자격-화학환경', 'INDUSTRIAL', QNET, [
    '화공산업기사', '대기환경산업기사', '수질환경산업기사', '폐기물처리산업기사',
    '소음진동산업기사', '자연생태복원산업기사',
  ]],
  ['국가기술자격-화학환경', 'CRAFTSMAN', QNET, ['화학분석기능사', '환경기능사']],

  ['국가기술자격-재료', 'TECHNICIAN', QNET, [
    '금속재료기사', '표면처리기사', '용접기사', '세라믹기사', '재료조직평가산업기사',
  ]],
  ['국가기술자격-재료', 'INDUSTRIAL', QNET, ['금속재료산업기사', '표면처리산업기사', '용접산업기사']],
  ['국가기술자격-재료', 'CRAFTSMAN', QNET, [
    '용접기능사', '특수용접기능사', '주조기능사', '열처리기능사', '금속재료시험기능사',
  ]],
  ['국가기술자격-재료', 'MASTER', QNET, ['용접기능장', '금속재료기능장']],

  ['국가기술자격-식품조리', 'TECHNICIAN', QNET, ['식품기사', '수산제조기사']],
  ['국가기술자격-식품조리', 'INDUSTRIAL', QNET, ['식품산업기사']],
  ['국가기술자격-식품조리', 'CRAFTSMAN', QNET, [
    '한식조리기능사', '양식조리기능사', '중식조리기능사', '일식조리기능사', '복어조리기능사',
    '제과기능사', '제빵기능사', '조주기능사', '식품가공기능사', '떡제조기능사',
  ]],
  ['국가기술자격-식품조리', 'MASTER', QNET, ['조리기능장', '제과기능장']],

  ['국가기술자격-운전운송', 'CRAFTSMAN', QNET, [
    '지게차운전기능사', '굴착기운전기능사', '기중기운전기능사', '로더운전기능사',
    '불도저운전기능사', '천장크레인운전기능사', '컨테이너크레인운전기능사',
  ]],

  ['국가기술자격-디자인', 'TECHNICIAN', QNET, ['시각디자인기사', '제품디자인기사', '컬러리스트기사']],
  ['국가기술자격-디자인', 'INDUSTRIAL', QNET, ['시각디자인산업기사', '제품디자인산업기사', '컬러리스트산업기사']],
  ['국가기술자격-디자인', 'CRAFTSMAN', QNET, [
    '컴퓨터그래픽스운용기능사', '웹디자인기능사', '제품응용모델링기능사', '사진기능사', '영사기능사',
  ]],

  ['국가기술자격-농림어업', 'TECHNICIAN', QNET, [
    '산림기사', '식물보호기사', '유기농업기사', '종자기사', '수산양식기사', '조경기사',
  ]],
  ['국가기술자격-농림어업', 'INDUSTRIAL', QNET, ['산림산업기사', '식물보호산업기사', '유기농업산업기사', '종자산업기사']],
  ['국가기술자격-농림어업', 'CRAFTSMAN', QNET, [
    '유기농업기능사', '종자기능사', '원예기능사', '화훼장식기능사', '버섯종균기능사', '임업종묘기능사',
  ]],

  ['국가기술자격-미용', 'CRAFTSMAN', QNET, [
    '미용사(일반)', '미용사(피부)', '미용사(네일)', '미용사(메이크업)', '이용사',
  ]],
  ['국가기술자격-미용', 'INDUSTRIAL', QNET, ['미용장']],

  ['국가기술자격-섬유공예', 'INDUSTRIAL', QNET, ['패션디자인산업기사', '패션머천다이징산업기사', '섬유산업기사']],
  ['국가기술자격-섬유공예', 'CRAFTSMAN', QNET, [
    '양복기능사', '한복기능사', '양장기능사', '가구제작기능사', '목공예기능사',
    '도자기공예기능사', '귀금속가공기능사', '인쇄기능사', '자수기능사',
  ]],

  // ── 국가전문자격 ────────────────────────────────────────────
  ['국가전문자격', 'ETC', QNET, [
    '공인중개사', '주택관리사보', '감정평가사', '공인노무사', '관세사', '세무사', '변리사',
    '물류관리사', '경영지도사', '기술지도사', '손해평가사', '보세사', '행정사', '경비지도사',
    '청소년상담사', '청소년지도사', '국제의료관광코디네이터', '임상심리사 1급', '임상심리사 2급',
    '산업보건지도사', '관광통역안내사', '국내여행안내사', '호텔경영사', '호텔관리사', '호텔서비스사',
  ]],
  ['국가전문자격', 'ETC', { agency: '금융감독원', url: 'https://www.fss.or.kr/' }, [
    '공인회계사', '보험계리사', '손해사정사', '보험중개사',
  ]],
  ['국가전문자격', 'ETC', { agency: '한국소방안전원', url: 'https://www.kfsi.or.kr/' }, [
    '소방시설관리사', '소방안전관리자 특급', '소방안전관리자 1급',
    '소방안전관리자 2급', '소방안전관리자 3급',
  ]],
  ['국가전문자격', 'ETC', { agency: '국립국어원', url: 'https://kteacher.korean.go.kr/' }, ['한국어교원 2급', '한국어교원 3급']],
  ['국가전문자격', 'ETC', { agency: 'TS한국교통안전공단', url: 'https://lic.kotsa.or.kr/' }, [
    '버스운전자격', '화물운송종사자격', '택시운전자격',
  ]],
  ['국가전문자격', 'ETC', { agency: '도로교통공단', url: 'https://www.safedriving.or.kr/' }, [
    '제1종 운전면허', '제2종 운전면허',
  ]],

  // ── 보건·의료 (국시원) ──────────────────────────────────────
  ['보건의료', 'ETC', { agency: '한국보건의료인국가시험원', url: 'https://www.kuksiwon.or.kr/' }, [
    '간호사', '임상병리사', '방사선사', '물리치료사', '작업치료사', '치과위생사',
    '치과기공사', '응급구조사 1급', '응급구조사 2급', '영양사', '위생사', '보건교육사',
    '안경사', '의무기록사', '약사', '한약사', '요양보호사',
  ]],
  ['보건의료', 'ETC', { agency: '한국사회복지사협회', url: 'https://www.welfare.net/' }, [
    '사회복지사 1급', '보육교사', '정신건강사회복지사',
  ]],

  // ── 어학 ────────────────────────────────────────────────────
  ['어학-영어', 'ETC', { agency: 'YBM', url: 'https://exam.toeic.co.kr/' }, [
    'TOEIC 토익', 'TOEIC Speaking 토익스피킹', 'TOEIC Writing 토익라이팅', 'TOEIC Bridge',
  ]],
  ['어학-영어', 'ETC', { agency: '서울대학교 TEPS관리위원회', url: 'https://www.teps.or.kr/' }, [
    'TEPS 텝스', 'TEPS Speaking', 'TEPS Writing',
  ]],
  ['어학-영어', 'ETC', { agency: '크레듀(ACTFL)', url: 'https://www.opic.or.kr/' }, ['OPIc 오픽']],
  ['어학-영어', 'ETC', { agency: 'G-TELP KOREA', url: 'https://www.g-telp.co.kr/' }, [
    'G-TELP 지텔프(Level 2)', 'G-TELP Speaking',
  ]],
  ['어학-영어', 'ETC', { agency: 'ETS', url: 'https://www.ets.org/toefl' }, ['TOEFL iBT 토플']],
  ['어학-영어', 'ETC', { agency: 'British Council / IDP', url: 'https://www.britishcouncil.kr/' }, ['IELTS 아이엘츠']],
  ['어학-영어', 'ETC', { agency: '한국외국어대학교', url: 'https://flex.hufs.ac.kr/' }, ['FLEX 영어']],

  ['어학-일본어', 'ETC', { agency: 'JEES / 국제교류기금', url: 'https://www.jlpt.or.kr/' }, ['JLPT 일본어능력시험']],
  ['어학-일본어', 'ETC', { agency: 'YBM', url: 'https://www.jpt.co.kr/' }, ['JPT 일본어능력시험', 'SJPT 일본어 말하기시험']],
  ['어학-일본어', 'ETC', { agency: 'JEES', url: 'https://www.jlpt.or.kr/' }, ['BJT 비즈니스일본어']],
  ['어학-일본어', 'ETC', { agency: '한국외국어대학교', url: 'https://flex.hufs.ac.kr/' }, ['FLEX 일본어']],

  ['어학-중국어', 'ETC', { agency: 'HSK한국사무국', url: 'https://www.hsk.or.kr/' }, [
    'HSK 중국어능력시험(필기)', 'HSKK 중국어 회화시험',
  ]],
  ['어학-중국어', 'ETC', { agency: 'YBM', url: 'https://www.ybmtsc.co.kr/' }, ['TSC 중국어 말하기시험', 'BCT 비즈니스중국어']],
  ['어학-중국어', 'ETC', { agency: '한국외국어대학교', url: 'https://flex.hufs.ac.kr/' }, ['FLEX 중국어']],

  ['어학-기타', 'ETC', { agency: '인스티투토 세르반테스', url: 'https://seul.cervantes.es/' }, ['DELE 스페인어']],
  ['어학-기타', 'ETC', { agency: '주한프랑스문화원', url: 'https://www.delfdalf.kr/' }, ['DELF 프랑스어', 'DALF 프랑스어']],
  ['어학-기타', 'ETC', { agency: '주한독일문화원(괴테)', url: 'https://www.goethe.de/korea' }, [
    'Goethe-Zertifikat 독일어', 'TestDaF 독일어',
  ]],
  ['어학-기타', 'ETC', { agency: '한국외국어대학교', url: 'https://flex.hufs.ac.kr/' }, [
    'FLEX 스페인어', 'FLEX 프랑스어', 'FLEX 독일어', 'FLEX 러시아어',
  ]],

  ['한국어', 'ETC', { agency: '국립국제교육원', url: 'https://www.topik.go.kr/' }, ['TOPIK 한국어능력시험(I)', 'TOPIK 한국어능력시험(II)']],
  ['국어', 'ETC', { agency: 'KBS한국방송', url: 'https://www.klt.or.kr/' }, ['KBS한국어능력시험']],
  ['국어', 'ETC', { agency: '한국언어문화연구원', url: 'https://www.tokl.or.kr/' }, ['ToKL 국어능력인증시험']],
  ['국어', 'ETC', { agency: '한국국어능력평가협회', url: 'https://www.klata.or.kr/' }, ['한국실용글쓰기검정']],

  // ── IT·사무 민간/공인 ───────────────────────────────────────
  ['IT-데이터', 'ETC', { agency: '한국데이터산업진흥원', url: 'https://www.dataq.or.kr/' }, [
    'ADsP 데이터분석 준전문가', 'ADP 데이터분석 전문가', 'SQLD SQL 개발자', 'SQLP SQL 전문가',
    'DAsP 데이터아키텍처 준전문가', 'DAP 데이터아키텍처 전문가',
  ]],
  ['IT-보안', 'ETC', { agency: '한국인터넷진흥원(KISA)', url: 'https://isms.kisa.or.kr/' }, ['ISMS-P 인증심사원']],
  ['IT-보안', 'ETC', { agency: '한국방송통신전파진흥원(KCA)', url: 'https://www.cq.or.kr/' }, ['디지털포렌식전문가 2급']],
  ['IT-보안', 'ETC', { agency: '한국CPO포럼', url: 'https://www.cpptg.or.kr/' }, ['CPPG 개인정보관리사']],
  ['IT-보안', 'ETC', { agency: '한국정보통신자격협회(ICQA)', url: 'https://www.icqa.or.kr/' }, [
    '네트워크관리사 1급', '네트워크관리사 2급', 'PC정비사 1급', 'PC정비사 2급',
  ]],
  ['IT-보안', 'ETC', { agency: '한국정보통신인력개발센터', url: 'https://www.ihd.or.kr/' }, [
    '리눅스마스터 1급', '리눅스마스터 2급',
  ]],

  ['사무-IT', 'ETC', { agency: '한국생산성본부', url: 'https://license.kpc.or.kr/' }, [
    'ITQ 정보기술자격(한글)', 'ITQ 정보기술자격(엑셀)', 'ITQ 정보기술자격(파워포인트)',
    'ITQ 정보기술자격(액세스)', 'ITQ 정보기술자격(인터넷)',
    'GTQ 그래픽기술자격 1급', 'GTQ 그래픽기술자격 2급', 'GTQi 일러스트 1급',
    'ERP정보관리사 회계', 'ERP정보관리사 인사', 'ERP정보관리사 물류', 'ERP정보관리사 생산',
  ]],
  ['사무-IT', 'ETC', { agency: 'Microsoft·YBM', url: 'https://mos.co.kr/' }, [
    'MOS Word', 'MOS Excel', 'MOS PowerPoint', 'MOS Master',
  ]],
  ['사무-IT', 'ETC', { agency: '한국정보통신진흥협회(KAIT)', url: 'https://www.ihd.or.kr/' }, [
    'DIAT 디지털정보활용능력', '인터넷정보관리사',
  ]],

  ['IT-벤더', 'ETC', { agency: 'Amazon Web Services', url: 'https://aws.amazon.com/certification/' }, [
    'AWS Certified Solutions Architect – Associate', 'AWS Certified Developer – Associate',
    'AWS Certified Cloud Practitioner',
  ]],
  ['IT-벤더', 'ETC', { agency: 'Microsoft', url: 'https://learn.microsoft.com/credentials/' }, [
    'Microsoft Azure Fundamentals (AZ-900)', 'Microsoft Azure Administrator (AZ-104)',
  ]],
  ['IT-벤더', 'ETC', { agency: 'Cisco', url: 'https://www.cisco.com/' }, ['CCNA', 'CCNP']],
  ['IT-벤더', 'ETC', { agency: 'Oracle', url: 'https://education.oracle.com/' }, ['OCA', 'OCP']],
  ['IT-벤더', 'ETC', { agency: 'Google Cloud', url: 'https://cloud.google.com/certification' }, [
    'Google Cloud Associate Cloud Engineer',
  ]],
  ['IT-벤더', 'ETC', { agency: 'CompTIA', url: 'https://www.comptia.org/' }, [
    'CompTIA A+', 'CompTIA Network+', 'CompTIA Security+',
  ]],
  ['IT-벤더', 'ETC', { agency: 'PMI', url: 'https://www.pmi.org/' }, ['PMP 프로젝트관리전문가']],

  // ── 금융·회계·경제 ──────────────────────────────────────────
  ['금융', 'ETC', { agency: '금융투자협회', url: 'https://license.kofia.or.kr/' }, [
    '투자자산운용사', '펀드투자권유대행인', '증권투자권유대행인', '파생상품투자권유대행인',
    '펀드투자권유자문인력', '증권투자권유자문인력', '파생상품투자권유자문인력',
    '금융투자분석사', '재무위험관리사',
  ]],
  ['금융', 'ETC', { agency: '한국금융연수원', url: 'https://www.kbi.or.kr/' }, [
    '신용분석사', '여신심사역', '자산관리사(FP)', '국제금융역', '외환전문역 1종', '외환전문역 2종',
    '은행텔러', '영업점컴플라이언스오피서',
  ]],
  ['금융', 'ETC', { agency: '한국FPSB', url: 'https://www.fpsbkorea.org/' }, ['AFPK 재무설계사', 'CFP 국제재무설계사']],
  ['금융', 'ETC', { agency: '보험연수원', url: 'https://www.in.or.kr/' }, ['보험심사역(AIU)', '보험심사역(CIU)']],
  ['금융', 'ETC', { agency: '한국신용정보협회', url: 'https://www.cretop.or.kr/' }, ['신용관리사']],

  ['회계-세무', 'ETC', { agency: '한국세무사회', url: 'https://license.kacpta.or.kr/' }, [
    '전산세무 1급', '전산세무 2급', '전산회계 1급', '전산회계 2급',
  ]],
  ['회계-세무', 'ETC', { agency: '한국공인회계사회', url: 'https://at.kicpa.or.kr/' }, [
    'FAT 회계실무 1급', 'FAT 회계실무 2급', 'TAT 세무실무 1급', 'TAT 세무실무 2급',
  ]],
  ['회계-세무', 'ETC', { agency: '삼일회계법인', url: 'https://www.samilexam.com/' }, [
    '재경관리사', '회계관리 1급', '회계관리 2급',
  ]],

  ['경제-경영', 'ETC', { agency: '한국경제신문', url: 'https://www.tesat.or.kr/' }, ['TESAT 경제이해력검증시험']],
  ['경제-경영', 'ETC', { agency: '매일경제신문', url: 'https://exam.mk.co.kr/' }, ['매경TEST 경제경영이해력시험']],

  // ── 유통·무역 ───────────────────────────────────────────────
  ['유통-무역', 'ETC', { agency: '대한상공회의소', url: 'https://license.korcham.net/' }, [
    '무역영어 1급', '무역영어 2급', '무역영어 3급', '상공회의소한자 1급', '상공회의소한자 2급',
  ]],
  ['유통-무역', 'ETC', { agency: '한국무역협회', url: 'https://www.tradecampus.com/' }, ['국제무역사 1급', '외환관리사']],

  // ── 한국사·한자 ─────────────────────────────────────────────
  ['한국사', 'ETC', { agency: '국사편찬위원회', url: 'https://www.historyexam.go.kr/' }, [
    '한국사능력검정시험(심화)', '한국사능력검정시험(기본)',
  ]],
  ['한자', 'ETC', { agency: '한국어문회', url: 'https://www.hanja.re.kr/' }, [
    '한자능력검정시험(1급)', '한자능력검정시험(2급)', '한자능력검정시험(3급)', '한자능력검정시험(4급)',
  ]],
  ['한자', 'ETC', { agency: '대한검정회', url: 'https://www.hanja.ne.kr/' }, ['대한검정회 한자 준1급', '대한검정회 한자 2급']],

  // ── 공무원 ──────────────────────────────────────────────────
  ['공무원', 'ETC', { agency: '인사혁신처', url: 'https://www.gosi.kr/' }, [
    '국가직 9급 공개경쟁채용', '국가직 7급 공개경쟁채용', '국가직 5급 공개경쟁채용',
  ]],
  ['공무원', 'ETC', { agency: '각 시·도 지방자치단체', url: 'https://local.gosi.go.kr/' }, [
    '지방직 9급 공개경쟁채용', '지방직 7급 공개경쟁채용',
  ]],
  ['공무원', 'ETC', { agency: '경찰청', url: 'https://www.police.go.kr/' }, ['경찰공무원 순경 공개채용', '경찰간부후보생']],
  ['공무원', 'ETC', { agency: '소방청', url: 'https://www.nfa.go.kr/' }, ['소방공무원 공개채용', '소방간부후보생']],
  ['공무원', 'ETC', { agency: '국방부', url: 'https://recruit.mnd.go.kr/' }, ['군무원 공개채용']],
  ['공무원', 'ETC', { agency: '법원행정처', url: 'https://exam.scourt.go.kr/' }, ['법원직 9급 공개채용']],
  ['공무원', 'ETC', { agency: '국회사무처', url: 'https://gosi.assembly.go.kr/' }, ['국회직 8급 공개채용', '국회직 9급 공개채용']],
  ['공무원', 'ETC', { agency: '각 시·도 교육청', url: 'https://www.gosi.kr/' }, [
    '교육행정직 9급', '중등교사 임용시험', '초등교사 임용시험',
  ]],

  // ── 서비스·기타 ─────────────────────────────────────────────
  ['서비스-기타', 'ETC', { agency: '국민체육진흥공단', url: 'https://sqms.kspo.or.kr/' }, [
    '생활스포츠지도사 2급', '전문스포츠지도사 2급', '유소년스포츠지도사', '노인스포츠지도사',
  ]],
  ['서비스-기타', 'ETC', { agency: '한국바리스타자격검정원', url: 'https://www.kbca.or.kr/' }, ['바리스타 1급', '바리스타 2급']],
];

const exams = [];
const seenName = new Set();
const seenCode = new Set();

function toCode(name) {
  // sourceCode 는 unique 여야 한다. 한글은 코드로 못 쓰므로 순번 기반으로 만든다.
  let base = 'M' + String(exams.length + 1).padStart(4, '0');
  while (seenCode.has(base)) base += 'X';
  seenCode.add(base);
  return base;
}

for (const [category, series, org, names] of GROUPS) {
  for (const name of names) {
    const key = name.replace(/\s+/g, '');
    if (seenName.has(key)) continue; // 같은 종목이 두 갈래에 겹쳐 적힌 경우
    seenName.add(key);
    exams.push({
      name,
      series,
      agency: org.agency,
      category,
      sourceCode: toCode(name),
      sourceUrl: org.url,
    });
  }
}

const out = {
  _comment:
    '시험 마스터 시드 — "어떤 시험이 존재하는가"만 담는다. 일정(ExamSchedule)은 여기 없다. ' +
    '일정이 없어도 검색·목록·상세·관심등록이 되어야 하므로 마스터를 먼저 적재한다. ' +
    '일정은 큐넷 API/스크래퍼/수기입력이 나중에 채운다. 출처 목록: 설계/설계문서/09_시험_전수목록.md',
  _prod: 'CertificateMasterInitializer 가 @Profile("!prod") 로 읽는다. 운영은 실 수집 소스가 대체한다.',
  generatedAt: '2026-08-06',
  count: exams.length,
  exams,
};

const path = 'c:/Users/gks93/workspace/simple_side/exam_hub/src/main/resources/seed/exam_master.json';
writeFileSync(path, JSON.stringify(out, null, 2) + '\n', 'utf8');

const byCat = {};
for (const e of exams) byCat[e.category] = (byCat[e.category] || 0) + 1;
console.log('총 종목:', exams.length);
console.log('분류 수:', Object.keys(byCat).length);
console.log(Object.entries(byCat).sort((a, b) => b[1] - a[1]).map(([k, v]) => `${k} ${v}`).join(' / '));
