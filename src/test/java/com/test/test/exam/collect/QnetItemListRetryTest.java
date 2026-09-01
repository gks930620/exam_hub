package com.test.test.exam.collect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 종목목록 재시도의 안전장치.
 *
 * <p><b>왜 이런 테스트가 있나</b>: 재시도를 넣으면서 실수로 재시도 메서드가 <b>자기 자신</b>을
 * 부르게 만들었다. 컴파일도 되고 테스트도 다 통과했는데, 실제로 돌리자
 * {@code StackOverflowError} 로 앱이 죽었다 — 로그 18만 바이트가 전부 스택트레이스였다.
 *
 * <p>키가 없어도 도는 검사만 둔다(네트워크를 타지 않는다).
 */
class QnetItemListRetryTest {

    /**
     * 재시도가 자기를 부르면 무한 재귀다.
     * 키가 없으면 한 번에 빈 목록으로 끝나야 하는데, 재귀면 스택이 터진다.
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("키가 없어도 스택이 터지지 않고 빈 목록으로 끝난다")
    void does_not_recurse_into_itself() throws Exception {
        QnetApiScheduleSource source = new QnetApiScheduleSource();
        // 키·URL 미설정 상태 그대로 — 네트워크를 타지 않는다
        setField(source, "apiKey", "");
        setField(source, "itemListUrl", "http://localhost:1/none");
        setField(source, "scheduleBaseUrl", "http://localhost:1/none");

        Method m = QnetApiScheduleSource.class.getDeclaredMethod("fetchItemListWithRetry");
        m.setAccessible(true);

        Object result = m.invoke(source);

        assertNotNull(result);
        assertTrue(((List<?>) result).isEmpty(), "키가 없으면 빈 목록이어야 한다");
    }

    /**
     * 재시도 메서드가 <b>진짜 조회 메서드</b>를 부르는지 소스로 확인한다.
     * 자기 자신을 부르면(오타 한 글자 차이) 위 테스트가 잡기 전에 여기서 걸린다.
     */
    @Test
    @DisplayName("재시도는 자기 자신이 아니라 조회 메서드를 부른다")
    void retry_calls_the_real_fetch() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/test/test/exam/collect/QnetApiScheduleSource.java"));

        int start = src.indexOf("private List<JmItem> fetchItemListWithRetry()");
        int end = src.indexOf("private List<JmItem> fetchItemList()", start);
        assertTrue(start >= 0 && end > start, "재시도 메서드를 못 찾았다 — 이름이 바뀌었나?");

        String body = src.substring(start, end);
        assertEquals(0, count(body, "fetchItemListWithRetry();"),
                "재시도가 자기 자신을 부른다 — 무한 재귀다");
        assertTrue(body.contains("fetchItemList();"), "재시도가 실제 조회를 부르지 않는다");
    }

    private int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }

    private void setField(Object target, String name, Object value) throws Exception {
        var f = QnetApiScheduleSource.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
