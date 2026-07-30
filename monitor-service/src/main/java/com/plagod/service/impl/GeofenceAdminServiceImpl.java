package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.plagod.audit.Audited;
import com.plagod.dto.monitor.GeofenceCreateDTO;
import com.plagod.dto.monitor.GeofenceUpdateDTO;
import com.plagod.entity.monitor.Geofence;
import com.plagod.mapper.GeofenceEventMapper;
import com.plagod.mapper.GeofenceMapper;
import com.plagod.mapper.GeofenceStateMapper;
import com.plagod.service.GeofenceAdminService;
import com.plagod.vo.monitor.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class GeofenceAdminServiceImpl implements GeofenceAdminService {

    private static final Pattern MAC_PATTERN = Pattern.compile("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$");

    @Autowired
    private GeofenceMapper geofenceMapper;
    @Autowired
    private GeofenceStateMapper stateMapper;
    @Autowired
    private GeofenceEventMapper eventMapper;

    @Audited(action = "geofence.create")
    public GeofenceVO create(GeofenceCreateDTO dto) {
        Geofence entity = new Geofence();
        entity.setName(requireName(dto.getName()));
        entity.setCenterLatitude(normalizeLatitude(dto.getCenterLatitude()));
        entity.setCenterLongitude(normalizeLongitude(dto.getCenterLongitude()));
        entity.setRadiusMeters(normalizeRadius(dto.getRadiusMeters()));
        entity.setEnabled(dto.getEnabled() == null ? 1 : requireEnabled(dto.getEnabled()));
        entity.setDescription(cleanOptionalText(dto.getDescription()));
        entity.setDelFlag(0);

        if (geofenceMapper.insert(entity) != 1 || entity.getFenceId() == null) {
            throw new IllegalStateException("围栏创建失败");
        }

        return get(entity.getFenceId());
    }

    @Audited(action = "geofence.update")
    @Transactional(rollbackFor = Exception.class)
    public GeofenceVO update(Long fenceId, GeofenceUpdateDTO dto) {
        Geofence entity = requireFence(fenceId);
        boolean boundaryChanged = false;

        if (dto.getName() != null) {
            entity.setName(requireName(dto.getName()));
        }
        if (dto.getCenterLatitude() != null) {
            BigDecimal value = normalizeLatitude(dto.getCenterLatitude());

            if (entity.getCenterLatitude().compareTo(value) != 0) {
                entity.setCenterLatitude(value);
                boundaryChanged = true;
            }
        }
        if (dto.getCenterLongitude() != null) {
            BigDecimal value = normalizeLongitude(dto.getCenterLongitude());
            if (entity.getCenterLongitude().compareTo(value) != 0) {
                entity.setCenterLongitude(value);
                boundaryChanged = true;
            }
        }
        if (dto.getRadiusMeters() != null) {
            BigDecimal value = normalizeRadius(dto.getRadiusMeters());
            if (entity.getRadiusMeters().compareTo(value) != 0) {
                entity.setRadiusMeters(value);
                boundaryChanged = true;
            }
        }
        if (dto.getDescription() != null) {
            entity.setDescription(cleanOptionalText(dto.getDescription()));
        }

        if (geofenceMapper.updateById(entity) != 1) {
            throw new IllegalStateException("围栏更新失败");
        }

        // 边界变化后旧内外状态已经失效，下次位置点重新建立基线。
        if (boundaryChanged) {
            stateMapper.deleteByFenceId(fenceId);
        }

        return get(fenceId);
    }

    @Audited(action = "geofence.toggle")
    @Transactional(rollbackFor = Exception.class)
    public GeofenceVO toggle(Long fenceId, Integer enabled) {
        Geofence entity = requireFence(fenceId);
        int target = requireEnabled(enabled);

        if (!Integer.valueOf(target).equals(entity.getEnabled())) {
            entity.setEnabled(target);

            if (geofenceMapper.updateById(entity) != 1) {
                throw new IllegalStateException("围栏启停更新失败");
            }

            // 禁用或重新启用后都从新的位置基线开始。
            stateMapper.deleteByFenceId(fenceId);
        }

        return get(fenceId);
    }

    @Audited(action = "geofence.delete")
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long fenceId) {
        requireFence(fenceId);
        stateMapper.deleteByFenceId(fenceId);

        if (geofenceMapper.deleteById(fenceId) != 1) {
            throw new IllegalStateException("围栏删除失败");
        }
    }

    public GeofenceVO get(Long fenceId) {
        return toVO(requireFence(fenceId));
    }

    public GeofencePageResult page(long current, long size, Integer enabled, String keyword) {
        if (enabled != null) {
            requireEnabled(enabled);
        }

        long pageCurrent = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 10 : Math.min(size, 100);

        QueryWrapper<Geofence> query = new QueryWrapper<>();

        if (enabled != null) {
            query.eq("enabled", enabled);
        }
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            query.and(wrapper -> wrapper
                    .like("name", value)
                    .or()
                    .like("description", value));
        }

        query.orderByDesc("create_time").orderByDesc("fence_id");

        Page<Geofence> page = geofenceMapper.selectPage(new Page<>(pageCurrent, pageSize), query);

        List<GeofenceVO> records = new ArrayList<>();
        for (Geofence entity : page.getRecords()) {
            records.add(toVO(entity));
        }

        GeofencePageResult result = new GeofencePageResult();
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setRecords(records);
        return result;
    }

    public GeofenceEventPageResult pageEvents(long current, long size, Long fenceId, Long userId, Long sessionId, String mac, String eventType, LocalDateTime startTime, LocalDateTime endTime) {

        validateOptionalId(fenceId, "fenceId");
        validateOptionalId(userId, "userId");
        validateOptionalId(sessionId, "sessionId");

        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("事件结束时间不能早于开始时间");
        }

        String normalizedMac = normalizeOptionalMac(mac);
        String normalizedType = normalizeEventType(eventType);

        long pageCurrent = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 10 : Math.min(size, 100);

        Page<GeofenceEventVO> page = eventMapper.selectEventPage(new Page<>(pageCurrent, pageSize), fenceId, userId, sessionId, normalizedMac, normalizedType, startTime, endTime);

        for (GeofenceEventVO record : page.getRecords()) {
            record.setCoordinateSystem("WGS84");

            if (record.getLatitude() != null && record.getLongitude() != null) {
                GeofenceEventVO.GeoJsonPoint geometry = new GeofenceEventVO.GeoJsonPoint();

                geometry.setType("Point");
                geometry.setCoordinates(Arrays.asList(record.getLongitude(), record.getLatitude()));
                record.setGeometry(geometry);
            }
        }

        GeofenceEventPageResult result = new GeofenceEventPageResult();
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setRecords(page.getRecords());
        return result;
    }

    private Geofence requireFence(Long fenceId) {
        if (fenceId == null || fenceId <= 0) {
            throw new IllegalArgumentException("fenceId无效");
        }

        Geofence entity = geofenceMapper.selectById(fenceId);
        if (entity == null) {
            throw new IllegalArgumentException("围栏不存在");
        }
        return entity;
    }

    private GeofenceVO toVO(Geofence entity) {
        GeofenceVO vo = new GeofenceVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setCoordinateSystem("WGS84");

        GeofenceVO.GeoJsonPoint geometry = new GeofenceVO.GeoJsonPoint();
        geometry.setType("Point");
        geometry.setCoordinates(Arrays.asList(entity.getCenterLongitude(), entity.getCenterLatitude()));

        vo.setGeometry(geometry);
        return vo;
    }

    private String requireName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("围栏名称不能为空");
        }
        String value = name.trim();
        if (value.length() > 64) {
            throw new IllegalArgumentException("围栏名称不能超过64个字符");
        }
        return value;
    }

    private BigDecimal normalizeLatitude(BigDecimal value) {
        return normalizeCoordinate(value, -90.0D, 90.0D, "纬度");
    }

    private BigDecimal normalizeLongitude(BigDecimal value) {
        return normalizeCoordinate(value, -180.0D, 180.0D, "经度");
    }

    private BigDecimal normalizeCoordinate(BigDecimal value, double minimum, double maximum, String fieldName) {
        if (value == null || value.compareTo(BigDecimal.valueOf(minimum)) < 0 || value.compareTo(BigDecimal.valueOf(maximum)) > 0) {
            throw new IllegalArgumentException(fieldName + "范围无效");
        }
        if (value.stripTrailingZeros().scale() > 7) {
            throw new IllegalArgumentException(fieldName + "最多保留七位小数");
        }
        return value.setScale(7, RoundingMode.UNNECESSARY);
    }

    private BigDecimal normalizeRadius(BigDecimal value) {
        if (value == null || value.compareTo(new BigDecimal("5.00")) < 0 || value.compareTo(new BigDecimal("10000.00")) > 0) {
            throw new IllegalArgumentException("围栏半径必须在5到10000米之间");
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("围栏半径最多保留两位小数");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private int requireEnabled(Integer enabled) {
        if (enabled == null || (enabled != 0 && enabled != 1)) {
            throw new IllegalArgumentException("enabled只能是0或1");
        }
        return enabled;
    }

    private void validateOptionalId(Long id, String name) {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException(name + "无效");
        }
    }

    private String normalizeOptionalMac(String mac) {
        if (!StringUtils.hasText(mac)) {
            return null;
        }
        String value = mac.trim().toUpperCase(Locale.ROOT);
        if (!MAC_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("MAC格式不正确");
        }
        return value;
    }

    private String normalizeEventType(String eventType) {
        if (!StringUtils.hasText(eventType)) {
            return null;
        }
        String value = eventType.trim().toUpperCase(Locale.ROOT);
        if (!"ENTER".equals(value) && !"EXIT".equals(value)) {
            throw new IllegalArgumentException("eventType只能是ENTER或EXIT");
        }
        return value;
    }

    private String cleanOptionalText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}