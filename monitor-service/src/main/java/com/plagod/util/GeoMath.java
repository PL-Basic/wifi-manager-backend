package com.plagod.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class GeoMath {

    public static final double EARTH_RADIUS_METERS = 6371000.0D;
    public static final double METERS_PER_LATITUDE_DEGREE = 111320.0D;

    private GeoMath() {
    }

    public static double distanceMeters(BigDecimal firstLatitude, BigDecimal firstLongitude, BigDecimal secondLatitude, BigDecimal secondLongitude) {

        double firstLat = Math.toRadians(firstLatitude.doubleValue());
        double secondLat = Math.toRadians(secondLatitude.doubleValue());
        double latitudeDelta = Math.toRadians(secondLatitude.doubleValue() - firstLatitude.doubleValue());
        double longitudeDelta = Math.toRadians(secondLongitude.doubleValue() - firstLongitude.doubleValue());

        double sinLatitude = Math.sin(latitudeDelta / 2.0D);
        double sinLongitude = Math.sin(longitudeDelta / 2.0D);

        double haversine = sinLatitude * sinLatitude
                + Math.cos(firstLat) * Math.cos(secondLat)
                * sinLongitude * sinLongitude;

        double bounded = Math.max(0.0D, Math.min(1.0D, haversine));
        return 2.0D * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(bounded));
    }

    public static double metersPerLongitudeDegree(double referenceLatitude) {
        double cosine = Math.abs(Math.cos(Math.toRadians(referenceLatitude)));

        // 极区不适合当前简单网格，保留最小比例避免除零。
        return METERS_PER_LATITUDE_DEGREE * Math.max(0.01D, cosine);
    }

    public static BigDecimal decimal(double value, int scale) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("地理计算结果不是有效数值");
        }

        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
}