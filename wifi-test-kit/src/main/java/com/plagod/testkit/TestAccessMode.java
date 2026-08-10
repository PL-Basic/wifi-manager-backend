package com.plagod.testkit;

public enum TestAccessMode {
    READ_ONLY,
    WRITE;

    public boolean allowsWrites() {
        return this == WRITE;
    }
}
