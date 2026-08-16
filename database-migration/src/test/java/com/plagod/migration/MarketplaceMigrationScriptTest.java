package com.plagod.migration;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketplaceMigrationScriptTest {

    private static final String MIGRATION =
            "db/migration/V2_5_1__marketplace_demo_schema.sql";

    @Test
    void migrationCreatesOnlyFiveMarketplaceTables() throws IOException {
        String sql = resource(MIGRATION);

        assertEquals(5, occurrences(sql, "create table t_market_"));
        assertTrue(sql.contains("create table t_market_product"));
        assertTrue(sql.contains("create table t_market_sku"));
        assertTrue(sql.contains("create table t_market_order ("));
        assertTrue(sql.contains("create table t_market_order_item"));
        assertTrue(sql.contains("create table t_market_fulfillment"));
        assertTrue(sql.contains("V2_5_1_REQUIRED_PREDECESSOR_TABLES"));
        assertTrue(sql.contains("V2_5_1_PARTIAL_DDL_STATE"));

        assertFalse(sql.contains("create table t_inventory"));
        assertFalse(sql.contains("create table t_rental"));
        assertFalse(sql.contains("create table t_hardware_asset"));
        assertFalse(sql.contains("create table t_shipment"));
        assertFalse(sql.contains("create table t_payment_record"));
    }

    @Test
    void catalogAndItemSnapshotsUseTypedStableTargets() throws IOException {
        String sql = resource(MIGRATION);

        for (String type : new String[]{
                "PERSONAL_ENTITLEMENT",
                "SAAS_SUBSCRIPTION",
                "HARDWARE_DEMO"}) {
            assertTrue(sql.contains("''" + type + "''"), type);
        }

        assertTrue(sql.contains("specification_json json not null"));
        assertTrue(sql.contains("specification_snapshot_json json not null"));
        assertTrue(sql.contains("price_cents bigint not null"));
        assertTrue(sql.contains("unit_price_cents bigint not null"));
        assertTrue(sql.contains("subtotal_cents = unit_price_cents * quantity"));
        assertTrue(sql.contains("entitlement_product_code varchar(32)"));
        assertTrue(sql.contains("saas_plan_version_id bigint"));
        assertTrue(sql.contains("hardware_model_code varchar(64)"));
    }

    @Test
    void orderIdempotencyAndDemoPaymentAreExplicit() throws IOException {
        String sql = resource(MIGRATION);

        assertTrue(sql.contains("request_fingerprint char(64) not null"));
        assertTrue(sql.contains("uk_market_order_request"));
        assertTrue(sql.contains("subject_key varchar(96)"));
        assertTrue(sql.contains("total_amount_cents bigint not null"));
        assertTrue(sql.contains("''LOCAL_DEMO'', ''MANUAL_DEMO''"));
        assertTrue(sql.contains("''PENDING_DEMO_PAYMENT''"));
        assertTrue(sql.contains("''FULFILLMENT_FAILED''"));
    }

    @Test
    void fulfillmentIsLeasedAndHardwareStaysTrackOnly() throws IOException {
        String sql = resource(MIGRATION);

        assertTrue(sql.contains("uk_market_fulfillment_event"));
        assertTrue(sql.contains("attempt_count int not null default 0"));
        assertTrue(sql.contains("next_attempt_time datetime not null"));
        assertTrue(sql.contains("lease_until datetime default null"));
        assertTrue(sql.contains("worker_id varchar(64)"));
        assertTrue(sql.contains("last_error_key varchar(64)"));
        assertTrue(sql.contains("status = ''PROCESSING''"));
        assertTrue(sql.contains("or result_reference is not null"));
        assertTrue(sql.contains("fulfillment_mode = ''TRACK_ONLY''"));
        assertTrue(sql.contains("''DEMO_DELIVERY_PENDING''"));
        assertTrue(sql.contains("''DEMO_DELIVERED''"));
    }

    @Test
    void entitlementReceiptHasStableMarketplaceSourceWithoutPayment()
            throws IOException {
        String sql = resource(MIGRATION);
        String preDdlGuard = section(
                sql,
                "set @v2_5_1_entitlement_column_signature_count = (",
                "prepare v2_5_1_guard_stmt from @v2_5_1_guard_sql;");
        String postDdlGuard = section(
                sql,
                "set @v2_5_1_post_entitlement_column_signature_count = (",
                "prepare v2_5_1_guard_stmt from @v2_5_1_guard_sql;");

        assertTrue(sql.contains("add column source_type varchar(24)"));
        assertTrue(sql.contains(
                "when order_type = ''PURCHASE'' "
                        + "then ''DIRECT_PURCHASE''"));
        assertTrue(sql.contains(
                "when order_type = ''REWARD'' then ''ADMIN_REWARD''"));
        assertTrue(sql.contains(
                "when order_type = ''MARKETPLACE'' then ''MARKETPLACE''"));
        assertTrue(sql.contains("else null"));
        assertFalse(sql.contains("else ''DIRECT_PURCHASE''"));
        assertTrue(sql.contains("add column source_reference varchar(128)"));
        assertTrue(sql.contains("uk_entitlement_order_source"));
        assertTrue(sql.contains("chk_entitlement_order_type"));
        assertTrue(sql.contains(
                "order_type in "
                        + "(''PURCHASE'', ''REWARD'', ''MARKETPLACE'')"));
        assertTrue(sql.contains("chk_entitlement_market_source"));
        assertTrue(sql.contains("@v2_5_1_object_count = 10"));
        assertTrue(sql.contains(
                "@v2_5_1_entitlement_column_signature_count = 2"));
        assertTrue(preDdlGuard.contains(
                "set @v2_5_1_entitlement_index_row_count = (\n"
                        + "    select count(*)\n"
                        + "    from information_schema.statistics\n"
                        + "    where table_schema = database()\n"
                        + "      and table_name = 't_entitlement_order'\n"
                        + "      and index_name = "
                        + "'uk_entitlement_order_source'\n"
                        + ");"));
        assertTrue(preDdlGuard.contains(
                "@v2_5_1_entitlement_index_row_count = 3"));
        assertTrue(preDdlGuard.contains(
                "@v2_5_1_entitlement_index_signature_count = 3"));
        assertTrue(sql.contains(
                "@v2_5_1_entitlement_check_signature_count = 2"));
        assertTrue(sql.contains(
                "where order_type not in (''PURCHASE'', ''REWARD'')"));
        assertTrue(sql.contains(
                "@v2_5_1_object_count = 0,\n"
                        + "    'insert into v2_5_1_data_guard"));
        assertTrue(sql.contains(
                "@v2_5_1_post_entitlement_column_signature_count = 2"));
        assertTrue(postDdlGuard.contains(
                "set @v2_5_1_post_entitlement_index_row_count = (\n"
                        + "    select count(*)\n"
                        + "    from information_schema.statistics\n"
                        + "    where table_schema = database()\n"
                        + "      and table_name = 't_entitlement_order'\n"
                        + "      and index_name = "
                        + "'uk_entitlement_order_source'\n"
                        + ");"));
        assertTrue(postDdlGuard.contains(
                "@v2_5_1_post_entitlement_index_row_count = 3"));
        assertTrue(postDdlGuard.contains(
                "@v2_5_1_post_entitlement_index_signature_count = 3"));
        assertEquals(3, occurrences(preDdlGuard, "char(92)"));
        assertEquals(3, occurrences(postDdlGuard, "char(92)"));
        assertTrue(sql.contains(
                "@v2_5_1_post_entitlement_check_count = 2"));
        assertTrue(sql.contains(
                "@v2_5_1_post_entitlement_check_signature_count = 2"));
        assertEquals(2, occurrences(
                sql,
                "'elsenullend'"));
        assertEquals(2, occurrences(
                sql,
                "'order_typein''purchase'',''reward'',''marketplace'''"));
        assertEquals(2, occurrences(
                sql,
                "'orsource_referenceisnotnull'"));
        assertTrue(sql.contains("V2_5_1_POST_DDL_STATE"));
        assertFalse(sql.contains("@v2_5_1_object_count = 9"));
        assertFalse(sql.contains("alter table t_payment_record"));
    }

    private static String section(
            String value,
            String startMarker,
            String endMarker) {
        int start = value.indexOf(startMarker);
        assertTrue(start >= 0, startMarker);
        int end = value.indexOf(endMarker, start);
        assertTrue(end >= 0, endMarker);
        return value.substring(start, end);
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

    private static String resource(String path) throws IOException {
        try (InputStream input = MarketplaceMigrationScriptTest.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            assertTrue(input != null, path);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
