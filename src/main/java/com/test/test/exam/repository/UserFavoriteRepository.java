package com.test.test.exam.repository;

import com.test.test.exam.domain.Member;
import com.test.test.exam.domain.UserFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserFavoriteRepository extends JpaRepository<UserFavorite, Long> {

    List<UserFavorite> findByMemberOrderByCreatedAtAsc(Member member);

    long countByMember(Member member);

    boolean existsByMemberIdAndCertificateId(Long memberId, Long certificateId);

    Optional<UserFavorite> findByMemberIdAndCertificateId(Long memberId, Long certificateId);

    void deleteByMemberIdAndCertificateId(Long memberId, Long certificateId);

    /**
     * 특정 시험을 관심 등록한 사용자들 (알림 발송 대상 조회).
     * 탈퇴한 회원과 폐지·개칭으로 숨긴 시험은 뺀다 — 탈퇴자에게 메일이 가거나, 없어진 시험의 알림이 나가면 안 된다.
     */
    @Query("""
            SELECT f.member FROM UserFavorite f
             WHERE f.certificate.id = :certificateId
               AND f.member.status = com.test.test.exam.domain.MemberStatus.ACTIVE
               AND f.certificate.lifecycle IN (com.test.test.exam.domain.CertificateLifecycle.ACTIVE, com.test.test.exam.domain.CertificateLifecycle.UNVERIFIED)
            """)
    List<Member> findUsersByCertificateId(@Param("certificateId") Long certificateId);

    @Query("SELECT f.certificate.id FROM UserFavorite f WHERE f.member.id = :memberId")
    List<Long> findCertificateIdsByMemberId(@Param("memberId") Long memberId);
}
