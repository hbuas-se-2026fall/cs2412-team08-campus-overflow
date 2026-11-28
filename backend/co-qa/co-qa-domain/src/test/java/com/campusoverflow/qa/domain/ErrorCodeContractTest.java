package com.campusoverflow.qa.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** 契约测试：docs/错误码.md 是前端与后端共同依赖的契约，声明过的错误码必须在后端源码中真实存在。 */
class ErrorCodeContractTest {

    private static final Pattern DOC_CODE = Pattern.compile("`((?:ID|QA|REP|DIS|AUTH|SYS)-[A-Z0-9-]+)`");

    @Test
    void everyDocumentedErrorCodeExistsInBackendSources() throws IOException {
        Path repoRoot = locateRepoRoot();
        String doc = Files.readString(repoRoot.resolve("docs").resolve("错误码.md"), StandardCharsets.UTF_8);

        Set<String> documented = new LinkedHashSet<>();
        Matcher matcher = DOC_CODE.matcher(doc);
        while (matcher.find()) {
            documented.add(matcher.group(1));
        }
        assertThat(documented).hasSizeGreaterThanOrEqualTo(30);

        String sources = readBackendSources(repoRoot.resolve("backend"));
        List<String> missing = new ArrayList<>();
        for (String code : documented) {
            if (!sources.contains("\"" + code + "\"")) {
                missing.add(code);
            }
        }
        assertThat(missing).as("文档声明但后端未实现的错误码").isEmpty();
    }

    private static Path locateRepoRoot() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (Path p = start; p != null; p = p.getParent()) {
            if (Files.exists(p.resolve("docs").resolve("错误码.md"))) {
                return p;
            }
        }
        throw new IllegalStateException("未找到 docs/错误码.md，user.dir=" + start);
    }

    private static String readBackendSources(Path backend) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (Stream<Path> paths = Files.walk(backend)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().contains(File.separator + "src" + File.separator + "main"))
                    .filter(p -> !p.toString().contains(File.separator + "target" + File.separator))
                    .forEach(p -> {
                        try {
                            sb.append(Files.readString(p, StandardCharsets.UTF_8)).append('\n');
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    });
        }
        return sb.toString();
    }
}
