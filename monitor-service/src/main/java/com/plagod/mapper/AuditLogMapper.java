package com.plagod.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.plagod.entity.monitor.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface AuditLogMapper {

    Page<AuditLog> selectAuditPage(
            Page<AuditLog> page,
            @Param("action") String action,
            @Param("operatorName") String operatorName,
            @Param("target") String target,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    AuditLog selectAuditById(@Param("id") Long id);
}
