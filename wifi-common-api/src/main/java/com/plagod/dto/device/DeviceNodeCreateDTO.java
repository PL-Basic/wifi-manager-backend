package com.plagod.dto.device;

import lombok.Data;

import javax.validation.constraints.*;
import java.math.BigDecimal;


@Data
public class DeviceNodeCreateDTO {

    @NotBlank(message = "设备编码不能为空")
    private String deviceCode;
    
    @NotBlank(message = "设备名不能为空")
    private String name;

    private String location;

    @DecimalMin(value = "-90.0", message = "纬度不能小于-90")
    @DecimalMax(value = "90.0", message = "纬度不能大于90")
    @Digits(integer = 2, fraction = 7, message = "纬度最多保留七位小数")
    private BigDecimal latitude;

    @DecimalMin(value = "-180.0", message = "经度不能小于-180")
    @DecimalMax(value = "180.0", message = "经度不能大于180")
    @Digits(integer = 3, fraction = 7, message = "经度最多保留七位小数")
    private BigDecimal longitude;

    private String ip;

    private String firmwareVersion;

    @Min(value = 4,message = "设备最大连接数不能小于4")
    @Max(value = 128,message = "设备最大连接数不能大于128")
    private Integer maxClients;

    @Min(value = -100, message = "一米参考RSSI不能小于-100")
    @Max(value = -20, message = "一米参考RSSI不能大于-20")
    private Integer rssiAtOneMeter;

    @DecimalMin(value = "1.0", message = "路径损耗指数不能小于1.0")
    @DecimalMax(value = "6.0", message = "路径损耗指数不能大于6.0")
    @Digits(integer = 1, fraction = 2, message = "路径损耗指数最多保留两位小数")
    private BigDecimal pathLossExponent;


}