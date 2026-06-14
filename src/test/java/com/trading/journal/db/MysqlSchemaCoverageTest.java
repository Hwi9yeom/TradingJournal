package com.trading.journal.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pure JUnit 5 unit test (NO Spring context, NO live MySQL required).
 *
 * <p>Scans every JPA entity under {@code src/main/java/com/trading/journal/entity}, extracts each
 * {@code @Table(name = "...")} value, and asserts that {@code
 * src/main/resources/db/mysql/schema-mysql.sql} declares a matching {@code CREATE TABLE} for it.
 * Also asserts the non-entity {@code stress_test_result} table is present.
 *
 * <p>This guarantees the hand-maintained MySQL schema stays in coverage-sync with the entity model
 * without needing a database. (Type-level correctness is additionally verified by booting with
 * {@code --spring.profiles.active=mysql JPA_DDL_AUTO=validate} against a real MySQL.)
 */
class MysqlSchemaCoverageTest {

    /**
     * Matches @Table(name = "xyz") allowing single/double quotes and arbitrary whitespace. The name
     * attribute is anchored immediately after {@code @Table(} (every entity in this codebase
     * declares name first) so an {@code @Index(name = "...")} inside the @Table block is not
     * mistakenly captured as the table name.
     */
    private static final Pattern TABLE_NAME_PATTERN =
            Pattern.compile(
                    "@Table\\s*\\(\\s*name\\s*=\\s*[\"']([A-Za-z0-9_]+)[\"']", Pattern.DOTALL);

    @Test
    @DisplayName("schema-mysql.sql has a CREATE TABLE for every @Table entity + stress_test_result")
    void schemaCoversAllEntityTables() throws IOException {
        // Base dir = module directory when Gradle runs tests (working dir is the module root).
        File baseDir = new File("").getAbsoluteFile();

        File entityDir = new File(baseDir, "src/main/java/com/trading/journal/entity");
        assertThat(entityDir)
                .as("entity source directory must exist at %s", entityDir.getAbsolutePath())
                .isDirectory();

        File schemaFile = new File(baseDir, "src/main/resources/db/mysql/schema-mysql.sql");
        assertThat(schemaFile)
                .as("schema file must exist at %s", schemaFile.getAbsolutePath())
                .isFile();

        String schemaSql =
                new String(Files.readAllBytes(schemaFile.toPath()), StandardCharsets.UTF_8);

        // ---- Collect expected table names ----
        Set<String> expectedTables = new LinkedHashSet<>();

        File[] entityFiles = entityDir.listFiles((dir, name) -> name.endsWith(".java"));
        assertThat(entityFiles)
                .as("expected at least one entity .java file under %s", entityDir.getAbsolutePath())
                .isNotNull()
                .isNotEmpty();

        for (File entityFile : entityFiles) {
            String source =
                    new String(Files.readAllBytes(entityFile.toPath()), StandardCharsets.UTF_8);
            Matcher matcher = TABLE_NAME_PATTERN.matcher(source);
            while (matcher.find()) {
                expectedTables.add(matcher.group(1));
            }
        }

        assertThat(expectedTables)
                .as("at least one @Table(name=...) must be discovered in the entity package")
                .isNotEmpty();

        // Non-entity table created only by the native (formerly Flyway V4) migration.
        expectedTables.add("stress_test_result");

        // ---- Verify each expected table has a CREATE TABLE in the schema ----
        List<String> missing = new ArrayList<>();
        for (String table : expectedTables) {
            if (!schemaDeclaresTable(schemaSql, table)) {
                missing.add(table);
            }
        }

        assertThat(missing)
                .as(
                        "schema-mysql.sql is missing CREATE TABLE for the following table(s): %s%n"
                                + "Checked %d table(s): %s",
                        missing, expectedTables.size(), expectedTables)
                .isEmpty();
    }

    /**
     * Returns true if the schema contains a {@code CREATE TABLE} for {@code table},
     * case-insensitive, allowing optional backticks around the name and an optional {@code IF NOT
     * EXISTS} clause.
     */
    private static boolean schemaDeclaresTable(String schemaSql, String table) {
        String regex =
                "(?i)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?`?"
                        + Pattern.quote(table)
                        + "`?\\s*\\(";
        Pattern pattern = Pattern.compile(regex);
        return pattern.matcher(schemaSql).find();
    }
}
