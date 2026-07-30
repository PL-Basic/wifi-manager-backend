package com.plagod.dto.monitor;

import lombok.Data;

import javax.validation.constraints.*;
import java.math.BigDecimal;

@Data
public class GeofenceCreateDTO {

    @NotBlank(message = "围栏名称不能为空")
    @Size(max = 64, message = "围栏名称不能超过64个字符")
    private String name;

    @NotNull(message = "围栏中心纬度不能为空")
    @DecimalMin(value = "-90.0", message = "纬度不能小于-90")
    @DecimalMax(value = "90.0", message = "纬度不能大于90")
    @Digits(integer = 2, fraction = 7, message = "纬度最多保留七位小数")
    private BigDecimal centerLatitude;

    @NotNull(message = "围栏中心经度不能为空")
    @DecimalMin(value = "-180.0", message = "经度不能小于-180")
    @DecimalMax(value = "180.0", message = "经度不能大于180")
    @Digits(integer = 3, fraction = 7, message = "经度最多保留七位小数")
    private BigDecimal centerLongitude;

    @NotNull(message = "围栏半径不能为空")
    @DecimalMin(value = "5.0", message = "围栏半径不能小于5米")
    @DecimalMax(value = "10000.0", message = "围栏半径不能大于10000米")
    @Digits(integer = 5, fraction = 2, message = "围栏半径最多保留两位小数")
    private BigDecimal radiusMeters;

    @Min(value = 0, message = "enabled只能是0或1")
    @Max(value = 1, message = "enabled只能是0或1")
    private Integer enabled;

    @Size(max = 255, message = "围栏描述不能超过255个字符")
    private String description;
}