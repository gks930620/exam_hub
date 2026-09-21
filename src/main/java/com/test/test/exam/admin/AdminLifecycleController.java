package com.test.test.exam.admin;

import com.test.test.exam.domain.Certificate;
import com.test.test.exam.repository.CertificateRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 매니저용 <b>시험 변천사</b> — 폐지·개칭된 시험이 어디로 갔나.
 *
 * <p><b>왜 지우지 않고 보여주나</b>: 폐지된 시험을 DB 에서 없애면 그 시험이 있었다는 사실까지
 * 사라진다. 그러면 "웹디자인기능사 찾는데 왜 없죠?" 라는 문의에 답할 근거가 없고,
 * 관심 등록해 둔 사람에게 왜 알림이 안 가는지도 설명하지 못한다.
 *
 * <p>사용자 화면(검색·목록)에서는 빠지고, 여기에만 남는다.
 */
@RestController
@RequestMapping("/api/admin/lifecycle")
@RequiredArgsConstructor
public class AdminLifecycleController {

    private final CertificateRepository certificateRepository;

    @GetMapping
    public ResponseEntity<LifecycleResponse> history() {
        List<Row> rows = certificateRepository.findLifecycleHistory().stream()
                .map(Row::of)
                .toList();

        long unverified = rows.stream().filter(r -> "UNVERIFIED".equals(r.getLifecycle())).count();
        return ResponseEntity.ok(new LifecycleResponse(rows, unverified));
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Row {
        private Long certificateId;
        private String name;
        private String category;
        private String lifecycle;
        private String lifecycleLabel;
        /** 이름이 바뀐 경우 새 이름 */
        private String supersededBy;
        /** 왜 이 상태인지 — 근거가 없으면 나중에 아무도 판단을 못 한다 */
        private String note;

        static Row of(Certificate c) {
            return new Row(c.getId(), c.getName(), c.getCategory(),
                    c.getLifecycle().name(), c.getLifecycle().getLabel(),
                    c.getSupersededBy(), c.getLifecycleNote());
        }
    }

    /**
     * @param needsCheck 확인이 필요한 건수 — "큐넷에 없다"만으로는 폐지라 단정할 수 없다.
     *                   컴퓨터활용능력처럼 시행처가 큐넷이 아니라서 없는 것도 있다.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LifecycleResponse {
        private List<Row> items;
        private long needsCheck;
    }
}
