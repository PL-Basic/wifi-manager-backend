package com.plagod.service;

import com.plagod.dto.device.MacBlacklistCreateDTO;

public interface MacBlacklistService {
    // 新增黑名单并撤销该 MAC 的全部已分配 Session。
    void addBlacklist(MacBlacklistCreateDTO createDTO);
}
