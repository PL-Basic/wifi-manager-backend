package com.plagod.service;

import com.plagod.entity.monitor.*;
import com.plagod.mapper.GeofenceEventMapper;
import com.plagod.mapper.GeofenceMapper;
import com.plagod.mapper.GeofenceStateMapper;
import com.plagod.util.GeoMath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class GeofenceEvaluationService {

    @Autowired
    private GeofenceMapper geofenceMapper;
    @Autowired
    private GeofenceStateMapper stateMapper;
    @Autowired
    private GeofenceEventMapper eventMapper;

    public void evaluate(ClientLocation previous, ClientLocation current) {
        requireTrustedLocation(current);

        List<Geofence> fences = geofenceMapper.selectEnabled();

        for (Geofence fence : fences) {
            evaluateFence(fence, previous, current);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void clearUserData(Long userId) {
        eventMapper.deleteByUserId(userId);
        stateMapper.deleteByUserId(userId);
    }

    private void evaluateFence(Geofence fence, ClientLocation previous, ClientLocation current) {

        GeofenceState state = stateMapper.selectForUpdate(fence.getFenceId(), current.getSessionId());

        Integer currentPosition = classify(fence, current);

        if (state == null) {
            initializeState(fence, previous, current, currentPosition);
            return;
        }

        if (Objects.equals(state.getLastLocationId(), current.getId())) {
            return;
        }

        if (state.getLastReportTime() != null && !current.getReportTime().isAfter(state.getLastReportTime())) {
            return;
        }

        int nextState = currentPosition == null ? state.getInsideState() : currentPosition;

        if (currentPosition != null && nextState != state.getInsideState()) {
            writeEvent(fence, current, nextState == 1 ? "ENTER" : "EXIT");
        }

        copyCurrentState(state, current, nextState);

        if (stateMapper.updateState(state) != 1) {
            throw new IllegalStateException("围栏状态更新失败");
        }
    }

    private void initializeState(Geofence fence, ClientLocation previous, ClientLocation current, Integer currentPosition) {

        Integer previousPosition = null;

        if (isPreviousPointUsable(fence, previous, current)) {
            previousPosition = classify(fence, previous);
        }

        int initialState;

        if (currentPosition != null) {
            initialState = currentPosition;
        } else if (previousPosition != null) {
            initialState = previousPosition;
        } else {
            initialState = exactPosition(fence, current);
        }

        GeofenceState state = new GeofenceState();
        state.setFenceId(fence.getFenceId());
        copyCurrentState(state, current, initialState);

        if (stateMapper.insertIgnore(state) == 0) {
            GeofenceState concurrent = stateMapper.selectForUpdate(fence.getFenceId(), current.getSessionId());

            if (concurrent == null) {
                throw new IllegalStateException("围栏状态初始化失败");
            }

            evaluateFence(fence, previous, current);
            return;
        }

        if (previousPosition != null && currentPosition != null && !previousPosition.equals(currentPosition)) {
            writeEvent(fence, current, currentPosition == 1 ? "ENTER" : "EXIT");
        }
    }

    private Integer classify(Geofence fence, ClientLocation location) {
        double distance = distance(fence, location);
        double accuracy = location.getAccuracy().doubleValue();
        double radius = fence.getRadiusMeters().doubleValue();

        if (distance + accuracy <= radius) {
            return 1;
        }

        if (distance - accuracy > radius) {
            return 0;
        }

        // 精度范围与围栏边界相交，不改变已有状态。
        return null;
    }

    private int exactPosition(Geofence fence, ClientLocation location) {
        return distance(fence, location) <= fence.getRadiusMeters().doubleValue() ? 1 : 0;
    }

    private double distance(Geofence fence, ClientLocation location) {
        return GeoMath.distanceMeters(fence.getCenterLatitude(), fence.getCenterLongitude(), location.getLatitude(), location.getLongitude());
    }

    private boolean isPreviousPointUsable(Geofence fence, ClientLocation previous, ClientLocation current) {

        if (previous == null
                || previous.getReportTime() == null
                || previous.getLatitude() == null
                || previous.getLongitude() == null
                || previous.getAccuracy() == null
                || !Objects.equals(previous.getSessionId(), current.getSessionId())) {
            return false;
        }

        LocalDateTime revisionTime = fence.getUpdateTime() != null ? fence.getUpdateTime() : fence.getCreateTime();

        return revisionTime == null || !previous.getReportTime().isBefore(revisionTime);
    }

    private void writeEvent(Geofence fence, ClientLocation location, String eventType) {
        GeofenceEvent event = new GeofenceEvent();
        event.setFenceId(fence.getFenceId());
        event.setLocationId(location.getId());
        event.setUserId(location.getUserId());
        event.setSessionId(location.getSessionId());
        event.setNodeId(location.getNodeId());
        event.setDeviceCode(location.getDeviceCode());
        event.setMac(location.getMac());
        event.setEventType(eventType);
        event.setEventTime(location.getReportTime());

        // 0表示相同位置事件已存在，属于幂等成功。
        eventMapper.insertIgnore(event);
    }

    private void copyCurrentState(GeofenceState state, ClientLocation current, int insideState) {
        state.setSessionId(current.getSessionId());
        state.setUserId(current.getUserId());
        state.setNodeId(current.getNodeId());
        state.setDeviceCode(current.getDeviceCode());
        state.setMac(current.getMac());
        state.setInsideState(insideState);
        state.setLastLocationId(current.getId());
        state.setLastReportTime(current.getReportTime());
    }

    private void requireTrustedLocation(ClientLocation location) {
        if (location == null
                || location.getId() == null
                || location.getUserId() == null
                || location.getSessionId() == null
                || location.getNodeId() == null
                || location.getReportTime() == null
                || location.getLatitude() == null
                || location.getLongitude() == null
                || location.getAccuracy() == null
                || location.getDeviceCode() == null
                || location.getMac() == null
                || !Integer.valueOf(1)
                .equals(location.getTrustedBinding())) {
            throw new IllegalStateException("围栏评估收到不完整的可信位置");
        }
    }
}