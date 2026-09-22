package com.test.test.integration;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.Member;
import com.test.test.exam.domain.NotificationChannel;
import com.test.test.exam.domain.NotificationEventType;
import com.test.test.exam.domain.NotificationLog;
import com.test.test.exam.domain.NotificationResult;
import com.test.test.exam.domain.NotificationSchedule;
import com.test.test.exam.domain.NotificationScheduleStatus;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.ScheduleStatus;
import com.test.test.exam.domain.Series;
import com.test.test.exam.repository.CertificateRepository;
import com.test.test.exam.repository.ExamScheduleRepository;
import com.test.test.exam.repository.NotificationLogRepository;
import com.test.test.exam.repository.NotificationScheduleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <b>나한테 뭘 보냈다는 건지 볼 수 있어야 한다.</b>
 *
 * <p>{@code notification_log} 에 발송 기록이 쌓이지만 그건 <b>매니저 집계에만</b> 쓰였다.
 * 사용자는 "왔다는데 나는 못 받았다"를 확인할 길이 없었다. 확인 메일 버튼과 짝이 되는 쪽이다 —
 * 그 버튼은 "지금 보내면 오나"를 답하고, 이 화면은 "그동안 뭘 보냈나"를 답한다.
 *
 * <p>둘이 어긋나면(보냈다는데 받은 게 없으면) 사용자가 <b>접수를 놓치기 전에</b> 알아챈다.
 *
 * <p>내 것만 보인다 — 남의 발송 기록은 어떤 경우에도 새면 안 된다.
 */
class NotificationHistoryTest extends ApiIntegrationTestSupport {

    private static final String URL = "/api/me/notifications";

    @Autowired CertificateRepository certificateRepository;
    @Autowired ExamScheduleRepository examScheduleRepository;
    @Autowired NotificationScheduleRepository notificationScheduleRepository;
    @Autowired NotificationLogRepository notificationLogRepository;

    /** 발송 한 건을 실제로 만들어 둔다 — 화면이 읽는 것과 같은 경로다. */
    private NotificationLog logFor(Member member, NotificationChannel channel, NotificationResult result) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Certificate cert = certificateRepository.save(Certificate.builder()
                .name("정보처리기사" + unique).slug("정보처리기사" + unique)
                .series(Series.ETC).agency("한국산업인력공단").build());
        LocalDate exam = TimeUtil.today().plusDays(10);
        ExamSchedule schedule = examScheduleRepository.save(ExamSchedule.builder()
                .certificate(cert).year(exam.getYear()).round(3).examType(ExamType.WRITTEN)
                .regStartAt(TimeUtil.now().minusDays(5)).regEndAt(TimeUtil.now().plusDays(2))
                .examStartDate(exam).examEndDate(exam)
                .provenance(ScheduleProvenance.API).status(ScheduleStatus.ACTIVE).build());
        NotificationSchedule ns = notificationScheduleRepository.save(NotificationSchedule.builder()
                .examSchedule(schedule).eventType(NotificationEventType.REG_CLOSE_EVE)
                .sendAt(TimeUtil.now().minusHours(1)).status(NotificationScheduleStatus.SENT).build());
        return notificationLogRepository.save(NotificationLog.builder()
                .member(member).notificationSchedule(ns)
                .channel(channel).sentAt(TimeUtil.now().minusMinutes(30))
                .result(result).build());
    }

    @Test
    @DisplayName("비로그인은 볼 수 없다")
    void anonymous_cannot_read() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("아직 받은 게 없으면 빈 목록이다 — 오류가 아니다")
    void empty_is_not_an_error() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", bearer(newMember())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("언제 어떤 시험으로 무엇을 보냈는지 보여준다")
    void shows_what_was_sent() throws Exception {
        Member me = newMember();
        logFor(me, NotificationChannel.EMAIL, NotificationResult.SUCCESS);

        mockMvc.perform(get(URL).header("Authorization", bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].certificateName").isNotEmpty())
                .andExpect(jsonPath("$.items[0].sentAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].eventLabel").isNotEmpty())
                .andExpect(jsonPath("$.items[0].delivered").value(true));
    }

    /**
     * 가장 중요한 한 줄. 서버 로그로만 나간 건은 성공으로 기록되지만 사람에게는 안 갔다.
     * 사용자에게 "보냈습니다"라고 하면 오지도 않은 메일을 계속 기다린다.
     */
    @Test
    @DisplayName("로그 채널로 나간 것은 보냈다고 하지 않는다")
    void log_channel_is_not_delivered() throws Exception {
        Member me = newMember();
        logFor(me, NotificationChannel.LOG, NotificationResult.SUCCESS);

        mockMvc.perform(get(URL).header("Authorization", bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].delivered").value(false));
    }

    @Test
    @DisplayName("실패한 발송도 숨기지 않는다 — 못 받은 이유가 거기 있다")
    void failures_are_shown() throws Exception {
        Member me = newMember();
        logFor(me, NotificationChannel.EMAIL, NotificationResult.FAILED);

        mockMvc.perform(get(URL).header("Authorization", bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].delivered").value(false));
    }

    /** 남의 발송 기록이 새면 그건 사고다. */
    @Test
    @DisplayName("남이 받은 알림은 보이지 않는다")
    void other_members_history_is_invisible() throws Exception {
        Member other = newMember();
        logFor(other, NotificationChannel.EMAIL, NotificationResult.SUCCESS);

        mockMvc.perform(get(URL).header("Authorization", bearer(newMember())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }
}
