package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.vo.device.SessionPageResult;
import com.plagod.vo.device.SessionRecordVO;
import com.plagod.entity.SessionRecord;
import com.plagod.mapper.SessionRecordMapper;
import com.plagod.service.SessionQueryService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class SessionQueryServiceImpl implements SessionQueryService {

    @Autowired
    private SessionRecordMapper sessionRecordMapper;

    @Override
    public SessionPageResult pageSessions(long current, long size, String mac, Long nodeId, Long userId, Integer status) {
        long pageCurrent = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 10 : Math.min(size, 100);

        QueryWrapper<SessionRecord> queryWrapper = new QueryWrapper<>();
        if (StringUtils.hasText(mac)) {
            queryWrapper.like("mac", mac);
        }
        if (nodeId != null) {
            queryWrapper.eq("node_id", nodeId);
        }
        if (userId != null) {
            queryWrapper.eq("user_id", userId);
        }
        if (status != null) {
            queryWrapper.eq("status", status);
        }
        queryWrapper.orderByDesc("login_time");

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SessionRecord> page =
                sessionRecordMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageCurrent, pageSize), queryWrapper);

        List<SessionRecordVO> records = new ArrayList<>();
        for (SessionRecord item : page.getRecords()) {
            SessionRecordVO vo = new SessionRecordVO();
            BeanUtils.copyProperties(item, vo);
            records.add(vo);
        }

        SessionPageResult result = new SessionPageResult();
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setRecords(records);
        return result;
    }
}
