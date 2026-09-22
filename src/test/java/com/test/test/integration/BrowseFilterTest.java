package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <b>"지금 접수 중인 것만" 볼 수 있어야 한다.</b>
 *
 * <p>이 서비스의 약속은 하나다: 접수 마감을 놓치지 않게 한다. 그런데 2026-09-22 실측으로
 * 232종이 접수 중이고 그중 6종이 이번 주에 마감되는데, <b>사용자가 그 6종을 찾을 방법이 없었다.</b>
 * 검색 조건은 이름과 분류 둘뿐이었고 정렬도 상태 필터도 없어서 841종을 인기순으로 훑어야 했다.
 * 서버는 그 숫자를 이미 세고 있었고({@code /api/certificates/stats}) 어느 화면도 부르지 않았다.
 *
 * <p><b>숫자가 어긋나면 안 된다.</b> 칩에 "지금 접수 중 232"라고 써 놓고 눌렀을 때 다른 수가
 * 나오면 둘 중 무엇을 믿어야 할지 알 수 없다. 그래서 목록과 지표가 <b>같은 조건</b>을 쓰는지 잡는다.
 *
 * <p>정렬도 상태가 정한다 — 접수 중이면 마감이 급한 것부터다. 고를 것을 하나 더 만드는 대신
 * 상태 자체가 "무엇이 급한가"를 이미 담고 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class BrowseFilterTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private JsonNode json(String url) throws Exception {
        MvcResult r = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        return om.readTree(r.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("접수 중 필터의 개수가 지표의 숫자와 같다 — 다르면 둘 중 무엇을 믿어야 할지 모른다")
    void open_filter_agrees_with_the_headline_number() throws Exception {
        long headline = json("/api/certificates/stats").get("registrationOpen").asLong();
        long listed = json("/api/certificates/browse?state=OPEN&size=1").get("totalElements").asLong();

        assertThat(listed).isEqualTo(headline);
    }

    @Test
    @DisplayName("곧 접수 필터도 지표와 같다")
    void soon_filter_agrees_with_the_headline_number() throws Exception {
        long headline = json("/api/certificates/stats").get("openingWithin7Days").asLong();
        long listed = json("/api/certificates/browse?state=SOON&size=1").get("totalElements").asLong();

        assertThat(listed).isEqualTo(headline);
    }

    /** 접수 중만 골랐는데 접수 중이 아닌 것이 섞이면 필터가 거짓말을 하는 것이다. */
    @Test
    @DisplayName("접수 중 필터에는 접수 중인 시험만 나온다")
    void open_filter_returns_only_open_exams() throws Exception {
        JsonNode items = json("/api/certificates/browse?state=OPEN&size=50").get("items");

        assertThat(items).isNotEmpty();
        for (JsonNode it : items) {
            assertThat(it.get("nextBadge").asText())
                    .as("%s 가 접수 중이 아닌데 섞였다", it.get("name").asText())
                    .isEqualTo("REG_OPEN");
        }
    }

    /**
     * 접수 중 목록의 쓸모는 순서에 있다. 마감이 급한 것이 뒤에 묻히면 필터를 만든 의미가 없다 —
     * 이번 주에 마감되는 6종을 찾으러 온 사람이 인기순 232개를 넘겨야 한다.
     */
    @Test
    @DisplayName("접수 중은 마감이 급한 것부터 나온다")
    void open_filter_is_sorted_by_deadline() throws Exception {
        JsonNode items = json("/api/certificates/browse?state=OPEN&size=50").get("items");

        List<Integer> ddays = new ArrayList<>();
        for (JsonNode it : items) {
            if (!it.get("nextDday").isNull()) {
                ddays.add(it.get("nextDday").asInt());
            }
        }
        assertThat(ddays).isSorted();
    }

    @Test
    @DisplayName("곧 접수도 시작이 가까운 것부터 나온다")
    void soon_filter_is_sorted_by_opening() throws Exception {
        JsonNode items = json("/api/certificates/browse?state=SOON&size=50").get("items");

        List<Integer> ddays = new ArrayList<>();
        for (JsonNode it : items) {
            if (!it.get("nextDday").isNull()) {
                ddays.add(it.get("nextDday").asInt());
            }
        }
        assertThat(ddays).isSorted();
    }

    @Test
    @DisplayName("상태를 안 주면 예전처럼 전부 나온다 — 기존 화면이 깨지지 않는다")
    void no_state_keeps_the_old_behaviour() throws Exception {
        long all = json("/api/certificates/browse?size=1").get("totalElements").asLong();
        long open = json("/api/certificates/browse?state=OPEN&size=1").get("totalElements").asLong();

        assertThat(all).isGreaterThan(open);
    }

    @Test
    @DisplayName("검색어·분류와 같이 걸린다 — 좁혀 놓고 또 좁힐 수 있어야 한다")
    void state_combines_with_query_and_category() throws Exception {
        long open = json("/api/certificates/browse?state=OPEN&size=1").get("totalElements").asLong();
        long openInCategory = json("/api/certificates/browse?state=OPEN&category=" +
                java.net.URLEncoder.encode("국가기술자격-건설", java.nio.charset.StandardCharsets.UTF_8)
                + "&size=1").get("totalElements").asLong();

        assertThat(openInCategory).isLessThanOrEqualTo(open);
    }

    @Test
    @DisplayName("모르는 상태값은 400 으로 막는다 — 조용히 전체를 주면 사용자가 속는다")
    void unknown_state_is_rejected() throws Exception {
        mvc.perform(get("/api/certificates/browse?state=WHATEVER"))
                .andExpect(status().isBadRequest());
    }

    /**
     * 두 칩이 겹치면 같은 시험이 양쪽에 나오고, 카드가 보여주는 사건과 정렬 기준이 달라져
     * 목록이 D-1 · D-15 · D-2 처럼 뒤죽박죽으로 보인다(2026-09-22 실측, HSK).
     * 이미 열렸으면 "지금 접수 중"이지 "곧 시작"이 아니다.
     */
    @Test
    @DisplayName("두 칩은 겹치지 않는다 — 이미 열린 시험은 '곧 시작'이 아니다")
    void open_and_soon_do_not_overlap() throws Exception {
        JsonNode soon = json("/api/certificates/browse?state=SOON&size=100").get("items");

        for (JsonNode it : soon) {
            assertThat(it.get("nextBadge").asText())
                    .as("%s 는 이미 접수 중인데 '곧 시작'에 있다", it.get("name").asText())
                    .isNotEqualTo("REG_OPEN");
        }
    }
}
