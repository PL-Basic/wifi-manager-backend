package com.plagod.testkit;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class JsonContractAssertions {

    private static final Pattern DEFAULT_LONG_ID_FIELD =
            Pattern.compile("(?i)(^id$|.*Id$|.*Ids$)");

    private JsonContractAssertions() {
    }

    public static void assertLongIdsAreStrings(JsonNode root) {
        assertLongIdsAreStrings(root, field -> DEFAULT_LONG_ID_FIELD.matcher(field).matches());
    }

    public static void assertLongIdsAreStrings(
            JsonNode root,
            Predicate<String> longIdField) {

        if (root == null) {
            throw new AssertionError("JSON root is required");
        }
        inspect(root, "$", longIdField);
    }

    private static void inspect(
            JsonNode node,
            String path,
            Predicate<String> longIdField) {

        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String childPath = path + "." + field.getKey();
                if (longIdField.test(field.getKey())) {
                    assertIdValue(field.getValue(), childPath);
                }
                inspect(field.getValue(), childPath, longIdField);
            }
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                inspect(node.get(index), path + "[" + index + "]", longIdField);
            }
        }
    }

    private static void assertIdValue(JsonNode value, String path) {
        if (value == null || value.isNull() || value.isTextual()) {
            return;
        }
        if (value.isArray()) {
            for (int index = 0; index < value.size(); index++) {
                JsonNode item = value.get(index);
                if (item != null && !item.isNull() && !item.isTextual()) {
                    throw new AssertionError(path + "[" + index + "] must be a JSON string");
                }
            }
            return;
        }
        throw new AssertionError(path + " must be a JSON string");
    }
}
