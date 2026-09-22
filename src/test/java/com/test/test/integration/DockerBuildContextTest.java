package com.test.test.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>도커 빌드에 프런트가 쓰는 파일이 다 들어가는가.</b>
 *
 * <p>2026-09-22 실측: 안 들어갔다. {@code frontend/src/main.tsx} 는 디자인 킷을
 * {@code ../../design_kits_lets/} 에서 읽는데(킷 문서가 정한 위치라 프런트 폴더 밖이다),
 * Dockerfile 1단계는 {@code frontend/} 만 복사했다. 그래서 컨테이너 안에서 그 경로가 비고
 * 빌드가 이렇게 죽는다:
 *
 * <pre>Could not resolve "../../design_kits_lets/base.css" from "src/main.tsx"</pre>
 *
 * <p>로컬에서는 폴더가 제자리에 있어 <b>아무 증상이 없다.</b> 배포를 눌러야 알게 되는 부류라,
 * 프런트 폴더 밖을 새로 참조할 때마다 사람이 Dockerfile 을 같이 고칠 거라고 믿는 대신 여기서 잡는다.
 *
 * <p>컨트롤러를 안 거치는 검사라 컨벤션 §6 의 예외(순수 검사)로 둔다 — 스프링 컨텍스트를 안 띄운다.
 */
class DockerBuildContextTest {

    /** {@code import '../../foo/bar.css'} 처럼 frontend/ 밖을 가리키는 import 를 집는다. */
    private static final Pattern OUT_OF_TREE =
            Pattern.compile("['\"]\\.\\./\\.\\./([^/'\"]+)/");

    private static Path repoRoot() {
        // 테스트는 저장소 루트에서 돈다(Gradle 기본). 그래도 못 찾으면 위로 올라가 본다.
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("Dockerfile"))) {
            p = p.getParent();
        }
        return p;
    }

    @Test
    @DisplayName("프런트가 frontend/ 밖에서 읽는 폴더는 Dockerfile 이 반드시 복사한다")
    void out_of_tree_imports_are_copied_into_the_image() throws IOException {
        Path root = repoRoot();
        assertThat(root).as("Dockerfile 이 있는 저장소 루트를 못 찾았습니다").isNotNull();

        Set<String> needed = new TreeSet<>();
        Path src = root.resolve("frontend/src");
        try (Stream<Path> files = Files.walk(src)) {
            for (Path f : files.filter(Files::isRegularFile)
                    .filter(f -> {
                        String n = f.getFileName().toString();
                        return n.endsWith(".ts") || n.endsWith(".tsx");
                    })
                    .toList()) {
                Matcher m = OUT_OF_TREE.matcher(Files.readString(f, StandardCharsets.UTF_8));
                while (m.find()) {
                    needed.add(m.group(1));
                }
            }
        }

        // 참조가 하나도 없으면 검사할 것도 없다 — 그것도 정상이다.
        if (needed.isEmpty()) {
            return;
        }

        String dockerfile = Files.readString(root.resolve("Dockerfile"), StandardCharsets.UTF_8);
        // 프런트를 빌드하는 1단계만 본다. 뒷 단계에 있어 봐야 npm run build 때는 이미 늦다.
        String webStage = dockerfile.split("(?m)^FROM ")[1];

        for (String folder : needed) {
            assertThat(webStage)
                    .as("frontend 가 ../../%s 를 읽는데 Dockerfile 의 프런트 빌드 단계가 그 폴더를 "
                            + "복사하지 않습니다. 컨테이너 안에서 경로가 비어 npm run build 가 깨집니다.", folder)
                    .contains("COPY " + folder + "/");
        }
    }

    /**
     * 복사만으로는 모자라다 — <b>상대 경로가 맞아야</b> 한다.
     * {@code ../../design_kits_lets} 는 프런트 폴더의 부모에서 찾으므로,
     * 이미지 안에서도 킷이 프런트의 형제로 있어야 한다.
     */
    @Test
    @DisplayName("이미지 안에서도 킷은 frontend 의 형제 자리에 놓인다")
    void kit_sits_next_to_frontend_in_the_image() throws IOException {
        Path root = repoRoot();
        String dockerfile = Files.readString(root.resolve("Dockerfile"), StandardCharsets.UTF_8);
        String webStage = dockerfile.split("(?m)^FROM ")[1];

        List<String> lines = webStage.lines().map(String::trim).toList();
        String workdir = lines.stream()
                .filter(l -> l.startsWith("WORKDIR "))
                .map(l -> l.substring("WORKDIR ".length()).trim())
                .findFirst().orElse("");

        assertThat(workdir)
                .as("프런트 작업 폴더가 루트 바로 아래면 ../../ 가 이미지 바깥을 가리킵니다. "
                        + "저장소와 같은 모양(<루트>/frontend)으로 두세요.")
                .endsWith("/frontend");
    }
}
