package com.test.test.exam.notification;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.NotificationEventType;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.ScheduleStatus;
import com.test.test.exam.domain.Series;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>메일에 우리 내부 숫자가 새지 않는다.</b>
 *
 * <p>회차가 없는 시험(토익스피킹·토플 등)에도 같은 회차를 두 번 넣지 않으려고 회차 자리에
 * <b>날짜를 멱등 키로</b> 넣는다. 그건 시행처가 부르는 이름이 아니라 우리 내부 키다.
 * 화면은 2026-09-08 에 이걸 감추도록 고쳤다({@code ExamSchedule.roundLabel()}) —
 * 그런데 <b>알림 문안은 그 함수를 안 쓰고 회차를 직접 붙이고 있었다.</b>
 *
 * <p>그래서 화면에는 안 보이는 "20261011회"·"0회"가 <b>메일로는 그대로 나간다.</b>
 * 2026-09-22 실측: 앞으로 올 회차 610건 중 <b>70건(9종)</b>이 날짜를 회차로 달고 있었다 —
 * G-TELP·HSK·TOEIC Bridge 처럼 회차를 안 매기는 시험들이다.
 * 받는 사람은 우리가 뭔가 잘못 읽었다고 생각하고, 그 메일에 적힌 날짜도 같이 의심하게 된다.
 * 알림은 이 서비스가 사용자와 만나는 거의 유일한 접점이라 여기서 신뢰를 잃으면 회복할 자리가 없다.
 *
 * <p>컨트롤러를 안 거치는 순수 문안 생성이라 컨벤션 §6 의 예외로 둔다.
 */
class NotificationContentTest {

    private final NotificationContentFactory factory = new NotificationContentFactory();

    private ExamSchedule schedule(Integer round, ExamType type) {
        Certificate c = Certificate.builder()
                .name("TOEIC Speaking").slug("toeic-speaking")
                .series(Series.ETC).agency("YBM").build();
        LocalDate soon = TimeUtil.today().plusDays(30);
        return ExamSchedule.builder()
                .certificate(c).year(soon.getYear()).round(round).examType(type)
                .regStartAt(soon.minusDays(20).atTime(10, 0))
                .regEndAt(soon.minusDays(10).atTime(18, 0))
                .examStartDate(soon).examEndDate(soon)
                .provenance(ScheduleProvenance.API).status(ScheduleStatus.ACTIVE)
                .build();
    }

    /** 기본은 구분을 붙이는 쪽 — 필기·실기를 둘 다 치르는 시험을 가정한다. */
    private String body(Integer round, ExamType type, NotificationEventType event) {
        return body(round, type, event, true);
    }

    private String body(Integer round, ExamType type, NotificationEventType event, boolean withExamType) {
        return factory.build(new NotificationContentFactory.NotificationSchedule_Ref(
                schedule(round, type), event, withExamType)).getBody();
    }

    /** 날짜를 회차 자리에 넣은 멱등 키 — 시행처가 그렇게 부르지 않는다. */
    @Test
    @DisplayName("날짜를 회차 자리에 쓴 시험은 그 숫자를 메일에 쓰지 않는다")
    void date_keyed_round_never_reaches_the_mail() {
        String b = body(20261011, ExamType.WRITTEN, NotificationEventType.REG_CLOSE_EVE);

        assertThat(b).doesNotContain("20261011");
        assertThat(b).contains("접수 마감");
    }

    /** 큐넷 기능사 실기처럼 회차 번호가 공고되지 않은 회차는 round=0 으로 들어온다(지난 회차 92건). */
    @Test
    @DisplayName("회차 번호가 없는 회차를 0회라고 부르지 않는다")
    void zero_round_is_not_called_a_round() {
        String b = body(0, ExamType.PRACTICAL, NotificationEventType.EXAM_D7);

        assertThat(b).doesNotContain("0회");
        assertThat(b).contains("시험");
    }

    @Test
    @DisplayName("진짜 회차는 그대로 쓴다 — 감추는 건 우리가 만든 숫자뿐이다")
    void real_round_is_kept() {
        String b = body(3, ExamType.WRITTEN, NotificationEventType.EXAM_D1);

        assertThat(b).contains("3회");
    }

    /** 구분이 비어 있는 행이 들어와도 발송 배치가 죽으면 안 된다 — 한 건이 나머지를 막는다. */
    @Test
    @DisplayName("구분이 비어 있어도 문안을 만든다")
    void missing_exam_type_does_not_break_the_batch() {
        String b = body(3, null, NotificationEventType.EXAM_D1);

        assertThat(b).contains("3회");
    }

    /** 취소는 "업데이트됐어요"로 뭉개면 안 된다 — 그대로 시험장에 간다. */
    @Test
    @DisplayName("취소된 회차는 취소라고 말한다")
    void canceled_says_canceled() {
        ExamSchedule s = schedule(3, ExamType.WRITTEN);
        s.changeStatus(ScheduleStatus.CANCELED);

        String b = factory.build(new NotificationContentFactory.NotificationSchedule_Ref(
                s, NotificationEventType.SCHEDULE_CHANGED, true)).getBody();

        assertThat(b).contains("취소");
    }

    /**
     * 실기가 없는 시험에 "필기"를 붙이면 그냥 틀린 말이다. 2026-09-22 전수 실측으로 206종이 그랬다.
     * 메일은 사용자와 만나는 거의 유일한 접점이라, 여기 적힌 말이 사실이 아니면 날짜까지 의심받는다.
     */
    @Test
    @DisplayName("한 종류만 치르는 시험 메일에는 필기라고 쓰지 않는다")
    void single_type_exam_is_not_called_written() {
        String b = body(3, ExamType.WRITTEN, NotificationEventType.REG_CLOSE_EVE, false);

        assertThat(b).doesNotContain("필기");
        assertThat(b).contains("3회");
        assertThat(b).contains("접수 마감");
    }

    @Test
    @DisplayName("둘 다 치르는 시험은 그대로 구분해 쓴다 — 접수일이 서로 다르다")
    void split_exam_keeps_the_label() {
        assertThat(body(3, ExamType.PRACTICAL, NotificationEventType.REG_CLOSE_EVE, true))
                .contains("3회 실기");
    }

    /** 회차도 구분도 없는 시험은 남길 말이 없다 — 앞 공백으로 시작하면 안 된다. */
    @Test
    @DisplayName("쓸 말이 없으면 공백으로 시작하지 않는다")
    void nothing_to_prefix_leaves_no_gap() {
        String b = body(20261011, ExamType.WRITTEN, NotificationEventType.REG_CLOSE_EVE, false);

        assertThat(b).startsWith("접수 마감");
    }
}
