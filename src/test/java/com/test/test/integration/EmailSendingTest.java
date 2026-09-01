package com.test.test.integration;

import com.test.test.exam.domain.AuthProvider;
import com.test.test.exam.domain.Member;
import com.test.test.exam.domain.MemberRole;
import com.test.test.exam.domain.NotificationResult;
import com.test.test.exam.notification.EmailNotificationSender;
import com.test.test.exam.notification.NotificationMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 이메일이 <b>실제로 SMTP 대화를 거쳐 나가는지</b>를 확인한다.
 *
 * <p><b>왜 이런 테스트가 필요한가</b>: 알림 채널이 이메일 하나뿐인데, 이 경로는 Gmail 앱
 * 비밀번호를 넣기 전까지 아무도 밟아보지 못한 채로 있었다. 발송이 안 되면 사용자는
 * "알림이 안 왔다"는 사실조차 모른 채 접수 마감을 놓친다 — 조용한 실패가 가장 나쁘다.
 *
 * <p>그래서 테스트 안에 <b>가짜 SMTP 서버를 띄운다.</b> 외부 계정도, 새 라이브러리도 필요 없고,
 * 서버가 실제로 받은 바이트를 검사하므로 "빈이 떴다" 수준이 아니라 전송까지 확인된다.
 * Gmail 인증만 다를 뿐 메시지 조립·인코딩·전송은 운영과 같은 코드다.
 */
@SpringBootTest
@ActiveProfiles("test")
class EmailSendingTest {

    private static FakeSmtpServer smtp;

    @BeforeAll
    static void startSmtp() throws IOException {
        smtp = new FakeSmtpServer();
        smtp.start();
    }

    @AfterAll
    static void stopSmtp() {
        smtp.stop();
    }

