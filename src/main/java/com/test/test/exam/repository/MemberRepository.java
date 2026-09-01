package com.test.test.exam.repository;

import com.test.test.exam.domain.AuthProvider;
import com.test.test.exam.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    /** 식별 키 — 이메일이 아니라 (공급자, 공급자ID) 다. */
    Optional<Member> findByProviderAndProviderId(AuthProvider provider, String providerId);

    boolean existsByNickname(String nickname);

    /** 매니저 계정(비밀번호가 있는 LOCAL 계정)이 하나라도 있는지 — 로그인 화면 안내용. */
    boolean existsByProviderAndPasswordHashIsNotNull(AuthProvider provider);
}
