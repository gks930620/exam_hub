package com.test.test.exam.admin;

import com.test.test.exam.collect.CollectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 종목 지정 재수집 — 매니저의 "다시 받아오기".
 *
 * <p><b>왜 필요한가</b>: 전량 수집(613콜) 중 일시 오류로 몇 종목이 비는 일이 실제로 있었다
 * (산업안전기사가 통째로 빠졌다). 다음 05:00 배치를 기다리거나 전량을 다시 도는 대신,
 * <b>빈 종목만 골라</b> 그만큼의 호출로 채운다. 큐넷 하루 한도(1,000콜)를 아끼는 길이기도 하다.
 *
 * <p>종목 없이 부르면 400 이다 — "비우면 전체"로 만들면 실수 한 번에 613콜이 나간다.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/collect")
@RequiredArgsConstructor
public class AdminCollectController {

    private final CollectService collectService;

    @PostMapping
    public ResponseEntity<CollectResponse> collect(@Valid @RequestBody CollectRequest request) {
        log.info("[매니저] 종목 재수집 요청 — {}종", request.sourceCodes().size());
        collectService.collectByCodes(request.sourceCodes());
        return ResponseEntity.ok(new CollectResponse(request.sourceCodes().size()));
    }

    public record CollectRequest(
            @NotEmpty(message = "다시 받을 종목코드를 지정하세요.")
            @Size(max = 650, message = "한 번에 650종까지만 — 전량은 05:00 배치가 합니다.")
            List<String> sourceCodes) {
    }

    public record CollectResponse(int requested) {
    }
}
