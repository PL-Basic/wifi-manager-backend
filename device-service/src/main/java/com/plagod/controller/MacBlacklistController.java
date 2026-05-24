package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.MacBlacklistCreateDTO;
import com.plagod.service.DeviceCommandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/blacklist")
public class MacBlacklistController {

    @Autowired
    private DeviceCommandService deviceCommandService;

    @PostMapping
    public ApiResponse<Void> addBlacklist(@Valid @RequestBody MacBlacklistCreateDTO createDTO) {
        deviceCommandService.addBlacklist(createDTO);
        return ApiResponse.success("黑名单新增成功", null);
    }

    @DeleteMapping("/{mac}")
    public ApiResponse<Void> removeBlacklist(@PathVariable String mac) {
        deviceCommandService.removeBlacklist(mac);
        return ApiResponse.success("黑名单移除成功", null);
    }
}
