package com.plagod.testkit;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public final class JsonFixtureLoader {

    private final ObjectMapper objectMapper;
    private final ClassLoader classLoader;

    public JsonFixtureLoader(ObjectMapper objectMapper) {
        this(objectMapper, Thread.currentThread().getContextClassLoader());
    }

    public JsonFixtureLoader(ObjectMapper objectMapper, ClassLoader classLoader) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
    }

    public <T> T load(String resourcePath, Class<T> type) {
        String safePath = requireSafePath(resourcePath);
        try (InputStream input = classLoader.getResourceAsStream(safePath)) {
            if (input == null) {
                throw new IllegalArgumentException("fixture resource does not exist: " + safePath);
            }
            return objectMapper.readValue(input, Objects.requireNonNull(type, "type"));
        } catch (IOException exception) {
            throw new IllegalArgumentException("fixture resource is not valid JSON: " + safePath);
        }
    }

    private String requireSafePath(String resourcePath) {
        if (resourcePath == null
                || resourcePath.trim().isEmpty()
                || resourcePath.startsWith("/")
                || resourcePath.contains("..")
                || resourcePath.contains("\\")) {
            throw new IllegalArgumentException("fixture resource path must be classpath-relative");
        }
        return resourcePath;
    }
}
