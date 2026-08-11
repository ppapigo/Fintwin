package com.fintwin.fintwin.pattern;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PatternUploadPrivacyArchitectureTest {
    @Test
    void uploadAnalysisHasNoAiStorageCacheOrSessionDependency() throws Exception {
        String service = source("src/main/java/com/fintwin/fintwin/pattern/service/FinancialPatternAnalysisService.java");
        String csvParser = source("src/main/java/com/fintwin/fintwin/pattern/parser/TransactionCsvParser.java");
        String xlsxParser = source("src/main/java/com/fintwin/fintwin/pattern/parser/TransactionXlsxParser.java");
        String sources = service + csvParser + xlsxParser;

        assertThat(sources).doesNotContain(
                "com.fintwin.fintwin.ai", "OpenAi", "RestClient", "WebClient",
                "Repository", ".save(", "Files.write", "Path.of", "Cache", "HttpSession");
        assertThat(service).contains("@Transactional(readOnly = true)")
                .doesNotContain("@Transactional(readOnly = false)");
    }

    private String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
