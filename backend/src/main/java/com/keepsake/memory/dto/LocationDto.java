package com.keepsake.memory.dto;

/** A place name, coordinates, or both. */
public record LocationDto(String name, Double latitude, Double longitude) {
}
