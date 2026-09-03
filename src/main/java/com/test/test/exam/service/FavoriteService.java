package com.test.test.exam.service;

import com.test.test.common.exception.BusinessRuleException;
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
 * 관심 시험 등록/해제, 홈 D-day 카드, 월간 캘린더 (설계 05 §2-3, §2-4, §2-7).
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final UserFavoriteRepository favoriteRepository;
    private final CertificateRepository certificateRepository;
    private final ExamScheduleRepository examScheduleRepository;
    private final DdayService ddayService;

    /** 홈: 관심 시험별 D-day 카드 (정렬: 접수 중 → 진행 중 → 접수 예정 → 시험 예정, 같은 급은 임박순, 이벤트 없음은 뒤). */
    @Transactional(readOnly = true)
    public FavoriteDtos.ListResponse getFavorites(Member user) {
        List<UserFavorite> favorites = favoriteRepository.findByMemberOrderByCreatedAtAsc(user);

        List<FavoriteDtos.Card> cards = favorites.stream()
                .map(f -> toCard(f.getCertificate()))
                .sorted(Comparator
                        .comparingInt((FavoriteDtos.Card c) -> badgePriority(c.badge()))
                        .thenComparing(FavoriteDtos.Card::dday, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        return new FavoriteDtos.ListResponse(cards);
    }

    /**
     * 카드 한 장. 판정은 시험 찾기 카드와 같은 {@link DdayService#summarize}.
     * 폐지·개칭으로 숨긴 시험은 카드를 <b>남기되</b>(사라지면 해제할 길이 없다) 이유를 붙이고 D-day 는 주지 않는다.
     */
    private FavoriteDtos.Card toCard(Certificate cert) {
        String hiddenReason = cert.hiddenReason();
        DdayService.ScheduleSummary summary = hiddenReason != null
                ? new DdayService.ScheduleSummary(DdayService.ScheduleState.NONE, NextEvent.none(), false, null)
                : ddayService.summarize(cert, examScheduleRepository
                        .findByCertificateIdAndStatusOrderByExamStartDateAsc(cert.getId(), ScheduleStatus.ACTIVE));
        NextEvent e = summary.next();
        return new FavoriteDtos.Card(
                cert.getId(), cert.getName(),
                e.badge().name(), e.badge().getLabel(),
                e.label(), TimeUtil.format(e.at()),
                e.isPresent() ? e.dday() : null,
                summary.state().name(),
                TimeUtil.format(summary.lastExamDate()),
                hiddenReason);
    }

    private int badgePriority(String badgeName) {
        try {
            return CardBadge.valueOf(badgeName).getPriority();
        } catch (IllegalArgumentException ex) {
            return CardBadge.NONE.getPriority();
        }
    }

    /**
     * 관심 등록. 중복 등록은 409, 폐지·개칭된 시험은 400 — 오지 않을 접수를 기다리게 두면 안 된다.
     * <p>개수 제한은 <b>없다</b> — 유료화를 하지 않기로 해서 무료 3개 제한(구 {@code Plan})을 없앴다.
     * 시험을 여러 개 준비하는 사람이 정상이고, 그걸 막을 이유가 없다.
     */
    @Transactional
    public FavoriteDtos.CreateResponse addFavorite(Member user, Long certificateId) {
        Certificate cert = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new EntityNotFoundException("시험을 찾을 수 없습니다."));

        if (!cert.isVisibleToUsers()) {
            throw new BusinessRuleException("관심 등록할 수 없는 시험입니다 — " + cert.hiddenReason());
        }
        if (favoriteRepository.existsByMemberIdAndCertificateId(user.getId(), certificateId)) {
            throw DuplicateResourceException.alreadyExists("이미 관심 등록된 시험입니다.");
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

    /** 월간 캘린더: 관심 시험의 접수 시작/마감/시험 이벤트 (설계 05 §2-7). 숨긴 시험(폐지·개칭)은 뺀다. */
    @Transactional(readOnly = true)
    public MeDtos.CalendarResponse calendar(Member user, int year, int month) {
        List<Long> favoriteIds = favoriteRepository.findCertificateIdsByMemberId(user.getId());
        if (favoriteIds.isEmpty()) {
            return new MeDtos.CalendarResponse(List.of());
        }
        Map<Long, String> nameById = certificateRepository.findAllById(favoriteIds).stream()
                .filter(Certificate::isVisibleToUsers)
                .collect(Collectors.toMap(Certificate::getId, Certificate::getName));
        if (nameById.isEmpty()) {
            return new MeDtos.CalendarResponse(List.of());
        }
        List<ExamSchedule> schedules = examScheduleRepository
                .findByCertificateIdInAndStatus(List.copyOf(nameById.keySet()), ScheduleStatus.ACTIVE);

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
