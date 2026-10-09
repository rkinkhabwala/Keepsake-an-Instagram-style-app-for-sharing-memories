package com.keepsake.media.dto;

import java.time.LocalDate;

public record UploadMetadataDto(LocalDate takenOn, Double latitude, Double longitude) {
}
