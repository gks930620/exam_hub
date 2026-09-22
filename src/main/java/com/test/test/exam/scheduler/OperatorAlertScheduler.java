package com.test.test.exam.scheduler;

import com.test.test.exam.admin.HealthService;
import com.test.test.exam.admin.OperatorAlert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;
import java.util.Optional;

/**
 * 하루 한 번, <b>고장이 있으면</b> 운영자에게 메일을 보낸다.
 *
 * <p>{@code /admin/health} 를 만들었지만 그건 사람이 열어야 보인다. 혼자 운영하는 서비스에서 매일
 * 그 화면을 여는 사람은 없다. 스크래퍼가 조용히 죽으면 DB 에 옛 일정이 남아 사용자 화면은 멀쩡해
 * 보이고, 그 사이 사용자는 지난 날짜를 믿고 준비한다. 화면을 만든 것만으로는 이 구멍이 안 막힌다.
 *
 * <p><b>수집 배치와 떼어 놨다.</b> 06:00 에 따로 돈다 — 05:00 배치가 아예 안 돌거나 멈춰 버린 경우가
 * 가장 알고 싶은 상황인데, 배치 끝에 매달아 두면 그때 같이 죽어 아무 소식이 없다.
 * 지켜보는 쪽은 지켜보는 대상과 함께 죽으면 안 된다.
 *
 * <p>보낼 곳이 없으면({@code ALERT_EMAIL} 미설정, 또는 메일 자체가 꺼짐) 조용히 넘어가지 않고
 * 경고를 남긴다 — "알릴 수 없다"는 사실 자체가 알아야 할 일이다.
 */
@Slf4j
@Component
public class OperatorAlertScheduler {

    private final HealthService healthService;
    /** 메일이 꺼져 있으면 이 빈이 아예 없다 — 그래서 Optional 로 받는다. */
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String to;
    private final String from;
    private final String baseUrl;

    public OperatorAlertScheduler(HealthService healthService,
                                  ObjectProvider<JavaMailSender> mailSender,
                                  @Value("${app.operator-email:}") String to,
                                  @Value("${notification.email.from:}") String from,
                                  @Value("${app.link-base:}") String baseUrl) {
        this.healthService = healthService;
        this.mailSender = mailSender;
        this.to = to;
        this.from = from;
        this.baseUrl = baseUrl;
    }

    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")
    public void alertIfBroken() {
        Optional<OperatorAlert> alert = OperatorAlert.of(
                healthService.collectHealth(), healthService.notificationHealth(), baseUrl);

        if (alert.isEmpty()) {
            log.info("[운영경보] 손볼 것 없음 — 메일을 보내지 않는다");
            return;
        }
        OperatorAlert a = alert.get();

        JavaMailSender sender = mailSender.getIfAvailable();
        if (to.isBlank() || sender == null) {
            // 조용히 넘어가면 "고장을 알려 줄 수 없는 상태"가 영영 안 드러난다.
            log.error("[운영경보] 보낼 곳이 없어 메일을 못 보냈습니다(ALERT_EMAIL={}, 메일={}). 내용: {}",
                    to.isBlank() ? "미설정" : to, sender == null ? "꺼짐" : "켜짐", a.getSubject());
            return;
        }

        try {
            MimeMessage mime = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(a.getSubject());
            helper.setText(a.getBody(), false);
            sender.send(mime);
            log.info("[운영경보] 발송 — {}", a.getSubject());
        } catch (MailException | jakarta.mail.MessagingException e) {
            log.error("[운영경보] 발송 실패 — 내용: {}\n{}", a.getSubject(), a.getBody(), e);
        }
    }
}
