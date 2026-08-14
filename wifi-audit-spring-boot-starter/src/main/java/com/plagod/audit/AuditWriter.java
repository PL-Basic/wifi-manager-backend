package com.plagod.audit;

interface AuditWriter {

    void append(AuditWriteRecord record);
}
