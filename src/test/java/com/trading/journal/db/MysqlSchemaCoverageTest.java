package com.trading.journal.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pure JUnit 5 unit test (NO Spring context, NO live MySQL required).
 *
 * <p>Scans every JPA entity under {@code src/main/java/com/trading/journal/entity} and asserts two
 * things about {@code src/main/resources/db/mysql/schema-mysql.sql}:
 *
 * <ol>
 *   <li>every {@code @Table(name = "...")} has a matching {@code CREATE TABLE} (coverage), and
 *   <li>no entity hardcodes a PostgreSQL-only {@code columnDefinition} type token that would make
 *       Hibernate {@code ddl-auto=validate} fail on MySQL.
 * </ol>
 *
 * <p>Full column/type/nullable parity is still only proven by booting with {@code
 * --spring.profiles.active=mysql JPA_DDL_AUTO=validate} against a real MySQL; these offline guards
 * catch the two most common drift classes without needing a database.
 */
class MysqlSchemaCoverageTest {

    /** Module root (Gradle runs tests with the module directory as the working dir). */
    private static final File BASE_DIR = new File("").getAbsoluteFile();

    private static final File ENTITY_DIR =
            new File(BASE_DIR, "src/main/java/com/trading/journal/entity");

    private static final File SCHEMA_FILE =
            new File(BASE_DIR, "src/main/resources/db/mysql/schema-mysql.sql");

    /** Matches {@code name = "xyz"} (single or double quotes). */
    private static final Pattern NAME_ATTR_PATTERN =
            Pattern.compile("name\\s*=\\s*[\"']([A-Za-z0-9_]+)[\"']");

    /** Captures the value of a {@code columnDefinition = "..."} attribute. */
    private static final Pattern COLUMN_DEFINITION_PATTERN =
            Pattern.compile("columnDefinition\\s*=\\s*[\"']([^\"']+)[\"']");

    /**
     * Type tokens that are PostgreSQL-only (or otherwise not valid MySQL 8.0 column types). If any
     * entity hardcodes one of these in a {@code columnDefinition}, {@code ddl-auto=validate} fails
     * on MySQL — this is exactly the {@code stress_scenario.sector_impacts "jsonb"} regression the
     * guard below prevents. Use {@code @JdbcTypeCode(SqlTypes.JSON)} (dialect-native) instead.
     */
    private static final Set<String> POSTGRES_ONLY_TYPES =
            Set.of("jsonb", "uuid", "serial", "bigserial", "smallserial", "timestamptz", "bytea");

    @Test
    @DisplayName("schema-mysql.sql has a CREATE TABLE for every @Table entity + stress_test_result")
    void schemaCoversAllEntityTables() throws IOException {
        assertThat(ENTITY_DIR)
                .as("entity source directory must exist at %s", ENTITY_DIR.getAbsolutePath())
                .isDirectory();
        assertThat(SCHEMA_FILE)
                .as("schema file must exist at %s", SCHEMA_FILE.getAbsolutePath())
                .isFile();

        String schemaSql = read(SCHEMA_FILE);

        Set<String> expectedTables = new LinkedHashSet<>();
        for (File entityFile : entityFiles()) {
            String tableName = extractTableName(read(entityFile));
            if (tableName != null) {
                expectedTables.add(tableName);
            }
        }

        assertThat(expectedTables)
                .as("at least one @Table(name=...) must be discovered in the entity package")
                .isNotEmpty();

        // Non-entity table created only by the native (formerly Flyway V4) migration.
        expectedTables.add("stress_test_result");

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

    @Test
    @DisplayName("no entity hardcodes a Postgres-only columnDefinition that breaks MySQL validate")
    void entitiesDeclareNoPostgresOnlyColumnDefinition() throws IOException {
        List<String> violations = new ArrayList<>();

        for (File entityFile : entityFiles()) {
            String source = read(entityFile);
            Matcher matcher = COLUMN_DEFINITION_PATTERN.matcher(source);
            while (matcher.find()) {
                String definition = matcher.group(1).trim();
                String firstToken = definition.split("[\\s(]", 2)[0].toLowerCase(Locale.ROOT);
                if (POSTGRES_ONLY_TYPES.contains(firstToken) || definition.contains("[]")) {
                    violations.add(
                            entityFile.getName() + ": columnDefinition=\"" + definition + "\"");
                }
            }
        }

        assertThat(violations)
                .as(
                        "entities must not hardcode Postgres-only column types in columnDefinition"
                                + " (they break ddl-auto=validate on MySQL). Use"
                                + " @JdbcTypeCode + dialect-native mapping instead. Offending: %s",
                        violations)
                .isEmpty();
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private static File[] entityFiles() {
        File[] files = ENTITY_DIR.listFiles((dir, name) -> name.endsWith(".java"));
        assertThat(files)
                .as(
                        "expected at least one entity .java file under %s",
                        ENTITY_DIR.getAbsolutePath())
                .isNotNull()
                .isNotEmpty();
        return files;
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /**
     * Extracts the {@code @Table(name = "...")} value robustly: it isolates the balanced
     * {@code @Table(...)} block and strips any nested {@code @Index(...)} /
     * {@code @UniqueConstraint(...)} sub-annotations BEFORE reading {@code name=}, so the table
     * name is found regardless of attribute order (an {@code @Index(name=...)} declared before
     * {@code name=} no longer hides the table). Returns {@code null} if the entity has no
     * {@code @Table}.
     */
    private static String extractTableName(String source) {
        int tableIdx = source.indexOf("@Table");
        if (tableIdx < 0) {
            return null;
        }
        String block = balancedBlock(source, source.indexOf('(', tableIdx));
        if (block == null) {
            return null;
        }
        String cleaned = stripAnnotation(stripAnnotation(block, "@Index"), "@UniqueConstraint");
        Matcher matcher = NAME_ATTR_PATTERN.matcher(cleaned);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Returns the substring from {@code openParenIdx} through its matching close paren (inclusive),
     * or {@code null} if no balanced block is found.
     */
    private static String balancedBlock(String s, int openParenIdx) {
        if (openParenIdx < 0) {
            return null;
        }
        int depth = 0;
        for (int i = openParenIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return s.substring(openParenIdx, i + 1);
                }
            }
        }
        return null;
    }

    /** Removes every {@code annotation(...)} occurrence (with balanced parens) from {@code s}. */
    private static String stripAnnotation(String s, String annotation) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            int at = s.indexOf(annotation, i);
            if (at < 0) {
                out.append(s, i, s.length());
                break;
            }
            out.append(s, i, at);
            int open = s.indexOf('(', at);
            String inner = balancedBlock(s, open);
            if (inner == null) {
                i = at + annotation.length();
                continue;
            }
            i = open + inner.length();
        }
        return out.toString();
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
        return Pattern.compile(regex).matcher(schemaSql).find();
    }
}
