package com.keepsake.memory.service;

import com.keepsake.common.pagination.InvalidRequestException;

/**
 * A map viewport, {@code minLng,minLat,maxLng,maxLat} in degrees. When {@code minLng > maxLng} the box crosses
 * the antimeridian (e.g. 170,-10,-170,10 spans Fiji).
 */
public record BoundingBox(double minLng, double minLat, double maxLng, double maxLat) {

    public static BoundingBox parse(String value) {
        if (value == null) {
            throw invalid();
        }
        String[] parts = value.split(",");
        if (parts.length != 4) {
            throw invalid();
        }
        double[] numbers = new double[4];
        try {
            for (int index = 0; index < 4; index++) {
                numbers[index] = Double.parseDouble(parts[index].strip());
                if (!Double.isFinite(numbers[index])) {
                    throw invalid();
                }
            }
        } catch (NumberFormatException exception) {
            throw invalid();
        }
        BoundingBox box = new BoundingBox(numbers[0], numbers[1], numbers[2], numbers[3]);
        if (Math.abs(box.minLng) > 180 || Math.abs(box.maxLng) > 180
                || Math.abs(box.minLat) > 90 || Math.abs(box.maxLat) > 90 || box.minLat > box.maxLat) {
            throw invalid();
        }
        return box;
    }

    public boolean crossesAntimeridian() {
        return minLng > maxLng;
    }

    private static InvalidRequestException invalid() {
        return new InvalidRequestException(
                "bbox must be minLng,minLat,maxLng,maxLat with longitudes in [-180,180] and latitudes in [-90,90]");
    }
}
