package com.plagod.dto;

import lombok.Data;

import javax.validation.constraints.*;
import java.math.BigDecimal;

@Data
public class ClientLocationReportDTO {

    @NotNull(message = "latitude is required")
    @DecimalMin(value = "-90.0", message = "latitude must be >= -90")
    @DecimalMax(value = "90.0", message = "latitude must be <= 90")
    private BigDecimal latitude;

    @NotNull(message = "longitude is required")
    @DecimalMin(value = "-180.0", message = "longitude must be >= -180")
    @DecimalMax(value = "180.0", message = "longitude must be <= 180")
    private BigDecimal longitude;

    @NotNull(message = "accuracy is required")
    @DecimalMin(value = "0.0", message = "accuracy must be >= 0")
    @DecimalMax(value = "1000.0", message = "accuracy must be <= 1000")
    private BigDecimal accuracy;

    @Pattern(regexp = "browser|portal|mobile", flags = Pattern.Flag.CASE_INSENSITIVE, message = "source must be browser, portal or mobile")
    private String source;
}