    /** 포트를 실행 시점에 잡아야 해서(고정 포트는 다른 프로세스와 충돌한다) 동적 프로퍼티로 넣는다. */
    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("notification.email.enabled", () -> "true");
        registry.add("notification.email.from", () -> "no-reply@exam-hub.test");
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> smtp.port());
        registry.add("spring.mail.properties.mail.smtp.auth", () -> "false");
        registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");
        registry.add("app.link-base", () -> "http://localhost:8081");
    }

    @Autowired
    private EmailNotificationSender sender;

    private Member memberWithEmail(String email) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return Member.builder()
                .provider(AuthProvider.KAKAO)
                .providerId("kakao-" + unique)
                .email(email)
                .nickname("김취준")
                .role(MemberRole.USER)
                .build();
    }

    private NotificationMessage message() {
        return new NotificationMessage(
                "[D-3] 정보처리기사 원서접수 마감",
                "정보처리기사 필기 원서접수가 3일 뒤 마감됩니다.",
                Map.of("certificateId", "42"));
    }

    @Test
    @DisplayName("이메일이 SMTP 서버까지 실제로 전달된다")
    void sends_mail_over_smtp() throws Exception {
        NotificationResult result = sender.send(memberWithEmail("student@example.com"), message());

        assertEquals(NotificationResult.SUCCESS, result);

        String mail = smtp.awaitMail();
        assertNotNull(mail, "SMTP 서버가 메일을 못 받았다");
        assertTrue(mail.contains("student@example.com"), "수신자가 안 들어갔다");
    }

    /**
     * 제목·본문·닉네임이 전부 한글이다. 인코딩이 깨지면 사용자에게는 외계어가 도착하는데,
     * 발송 자체는 성공으로 찍혀서 로그만 봐서는 못 잡는다.
     */
    @Test
    @DisplayName("한글 제목·본문이 깨지지 않는다")
    void korean_survives_encoding() throws Exception {
        sender.send(memberWithEmail("student@example.com"), message());

        String decoded = FakeSmtpServer.decodeBody(smtp.awaitMail());
        assertTrue(decoded.contains("정보처리기사 원서접수 마감"), "제목이 깨졌다: " + decoded);
        assertTrue(decoded.contains("3일 뒤 마감됩니다"), "본문이 깨졌다");
        assertTrue(decoded.contains("김취준"), "닉네임이 깨졌다");
    }

    /**
     * 메일 속 버튼이 아무 데도 닿지 않던 버그(exam-hub.local 하드코딩)의 회귀 방지.
     * 링크가 죽으면 "마감 임박"을 읽고도 접수하러 갈 방법이 없다.
     */
    @Test
    @DisplayName("본문 링크가 app.link-base 를 따른다")
    void link_uses_configured_base() throws Exception {
        sender.send(memberWithEmail("student@example.com"), message());

        String decoded = FakeSmtpServer.decodeBody(smtp.awaitMail());
        assertTrue(decoded.contains("http://localhost:8081/cert/42"), "링크가 틀렸다: " + decoded);
        assertFalse(decoded.contains("exam-hub.local"), "하드코딩된 주소가 남아 있다");
    }

    /** 주소가 없는 사람은 실패가 아니라 "보낼 수 없음" — 재시도 대상이 아니다. */
    @Test
    @DisplayName("이메일이 없으면 보내지 않는다")
    void no_email_means_no_send() {
        assertEquals(NotificationResult.SUBSCRIPTION_EXPIRED,
                sender.send(memberWithEmail(null), message()));
    }

    // ===== 가짜 SMTP =====

    /**
     * 최소한의 SMTP 만 말한다 — EHLO/MAIL/RCPT/DATA/QUIT.
     * 받은 메일 원문을 큐에 넣어 테스트가 꺼내 본다.
     */
    static class FakeSmtpServer {
        private final BlockingQueue<String> received = new ArrayBlockingQueue<>(16);
        private ServerSocket server;
        private Thread thread;

        void start() throws IOException {
            server = new ServerSocket(0);
            thread = new Thread(this::acceptLoop, "fake-smtp");
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return server.getLocalPort();
        }

        void stop() {
            try {
                server.close();
            } catch (IOException ignored) {
                // 테스트 종료 중이라 더 할 것이 없다
            }
        }

        String awaitMail() throws InterruptedException {
            return received.poll(10, TimeUnit.SECONDS);
        }

        private void acceptLoop() {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    handle(socket);
                } catch (IOException e) {
                    return;   // 서버를 닫으면 여기로 떨어진다 — 정상 종료
                }
            }
        }

        private void handle(Socket socket) throws IOException {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            out.print("220 fake-smtp ready\r\n");
            out.flush();

            StringBuilder mail = new StringBuilder();
            boolean inData = false;
            String line;
            while ((line = in.readLine()) != null) {
                if (inData) {
                    if (".".equals(line)) {
                        received.offer(mail.toString());
                        mail.setLength(0);
                        inData = false;
                        out.print("250 OK\r\n");
                        out.flush();
                    } else {
                        mail.append(line).append('\n');
                    }
                    continue;
                }
                String cmd = line.length() >= 4 ? line.substring(0, 4).toUpperCase() : line;
                switch (cmd) {
                    case "EHLO", "HELO" -> out.print("250-fake-smtp\r\n250 SIZE 10240000\r\n");
                    case "DATA" -> {
                        out.print("354 go ahead\r\n");
                        inData = true;
                    }
                    case "QUIT" -> {
                        out.print("221 bye\r\n");
                        out.flush();
                        return;
                    }
                    default -> out.print("250 OK\r\n");
                }
                out.flush();
            }
        }

        /**
         * 한글은 헤더가 base64 encoded-word(=?UTF-8?B?…?=), 본문이 quoted-printable 로 실려 온다.
         * 사람이 읽을 수 있게 되돌려야 "깨졌는지"를 검사할 수 있다.
         *
         * <p>둘을 <b>따로</b> 풀어야 한다 — quoted-printable 은 바이트 단위라, 이미 문자로 푼
         * 헤더까지 같이 통과시키면 한글이 한 바이트로 잘려 도로 깨진다.
         */
        static String decodeBody(String raw) {
            String[] lines = raw.split("\n");
            StringBuilder headers = new StringBuilder();
            StringBuilder body = new StringBuilder();
            boolean inBody = false;

            for (String line : lines) {
                if (!inBody) {
                    if (line.isBlank()) {
                        inBody = true;
                    } else {
                        headers.append(decodeEncodedWords(line.strip())).append('\n');
                    }
                    continue;
                }
                // 줄 끝 '=' 은 soft line break — 지우고 이어 붙여야 UTF-8 바이트가 안 쪼개진다
                if (line.endsWith("=")) {
                    body.append(line, 0, line.length() - 1);
                } else {
                    body.append(line).append('\n');
                }
            }
            return headers + decodeQuotedPrintable(body.toString());
        }

        private static String decodeEncodedWords(String line) {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            while (i < line.length()) {
                int start = line.indexOf("=?", i);
                if (start < 0) {
                    sb.append(line.substring(i));
                    break;
                }
                sb.append(line, i, start);
                int end = line.indexOf("?=", start + 2);
                if (end < 0) {
                    sb.append(line.substring(start));
                    break;
                }
                String[] parts = line.substring(start + 2, end).split("\\?", 3);
                if (parts.length == 3 && "B".equalsIgnoreCase(parts[1])) {
                    sb.append(new String(java.util.Base64.getDecoder().decode(parts[2]),
                            StandardCharsets.UTF_8));
                } else {
                    sb.append(line, start, end + 2);
                }
                i = end + 2;
            }
            return sb.toString();
        }

        private static String decodeQuotedPrintable(String s) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '=' && i + 2 < s.length()) {
                    try {
                        bytes.write(Integer.parseInt(s.substring(i + 1, i + 3), 16));
                        i += 2;
                        continue;
                    } catch (NumberFormatException ignored) {
                        // '=' 뒤가 16진수가 아니면 그냥 문자다
                    }
                }
                bytes.write(c);
            }
            return bytes.toString(StandardCharsets.UTF_8);
        }
    }
}
