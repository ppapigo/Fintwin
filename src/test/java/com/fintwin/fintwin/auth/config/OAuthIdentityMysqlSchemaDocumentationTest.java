package com.fintwin.fintwin.auth.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthIdentityMysqlSchemaDocumentationTest {
    private static final Path SCHEMA_DOCUMENT = Path.of("docs", "sql", "oauth_identity_mysql8.sql");

    @Test
    void usersIdentityTransitionTemporarilyDropsAndRestoresTheProfileForeignKey() throws IOException {
        String sql = Files.readString(SCHEMA_DOCUMENT);

        int dropForeignKey = sql.indexOf("DROP FOREIGN KEY");
        int alterUserId = sql.indexOf("MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT");
        int restoreForeignKey = sql.indexOf("ADD CONSTRAINT", alterUserId);

        assertThat(dropForeignKey).isGreaterThanOrEqualTo(0).isLessThan(alterUserId);
        assertThat(restoreForeignKey).isGreaterThan(alterUserId);
    }

    @Test
    void subjectCollationIsVerifiedWithoutSelectingSubjectValues() throws IOException {
        String sql = Files.readString(SCHEMA_DOCUMENT);

        assertThat(sql).contains("utf8mb4_0900_bin", "COLLATION_NAME");
        assertThat(sql).doesNotContain("SELECT provider_subject FROM oauth_identities");
    }
}
