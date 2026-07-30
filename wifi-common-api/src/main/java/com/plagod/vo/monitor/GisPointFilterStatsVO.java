package com.plagod.vo.monitor;

import lombok.Data;

@Data
public class GisPointFilterStatsVO {

    private Integer loadedPointCount;
    private Integer usedPointCount;
    private Integer invalidPointCount;
    private Integer inaccuratePointCount;
    private Integer duplicatePointCount;
    private Integer speedOutlierPointCount;
}