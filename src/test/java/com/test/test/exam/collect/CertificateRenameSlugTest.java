package com.test.test.exam.collect;

import com.test.test.exam.common.TimeUtil;
import com.test.test.exam.domain.Certificate;
import com.test.test.exam.domain.ExamSchedule;
import com.test.test.exam.domain.ExamType;
import com.test.test.exam.domain.ScheduleProvenance;
import com.test.test.exam.domain.Series;
import com.test.test.exam.repository.CertificateRepository;
import com.test.test.exam.repository.ExamScheduleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * <b>수집이 시험 이름을 바꾸면 slug 도 같이 따라간다.</b>
 *
 * <p>수집은 이름이 아니라 <b>종목코드로 행을 찾는다</b>. 그래서 시행처가 종목명을 바꾸면
 * 같은 행의 이름만 갱신되고 slug 은 옛 이름으로 남는다 — {@code /api/certificates/by-slug/옛이름} 이
 * 새 이름의 시험을 돌려주고, 진짜 그 이름의 시험은 {@code 이름-2} 를 달게 된다(2026-09-10 실측).
 *
 * <p>예전엔 {@code CertificateMasterInitializer.reconcileSlugs()} 가 <b>다음 기동 때</b> 쓸어 담았다.
 * 시드를 SQL 하나로 바꾸며 그 로더를 걷어냈으므로, 이름이 바뀌는 그 자리에서 맞춘다 —
 * 다음 기동까지 어긋난 주소를 두지 않으니 이쪽이 원래 더 맞다.
 */
class CertificateRenameSlugTest {

    private static final LocalDate EXAM_DAY = TimeUtil.today().plusDays(30);

    private final CertificateRepository certificates = Mockito.mock(CertificateRepository.class);
    private final ExamScheduleRepository schedules = Mockito.mock(ExamScheduleRepository.class);
    private final DiffService diff = new DiffService(certificates, schedules);

    private Certificate existing(String name, String slug) {
        return Certificate.builder()
                .name(name).slug(slug).series(Series.ETC)
                .agency("한국방송통신전파진흥원").category("IT-보안").sourceCode("KCA-SEC")
                .build();
    }

    private CollectedSchedule renamedTo(String newName) {
        return new CollectedSchedule("KCA-SEC", newName, Series.ETC, "한국방송통신전파진흥원", "IT-보안",
                EXAM_DAY.getYear(), 1, ExamType.WRITTEN, null, null, EXAM_DAY, null, null,
                "https://www.cq.or.kr/", ScheduleProvenance.SCRAPED);
    }

    @Test
    @DisplayName("이름이 바뀌면 slug 도 새 이름이 된다 — 옛 주소가 남의 시험을 가리키지 않게")
    void slug_follows_the_new_name() {
        Certificate cert = existing("정보보안기사", "정보보안기사");
        Mockito.when(certificates.findBySourceCode("KCA-SEC")).thenReturn(Optional.of(cert));
        Mockito.when(certificates.existsBySlug(Mockito.anyString())).thenReturn(false);
        Mockito.when(schedules.findByCertificateAndYearAndRoundAndExamType(
                Mockito.any(), Mockito.anyInt(), Mockito.anyInt(), Mockito.any())).thenReturn(Optional.empty());
        Mockito.when(schedules.save(Mockito.any())).thenAnswer(i -> i.getArgument(0));

        diff.upsert(renamedTo("정보보안산업기사"));

        assertEquals("정보보안산업기사", cert.getName());
        assertEquals("정보보안산업기사", cert.getSlug(), "slug 이 옛 이름에 남았다");
    }

    @Test
    @DisplayName("새 이름을 이미 다른 시험이 slug 으로 쓰고 있으면 뒤에 번호를 붙인다")
    void slug_avoids_collision() {
        Certificate cert = existing("정보보안기사", "정보보안기사");
        Mockito.when(certificates.findBySourceCode("KCA-SEC")).thenReturn(Optional.of(cert));
        // 남이 이미 그 slug 을 쓰고 있다 → 그대로 쓰면 유니크 제약으로 수집이 통째로 깨진다
        Mockito.when(certificates.existsBySlug("정보보안산업기사")).thenReturn(true);
        Mockito.when(certificates.existsBySlug("정보보안산업기사-2")).thenReturn(false);
        Mockito.when(schedules.findByCertificateAndYearAndRoundAndExamType(
                Mockito.any(), Mockito.anyInt(), Mockito.anyInt(), Mockito.any())).thenReturn(Optional.empty());
        Mockito.when(schedules.save(Mockito.any())).thenAnswer(i -> i.getArgument(0));

        diff.upsert(renamedTo("정보보안산업기사"));

        assertEquals("정보보안산업기사-2", cert.getSlug());
    }

    @Test
    @DisplayName("이름이 그대로면 slug 을 건드리지 않는다 — 멀쩡한 주소를 바꾸면 링크가 죽는다")
    void slug_is_left_alone_when_the_name_did_not_change() {
        Certificate cert = existing("정보보안기사", "sec");   // 사람이 정해 둔 짧은 주소
        Mockito.when(certificates.findBySourceCode("KCA-SEC")).thenReturn(Optional.of(cert));
        Mockito.when(schedules.findByCertificateAndYearAndRoundAndExamType(
                Mockito.any(), Mockito.anyInt(), Mockito.anyInt(), Mockito.any())).thenReturn(Optional.empty());
        Mockito.when(schedules.save(Mockito.any())).thenAnswer(i -> i.getArgument(0));

        diff.upsert(renamedTo("정보보안기사"));

        assertEquals("sec", cert.getSlug());
    }
}
