package com.keepsake.media.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;

@Component
public class ExifReader {

    private static final DateTimeFormatter EXIF_DATE = DateTimeFormatter.ofPattern("yyyy:MM:dd");

    /** Best effort: unreadable or missing metadata yields {@link ExifData#EMPTY} fields rather than failing. */
    public ExifData read(byte[] bytes) {
        Metadata metadata;
        try {
            metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes), bytes.length);
        } catch (ImageProcessingException | IOException exception) {
            return ExifData.EMPTY;
        }
        GeoLocation location = gps(metadata);
        return new ExifData(
                takenOn(metadata),
                location == null ? null : location.getLatitude(),
                location == null ? null : location.getLongitude(),
                orientation(metadata));
    }

    private static LocalDate takenOn(Metadata metadata) {
        for (ExifSubIFDDirectory directory : metadata.getDirectoriesOfType(ExifSubIFDDirectory.class)) {
            for (int tag : new int[] {ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL,
                    ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED}) {
                LocalDate date = parseDate(directory, tag);
                if (date != null) {
                    return date;
                }
            }
        }
        return null;
    }

    /** EXIF timestamps carry no zone, so only the calendar date the camera recorded is kept. */
    private static LocalDate parseDate(Directory directory, int tag) {
        String value = directory.getString(tag);
        if (value == null || value.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(value.substring(0, 10), EXIF_DATE);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static GeoLocation gps(Metadata metadata) {
        for (GpsDirectory directory : metadata.getDirectoriesOfType(GpsDirectory.class)) {
            GeoLocation location = directory.getGeoLocation();
            if (location != null && !location.isZero()
                    && Math.abs(location.getLatitude()) <= 90 && Math.abs(location.getLongitude()) <= 180) {
                return location;
            }
        }
        return null;
    }

    private static int orientation(Metadata metadata) {
        ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
        if (directory == null) {
            return 1;
        }
        Integer orientation = directory.getInteger(ExifIFD0Directory.TAG_ORIENTATION);
        return orientation == null || orientation < 1 || orientation > 8 ? 1 : orientation;
    }
}
