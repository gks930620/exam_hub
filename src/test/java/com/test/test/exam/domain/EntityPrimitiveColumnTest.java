package com.test.test.exam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>나중에 칸을 하나 더 붙이면 옛 행을 못 읽게 되는 함정.</b>
 *
 * <p>{@code ddl-auto: update} 는 칸만 붙이고 기존 행은 건드리지 않는다. 그래서 새 칸은
 * <b>옛 행에서 전부 NULL</b> 이다. 그 칸을 원시 타입({@code boolean}·{@code int})으로 받으면
 * 그 행을 읽는 순간 터진다:
 *
 * <pre>Null value was assigned to a property of primitive type</pre>
 *
 * <p>2026-09-22 에 실제로 겪었다 — {@code CrawlLog.partial} 을 원시 {@code boolean} 으로 넣었더니
 * 매니저 수집 상태 화면이 통째로 500 이 됐다. 로컬은 파일 H2 라 옛 행이 남아 있고, 운영도
 * 재배포 때 같은 일이 난다. <b>컴파일도 테스트도 통과하고 화면만 죽는</b> 부류라 여기서 잡는다.
 *
 * <p>규칙: 엔티티의 원시 필드는 {@code @Column(nullable = false)} 를 밝힌다.
 * 이미 데이터가 있는 표에 칸을 더할 때는 그것으로도 모자라니, 그럴 땐 래퍼 타입으로 받고
 * 읽는 쪽에서 비어 있음을 처리한다({@code CrawlLog.isPartial()} 이 그 예다).
 */
class EntityPrimitiveColumnTest {

    @Test
    @DisplayName("엔티티의 원시 필드는 nullable 이 아님을 밝힌다")
    void primitive_fields_declare_not_null() {
        List<String> offenders = new ArrayList<>();

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        for (BeanDefinition bd : scanner.findCandidateComponents("com.test.test")) {
            Class<?> type;
            try {
                type = Class.forName(bd.getBeanClassName());
            } catch (ClassNotFoundException e) {
                continue;
            }
            for (Field f : type.getDeclaredFields()) {
                if (!f.getType().isPrimitive() || Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                Column column = f.getAnnotation(Column.class);
                if (column == null || column.nullable()) {
                    offenders.add(type.getSimpleName() + "." + f.getName()
                            + " (" + f.getType().getSimpleName() + ")");
                }
            }
        }

        assertThat(offenders)
                .as("원시 필드가 비어 있을 수 있는 칸에 매핑돼 있습니다. 나중에 추가한 칸이면 "
                        + "옛 행이 NULL 이라 읽는 순간 터집니다 — nullable = false 를 밝히거나, "
                        + "이미 데이터가 있는 표라면 래퍼 타입으로 받고 비어 있음을 처리하세요.")
                .isEmpty();
    }
}
