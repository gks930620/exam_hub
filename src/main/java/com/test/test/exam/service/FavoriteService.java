package com.test.test.exam.service;

import com.test.test.common.exception.DuplicateResourceException;
import com.test.test.common.exception.EntityNotFoundException;
import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.*;
import com.test.test.exam.repository.CertificateRepository;
import com.test.test.exam.repository.ExamScheduleRepository;
import com.test.test.exam.repository.UserFavoriteRepository;
import com.test.test.exam.web.dto.FavoriteDtos;
import com.test.test.exam.web.dto.MeDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 관심 자격증 등록/해제, 홈 D-day 카드, 월간 캘린더 (설계 05 §2-3, §2-4, §2-7).
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final UserFavoriteRepository favoriteRepository;
    private final CertificateRepository certificateRepository;
    private final ExamScheduleRepository examScheduleRepository;
    private final DdayService ddayService;

    /** 홈: 관심 자격증별 D-day 카드 (정렬: 접수중 → 접수 예정 → 시험 예정, 접수중은 마감 임박순). */
    @Transactional(readOnly = true)
    public FavoriteDtos.ListResponse getFavorites(Member user) {
        List<UserFavorite> favorites = favoriteRepository.findByMemberOrderByCreatedAtAsc(user);

        List<FavoriteDtos.Card> cards = favorites.stream()
                .map(f -> toCard(f.getCertificate()))
                .sorted(Comparator
                        .comparingInt((FavoriteDtos.Card c) -> badgePriority(c.badge()))
                        .thenComparingLong(FavoriteDtos.Card::dday))
                .collect(Collectors.toList());

        return new FavoriteDtos.ListResponse(cards);
    }

    private FavoriteDtos.Card toCard(Certificate cert) {
        List<ExamSchedule> schedules = examScheduleRepository
                .findByCertificateIdAndStatusOrderByExamStartDateAsc(cert.getId(), ScheduleStatus.ACTIVE);
        NextEvent e = ddayService.computeNextEvent(schedules);
        return new FavoriteDtos.Card(
                cert.getId(), cert.getName(),
                e.badge().name(), e.badge().getLabel(),
                e.label(), TimeUtil.format(e.at()), e.dday());
    }

    private int badgePriority(String badgeName) {
        try {
            return CardBadge.valueOf(badgeName).getPriority();
        } catch (IllegalArgumentException ex) {
            return CardBadge.NONE.getPriority();
        }
    }

    /**
     * 관심 등록. 중복 등록은 409.
     * <p>개수 제한은 <b>없다</b> — 유료화를 하지 않기로 해서 무료 3개 제한(구 {@code Plan})을 없앴다.
     * 시험을 여러 개 준비하는 사람이 정상이고, 그걸 막을 이유가 없다.
     */
    @Transactional
    public FavoriteDtos.CreateResponse addFavorite(Member user, Long certificateId) {
        Certificate cert = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new EntityNotFoundException("자격증을 찾을 수 없습니다."));

        if (favoriteRepository.existsByMemberIdAndCertificateId(user.getId(), certificateId)) {
            throw DuplicateResourceException.alreadyExists("이미 관심 등록된 자격증입니다.");
        }

        favoriteRepository.save(UserFavorite.builder()
                .member(user).certificate(cert).build());
        cert.incrementFavorite();
        certificateRepository.save(cert);

        return new FavoriteDtos.CreateResponse(certificateId, cert.getFavoriteCount());
    }

    /** 관심 해제. */
    @Transactional
    public void removeFavorite(Member user, Long certificateId) {
        UserFavorite favorite = favoriteRepository
                .findByMemberIdAndCertificateId(user.getId(), certificateId)
                .orElseThrow(() -> new EntityNotFoundException("관심 등록 내역을 찾을 수 없습니다."));
        favoriteRepository.delete(favorite);

        certificateRepository.findById(certificateId).ifPresent(cert -> {
            cert.decrementFavorite();
            certificateRepository.save(cert);
        });
    }

    /** 월간 캘린더: 관심 자격증의 접수 시작/마감/시험 이벤트 (설계 05 §2-7). */
    @Transactional(readOnly = true)
    public MeDtos.CalendarResponse calendar(Member user, int year, int month) {
        List<Long> certIds = favoriteRepository.findCertificateIdsByMemberId(user.getId());
        if (certIds.isEmpty()) {
            return new MeDtos.CalendarResponse(List.of());
        }
        List<ExamSchedule> schedules = examScheduleRepository
                .findByCertificateIdInAndStatus(certIds, ScheduleStatus.ACTIVE);

        Map<Long, String> nameById = certificateRepository.findAllById(certIds).stream()
                .collect(Collectors.toMap(Certificate::getId, Certificate::getName));

        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());

        List<MeDtos.CalendarEvent> events = new ArrayList<>();
        for (ExamSchedule s : schedules) {
            Long cid = s.getCertificate().getId();
            String name = nameById.getOrDefault(cid, "");
            String typeLabel = s.getRound() + "회 " + s.getExamType().getLabel();

            addIfInMonth(events, s.getRegStartAt() == null ? null : s.getRegStartAt().toLocalDate(),
                    monthStart, monthEnd, "REG_START", cid, name, typeLabel + " 접수 시작");
            addIfInMonth(events, s.getRegEndAt() == null ? null : s.getRegEndAt().toLocalDate(),
                    monthStart, monthEnd, "REG_END", cid, name, typeLabel + " 접수 마감");
            addIfInMonth(events, s.getExamStartDate(),
                    monthStart, monthEnd, "EXAM", cid, name, typeLabel + " 시험");
            addIfInMonth(events, s.getResultDate(),
                    monthStart, monthEnd, "RESULT", cid, name, typeLabel + " 발표");
        }
        events.sort(Comparator.comparing(MeDtos.CalendarEvent::date));
        return new MeDtos.CalendarResponse(events);
    }

    private void addIfInMonth(List<MeDtos.CalendarEvent> out, LocalDate date,
                              LocalDate from, LocalDate to,
                              String type, Long cid, String name, String label) {
        if (date == null || date.isBefore(from) || date.isAfter(to)) {
            return;
        }
        out.add(new MeDtos.CalendarEvent(TimeUtil.format(date), type, cid, name, label));
    }
}
