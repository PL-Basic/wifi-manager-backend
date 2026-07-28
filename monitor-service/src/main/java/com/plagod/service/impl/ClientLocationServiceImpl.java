package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.plagod.audit.Audited;
import com.plagod.dto.ClientLocationReportDTO;
import com.plagod.entity.ClientLocation;
import com.plagod.mapper.ClientLocationMapper;
import com.plagod.service.ClientLocationService;
import com.plagod.vo.monitor.ClientLocationPageResult;
import com.plagod.vo.monitor.ClientLocationVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ClientLocationServiceImpl implements ClientLocationService {

    @Autowired
    private ClientLocationMapper clientLocationMapper;

    @Override
    @Audited(action = "location.report")
    public Long report(ClientLocationReportDTO dto, Long userId) {
        if (dto == null) {
            throw new IllegalArgumentException("位置上报参数不能为空");
        }
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("缺少有效用户身份");
        }

        ClientLocation entity = new ClientLocation();
        BeanUtils.copyProperties(dto, entity);

        // 位置归属只能来自 Gateway 注入的可信用户身份。
        entity.setUserId(userId);

        LocalDateTime now = LocalDateTime.now();

        if (entity.getConsentTime() == null) {
            entity.setConsentTime(now);
        }
        if (entity.getReportTime() == null) {
            entity.setReportTime(now);
        }

        entity.setCreateTime(now);

        if (clientLocationMapper.insert(entity) != 1 || entity.getId() == null) {
            throw new IllegalStateException("位置上报保存失败");
        }

        return entity.getId();
    }

    @Override
    public ClientLocationPageResult pageLocations(long current, long size, String mac, Long userId, LocalDateTime startTime, LocalDateTime endTime) {

        return page(current, size, mac, userId, startTime, endTime);
    }

    @Override
    public ClientLocationPageResult pageOwnedLocations(Long ownerUserId, long current, long size, String mac, LocalDateTime startTime, LocalDateTime endTime) {

        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("缺少有效用户身份");
        }

        return page(current, size, mac, ownerUserId, startTime, endTime);
    }

    private ClientLocationPageResult page(long current, long size, String mac, Long userId, LocalDateTime startTime, LocalDateTime endTime) {

        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {

            throw new IllegalArgumentException("结束时间不能早于开始时间");
        }

        long pageCurrent = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 10 : Math.min(size, 100);

        QueryWrapper<ClientLocation> query = new QueryWrapper<>();

        if (StringUtils.hasText(mac)) {
            query.like("mac", mac.trim());
        }
        if (userId != null) {
            query.eq("user_id", userId);
        }
        if (startTime != null) {
            query.ge("report_time", startTime);
        }
        if (endTime != null) {
            query.le("report_time", endTime);
        }
        query.orderByDesc("report_time")
                .orderByDesc("id");

        Page<ClientLocation> resultPage = clientLocationMapper.selectPage(new Page<>(pageCurrent, pageSize), query);

        List<ClientLocationVO> records = new ArrayList<>();

        for (ClientLocation item : resultPage.getRecords()) {
            ClientLocationVO vo = new ClientLocationVO();
            BeanUtils.copyProperties(item, vo);
            records.add(vo);
        }

        ClientLocationPageResult result = new ClientLocationPageResult();
        result.setTotal(resultPage.getTotal());
        result.setCurrent(resultPage.getCurrent());
        result.setSize(resultPage.getSize());
        result.setRecords(records);

        return result;
    }
}