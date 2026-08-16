package com.plagod.migration;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserAccountPersistenceMigrationScriptTest {

    private static final String MIGRATION =
            "db/migration/V2_9_1__user_account_persistence_contract.sql";

    @Test
    void migrationAddsOnlyAccountCommandPersistenceCarriers()
            throws IOException {
        String sql = resource();

        assertEquals(1, occurrences(
                sql,
                "'create table t_user_account_command_receipt"));
        assertTrue(sql.contains(
                "'alter table t_default_tenant_membership_outbox"));
        assertTrue(sql.contains(
                "'alter table t_verify_code"));
        assertTrue(sql.contains(
                "add column idempotency_key varchar(64) default null"));
        assertTrue(sql.contains(
                "add column request_fingerprint char(64) default null"));
        assertFalse(Pattern.compile("(?m)^\\s*update\\s+")
                .matcher(sql).find());
        assertFalse(Pattern.compile("(?m)^\\s*delete\\s+")
                .matcher(sql).find());
        assertFalse(Pattern.compile(
                        "'\\s*(update|delete|insert|replace)\\s+")
                .matcher(sql).find());
        assertFalse(sql.contains("foreign key"));
    }

    @Test
    void migrationSeparatelyGuardsMissingCompleteAndPartialStates()
            throws IOException {
        String sql = resource();

        assertTrue(sql.contains(
                "@v2_9_1_outbox_object_count = 0"));
        assertTrue(sql.contains(
                "@v2_9_1_outbox_object_count = 3"));
        assertTrue(sql.contains(
                "@v2_9_1_outbox_signature_count = 3"));
        assertTrue(sql.contains(
                "v2_9_1_outbox_partial_ddl_state"));

        assertTrue(sql.contains(
                "@v2_9_1_verify_code_object_count = 0"));
        assertTrue(sql.contains(
                "@v2_9_1_verify_code_object_count = 2"));
        assertTrue(sql.contains(
                "@v2_9_1_verify_code_signature_count = 2"));
        assertTrue(sql.contains(
                "v2_9_1_verify_code_partial_ddl_state"));

        assertTrue(sql.contains(
                "@v2_9_1_receipt_object_count = 0"));
        assertTrue(sql.contains(
                "@v2_9_1_receipt_column_count = 8"));
        assertTrue(sql.contains(
                "@v2_9_1_receipt_column_signature_count = 8"));
        assertTrue(sql.contains(
                "@v2_9_1_receipt_index_row_count = 5"));
        assertTrue(sql.contains(
                "@v2_9_1_receipt_index_signature_count = 5"));
        assertTrue(sql.contains(
                "@v2_9_1_receipt_check_count = 2"));
        assertTrue(sql.contains(
                "@v2_9_1_receipt_constraint_count = 4"));
        assertTrue(sql.contains(
                "@v2_9_1_receipt_constraint_signature_count = 4"));
        assertTrue(sql.contains(
                "@v2_9_1_receipt_check_signature_count = 2"));
        assertTrue(sql.contains(
                "v2_9_1_receipt_partial_ddl_state"));
        assertTrue(sql.contains(
                "@v2_9_1_required_table_count = 2"));
        assertTrue(sql.contains(
                "@v2_9_1_required_anchor_count = 2"));
        assertTrue(sql.contains("v2_9_1_required_tables"));
        assertTrue(sql.contains("v2_9_1_post_ddl_state"));
        assertTrue(sql.contains(
                "@v2_9_1_post_outbox_signature_count = 3"));
        assertTrue(sql.contains(
                "@v2_9_1_post_verify_code_signature_count = 2"));
        assertTrue(sql.contains(
                "@v2_9_1_post_receipt_column_signature_count = 8"));
        assertTrue(sql.contains(
                "@v2_9_1_post_receipt_index_signature_count = 5"));
        assertTrue(sql.contains(
                "@v2_9_1_post_receipt_constraint_count = 4"));
        assertTrue(sql.contains(
                "@v2_9_1_post_receipt_constraint_signature_count = 4"));
        assertTrue(sql.contains(
                "@v2_9_1_post_receipt_check_signature_count = 2"));

        assertEquals(3, matches(
                sql,
                "(?m)^prepare v2_9_1_ddl_stmt"));
        assertEquals(3, matches(
                sql,
                "(?m)^deallocate prepare v2_9_1_ddl_stmt"));
    }

    @Test
    void migrationFreezesIdempotencyAndFingerprintConstraints()
            throws IOException {
        String sql = compact(resource());

        assertTrue(sql.contains(
                "unique key uk_default_membership_request "
                        + "(idempotency_key)"));
        assertTrue(sql.contains(
                "consume_request_key char(64) default null"));
        assertTrue(sql.contains(
                "unique key uk_verify_code_consume_request "
                        + "(consume_request_key)"));
        assertTrue(sql.contains(
                "unique key uk_user_account_command "
                        + "(command_type, idempotency_key)"));
        assertTrue(sql.contains(
                "request_fingerprint char(64) not null"));
        assertTrue(sql.contains(
                "constraint chk_user_account_command_type "
                        + "check (command_type in "
                        + "(''register'', ''password_replace''))"));
        assertTrue(sql.contains(
                "constraint chk_user_account_result_status "
                        + "check (result_status in (''succeeded''))"));
    }

    @Test
    void migrationValidatesExactObjectSignaturesBeforeSkipping()
            throws IOException {
        String sql = compact(resource());

        assertTrue(sql.contains("character_maximum_length = 64"));
        assertTrue(sql.contains("is_nullable = 'yes'"));
        assertTrue(sql.contains("column_default is null"));
        assertTrue(sql.contains("non_unique = 0"));
        assertTrue(sql.contains("seq_in_index = 1"));
        assertTrue(sql.contains("sub_part is null"));
        assertTrue(sql.contains("ordinal_position ="));
        assertTrue(sql.contains("datetime_precision = 0"));
        assertTrue(sql.contains("engine = 'innodb'"));
        assertTrue(sql.contains("index_type = 'btree'"));
        assertTrue(sql.contains("is_visible = 'yes'"));
        assertTrue(sql.contains("collation = 'a'"));
        assertTrue(sql.contains("constraints.enforced = 'yes'"));
        assertTrue(sql.contains(
                "table_collation = 'utf8mb4_unicode_ci'"));
        assertTrue(sql.contains(
                "table_comment = "
                        + "'user account persistence command "
                        + "idempotency receipt'"));
        assertTrue(sql.contains(
                "'command_typein(''register'',''password_replace'')'"));
        assertTrue(sql.contains(
                "'result_statusin(''succeeded'')'"));
        assertEquals(
                2,
                occurrences(
                        sql,
                        "'result_status=''succeeded'''"));
        assertFalse(sql.contains(
                "lower(checks.check_clause) like"));
    }

    @Test
    void migrationRejectsGeneratedColumnsAndUnexpectedConstraints()
            throws IOException {
        String sql = compact(resource());
        String anchors = section(
                sql,
                "set @v2_9_1_required_anchor_count",
                "set @v2_9_1_guard_sql");
        String outbox = section(
                sql,
                "set @v2_9_1_outbox_signature_count",
                "set @v2_9_1_verify_code_object_count");
        String verifyCode = section(
                sql,
                "set @v2_9_1_verify_code_signature_count",
                "set @v2_9_1_receipt_object_count");
        String receiptColumns = section(
                sql,
                "set @v2_9_1_receipt_column_signature_count",
                "set @v2_9_1_receipt_index_row_count");
        String postOutbox = section(
                sql,
                "set @v2_9_1_post_outbox_signature_count",
                "set @v2_9_1_post_verify_code_signature_count");
        String postVerifyCode = section(
                sql,
                "set @v2_9_1_post_verify_code_signature_count",
                "set @v2_9_1_post_receipt_table_signature_count");
        String postReceiptColumns = section(
                sql,
                "set @v2_9_1_post_receipt_column_signature_count",
                "set @v2_9_1_post_receipt_index_row_count");

        assertEquals(2, occurrences(anchors, "and extra = ''"));
        assertEquals(2, occurrences(
                anchors,
                "and generation_expression = ''"));
        assertEquals(2, occurrences(outbox, "and extra = ''"));
        assertEquals(2, occurrences(
                outbox,
                "and generation_expression = ''"));
        assertEquals(1, occurrences(verifyCode, "and extra = ''"));
        assertEquals(1, occurrences(
                verifyCode,
                "and generation_expression = ''"));
        assertEquals(5, occurrences(receiptColumns, "and extra = ''"));
        assertEquals(8, occurrences(
                receiptColumns,
                "and generation_expression = ''"));
        assertEquals(2, occurrences(postOutbox, "and extra = ''"));
        assertEquals(2, occurrences(
                postOutbox,
                "and generation_expression = ''"));
        assertEquals(1, occurrences(postVerifyCode, "and extra = ''"));
        assertEquals(1, occurrences(
                postVerifyCode,
                "and generation_expression = ''"));
        assertEquals(5, occurrences(
                postReceiptColumns,
                "and extra = ''"));
        assertEquals(8, occurrences(
                postReceiptColumns,
                "and generation_expression = ''"));

        assertReceiptConstraintSignature(sql, "");
        assertReceiptConstraintSignature(sql, "post_");
        assertReceiptCheckSignature(sql, "");
        assertReceiptCheckSignature(sql, "post_");
    }

    @Test
    void migrationCompletesAllPreflightChecksBeforeFirstDdl()
            throws IOException {
        String sql = resource();

        int partialState = sql.indexOf(
                "v2_9_1_receipt_partial_ddl_state");
        int guardExecution = sql.indexOf(
                "execute v2_9_1_guard_stmt",
                partialState);
        int firstDdl = sql.indexOf(
                "'alter table t_default_tenant_membership_outbox");
        assertTrue(partialState >= 0);
        assertTrue(guardExecution > partialState);
        assertTrue(firstDdl > guardExecution);
    }

    private static int occurrences(String value, String fragment) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(fragment, offset)) >= 0) {
            count++;
            offset += fragment.length();
        }
        return count;
    }

    private static int matches(String value, String expression) {
        int count = 0;
        java.util.regex.Matcher matcher =
                Pattern.compile(expression).matcher(value);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static void assertReceiptConstraintSignature(
            String sql,
            String phase) {
        String constraints = section(
                sql,
                "set @v2_9_1_" + phase + "receipt_constraint_count",
                "set @v2_9_1_" + phase
                        + "receipt_check_signature_count");

        assertTrue(constraints.contains(
                "constraint_name = 'primary' "
                        + "and constraint_type = 'primary key'"));
        assertTrue(constraints.contains(
                "constraint_name = 'uk_user_account_command' "
                        + "and constraint_type = 'unique'"));
        assertTrue(constraints.contains(
                "constraint_name = 'chk_user_account_command_type' "
                        + "and constraint_type = 'check'"));
        assertTrue(constraints.contains(
                "constraint_name = 'chk_user_account_result_status' "
                        + "and constraint_type = 'check'"));
    }

    private static void assertReceiptCheckSignature(
            String sql,
            String phase) {
        String checks = section(
                sql,
                "set @v2_9_1_" + phase
                        + "receipt_check_signature_count",
                "set @v2_9_1_guard_sql");

        assertTrue(checks.contains(
                "'result_status=''succeeded'''"));
        assertTrue(checks.contains(
                "'(result_status=''succeeded'')'"));
        assertEquals(2, occurrences(checks, "char(92)"));
    }

    private static String section(
            String value,
            String start,
            String end) {
        int startIndex = value.indexOf(start);
        int endIndex = value.indexOf(end, startIndex);
        assertTrue(startIndex >= 0, start);
        assertTrue(endIndex > startIndex, end);
        return value.substring(startIndex, endIndex);
    }

    private static String compact(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private static String resource() throws IOException {
        try (InputStream input =
                     UserAccountPersistenceMigrationScriptTest.class
                             .getClassLoader()
                             .getResourceAsStream(MIGRATION)) {
            assertTrue(input != null, MIGRATION);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return new String(
                    output.toByteArray(),
                    StandardCharsets.UTF_8).toLowerCase();
        }
    }
}
