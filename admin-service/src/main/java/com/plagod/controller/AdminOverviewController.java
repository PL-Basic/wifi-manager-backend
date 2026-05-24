package com.plagod.controller;

import com.plagod.dto.AdminOverviewVO;
import com.plagod.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminOverviewController {

    @GetMapping("/overview")
    public ApiResponse<AdminOverviewVO> overview() {
        AdminOverviewVO overview = new AdminOverviewVO();
        overview.setGatewayStatus("UP");
        overview.setUserServiceStatus("UP");
        overview.setDeviceServiceStatus("UP");
        overview.setMonitorServiceStatus("NOT_CREATED");
        return ApiResponse.success(overview);
    }
}
