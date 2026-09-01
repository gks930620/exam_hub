package com.test.test.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.test.exam.auth.JwtProvider;
import com.test.test.exam.domain.AuthProvider;
import com.test.test.exam.domain.Member;
import com.test.test.exam.domain.MemberRole;
import com.test.test.exam.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * API 통합테스트 공통 베이스.
 *
 * <p>인증은 소셜 로그인 → JWT 다. 테스트에서 카카오·구글을 실제로 호출할 수는 없으므로
 * <b>Member 를 직접 만들고 JWT 를 직접 발급</b>해 검증한다.
 * 덕분에 <b>소셜 키가 없어도 인증이 걸린 API 를 전부 테스트할 수 있다.</b>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class ApiIntegrationTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected MemberRepository memberRepository;

    @Autowired
    protected JwtProvider jwtProvider;

    /** 매 테스트 고유한 일반 회원. */
    protected Member newMember() {
        return newMember(MemberRole.USER);
    }

    protected Member newAdmin() {
        return newMember(MemberRole.ADMIN);
    }

    protected Member newMember(MemberRole role) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return memberRepository.save(Member.builder()
                .provider(AuthProvider.GOOGLE)
                .providerId("test-" + unique)
                .email("test-" + unique + "@example.com")
                .nickname("테스터" + unique)
                .role(role)
                .build());
    }

    /** {@code Authorization} 헤더에 넣을 값. */
    protected String bearer(Member member) {
        return "Bearer " + jwtProvider.issue(member.getId(), member.getNickname(), member.getRole().name());
    }
}
