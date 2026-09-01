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

    /** 특정 자격증을 관심 등록한 사용자들 (알림 발송 대상 조회) */
    @Query("SELECT f.member FROM UserFavorite f WHERE f.certificate.id = :certificateId")
    List<Member> findUsersByCertificateId(@Param("certificateId") Long certificateId);

    @Query("SELECT f.certificate.id FROM UserFavorite f WHERE f.member.id = :memberId")
    List<Long> findCertificateIdsByMemberId(@Param("memberId") Long memberId);
}
