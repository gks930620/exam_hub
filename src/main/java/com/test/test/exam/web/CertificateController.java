package com.test.test.exam.web;

import com.test.test.exam.auth.CurrentMember;
import com.test.test.exam.domain.Member;
import com.test.test.exam.service.CertificateService;
import com.test.test.exam.web.dto.CertificateDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 자격증 조회 API (설계 05 §2-1, §2-2). <b>비로그인 공개</b>.
 * 로그인 상태면 {@code favorited} 플래그가 채워진다 — {@code @CurrentMember(required = false)}.
 */
@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;

    @GetMapping
    public ResponseEntity<CertificateDtos.SearchResponse> search(
            @RequestParam String query,
            @CurrentMember(required = false) Member member) {
        return ResponseEntity.ok(certificateService.search(query, idOf(member)));
    }

    /**
     * 전체 둘러보기 — 검색어 없이도 목록을 볼 수 있고, 일정이 아직 없는 시험도 포함한다.
     * 시험 찾기 화면이 인기 10종만 보여줘 "시험이 없다"고 느끼게 하던 문제를 이 API 가 해소한다.
     */
    @GetMapping("/browse")
    public ResponseEntity<CertificateDtos.BrowseResponse> browse(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size,
            @CurrentMember(required = false) Member member) {
        return ResponseEntity.ok(
                certificateService.browse(query, category, page, size, idOf(member)));
    }

    /** 분류 목록(+종목 수) — 시험 찾기 화면의 필터 칩. */
    @GetMapping("/stats")
    public ResponseEntity<CertificateDtos.StatsResponse> stats() {
        return ResponseEntity.ok(certificateService.stats());
    }

    @GetMapping("/categories")
    public ResponseEntity<CertificateDtos.CategoryResponse> categories() {
        return ResponseEntity.ok(certificateService.categories());
    }

    @GetMapping("/popular")
    public ResponseEntity<CertificateDtos.SearchResponse> popular(
            @CurrentMember(required = false) Member member) {
        return ResponseEntity.ok(certificateService.popular(idOf(member)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CertificateDtos.DetailResponse> detail(
            @PathVariable Long id,
            @RequestParam(required = false) Integer year,
            @CurrentMember(required = false) Member member) {
        return ResponseEntity.ok(certificateService.detail(id, year, idOf(member)));
    }

    /** slug 로 상세 조회 (React 자격증 정보 페이지). */
    @GetMapping("/by-slug/{slug}")
    public ResponseEntity<CertificateDtos.DetailResponse> detailBySlug(
            @PathVariable String slug,
            @RequestParam(required = false) Integer year,
            @CurrentMember(required = false) Member member) {
        var cert = certificateService.getBySlugOrThrow(slug);
        return ResponseEntity.ok(certificateService.buildDetail(cert, year, idOf(member)));
    }

    private Long idOf(Member member) {
        return member == null ? null : member.getId();
    }
}
