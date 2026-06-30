package com.plagod.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@Data

public class DeviceNodeUpdateDTO {

    private String name;

    private String location;

    private String ip;

    @Min(value = 4,message = "设备最大连接数不能小于4")
    @Max(value = 128,message = "设备最大连接数不能大于128")
    private Integer maxClients;
}
