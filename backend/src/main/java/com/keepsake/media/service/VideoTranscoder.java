package com.keepsake.media.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepsake.media.config.MediaProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Runs FFmpeg on untrusted uploads, defensively: input is always read as the mov/mp4 demuxer from a local file
 * only (no format sniffing, no network or playlist protocols), commands have timeouts, and every output drops
 * all metadata (including GPS and creation time).
 */
@Component
public class VideoTranscoder {

    public static final long MAX_DURATION_MS = 60_000;
    /** A little slack for container rounding, so a "60 s" phone clip is not rejected for being 60.02 s. */
    private static final long DURATION_SLACK_MS = 500;
    private static final long MAX_PIXELS_PER_FRAME = 8_294_400L * 2;
    private static final long TIMEOUT_SECONDS = 180;
    private static final List<String> SAFE_INPUT = List.of("-protocol_whitelist", "file", "-f", "mov");

    private final String ffmpeg;
    private final String ffprobe;
    private final ObjectMapper objectMapper;

    public VideoTranscoder(MediaProperties properties, ObjectMapper objectMapper) {
        this.ffmpeg = properties.ffmpeg();
        this.ffprobe = properties.ffprobe();
        this.objectMapper = objectMapper;
    }

    public record VideoInfo(long durationMs, int width, int height, boolean hasAudio) {
    }

    /** Reads the container and stream headers; rejects files without a video stream or over the limits. */
    public VideoInfo probe(Path video) throws MediaProcessingException {
        List<String> command = new ArrayList<>(List.of(ffprobe, "-v", "error"));
        command.addAll(SAFE_INPUT);
        command.addAll(List.of("-show_entries", "format=duration:stream=codec_type,width,height", "-of", "json",
                video.toString()));
        JsonNode json;
        try {
            json = objectMapper.readTree(run(command, "Video could not be read"));
        } catch (IOException exception) {
            throw new MediaProcessingException("Video could not be read", exception);
        }
        JsonNode videoStream = null;
        boolean hasAudio = false;
        for (JsonNode stream : json.path("streams")) {
            String type = stream.path("codec_type").asText();
            if (type.equals("video") && videoStream == null && stream.path("width").asInt() > 0) {
                videoStream = stream;
            }
            hasAudio |= type.equals("audio");
        }
        if (videoStream == null) {
            throw new MediaProcessingException("File has no video");
        }
        double seconds = json.path("format").path("duration").asDouble(-1);
        if (seconds <= 0) {
            throw new MediaProcessingException("Video duration could not be read");
        }
        long durationMs = Math.round(seconds * 1000);
        if (durationMs > MAX_DURATION_MS + DURATION_SLACK_MS) {
            throw new MediaProcessingException("Videos can be at most 60 seconds");
        }
        int width = videoStream.path("width").asInt();
        int height = videoStream.path("height").asInt();
        if ((long) width * height > MAX_PIXELS_PER_FRAME) {
            throw new MediaProcessingException("Video resolution is too high");
        }
        return new VideoInfo(durationMs, width, height, hasAudio);
    }

    /** One frame as a PNG (rotation applied), taken {@code atMs} into the video. */
    public Path poster(Path video, long atMs, Path outputDirectory) throws MediaProcessingException {
        Path poster = outputDirectory.resolve("poster.png");
        List<String> command = new ArrayList<>(List.of(ffmpeg, "-v", "error", "-nostdin", "-y",
                "-ss", String.format("%.3f", atMs / 1000.0)));
        command.addAll(SAFE_INPUT);
        command.addAll(List.of("-i", video.toString(), "-frames:v", "1", "-an", "-map_metadata", "-1",
                poster.toString()));
        run(command, "Video poster could not be made");
        return poster;
    }

    /**
     * A 720p (short side at most 720 px, never enlarged) H.264/AAC MP4 with fast start and no metadata. Rotation is
     * applied to the pixels, so players need no rotation hint.
     */
    public Path transcode(Path video, Path outputDirectory) throws MediaProcessingException {
        Path output = outputDirectory.resolve("display.mp4");
        List<String> command = new ArrayList<>(List.of(ffmpeg, "-v", "error", "-nostdin", "-y"));
        command.addAll(SAFE_INPUT);
        command.addAll(List.of("-i", video.toString(),
                "-t", Long.toString(MAX_DURATION_MS / 1000 + 1),
                "-map", "0:v:0", "-map", "0:a:0?",
                "-vf", "scale=w='if(gte(iw,ih),-2,min(720,iw))':h='if(gte(iw,ih),min(720,ih),-2)'",
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "128k", "-ac", "2",
                "-map_metadata", "-1", "-map_chapters", "-1",
                "-fflags", "+bitexact", "-flags:v", "+bitexact", "-flags:a", "+bitexact",
                "-movflags", "+faststart",
                output.toString()));
        run(command, "Video could not be converted");
        return output;
    }

    private String run(List<String> command, String failure) throws MediaProcessingException {
        Path log = null;
        try {
            log = Files.createTempFile("keepsake-ffmpeg-", ".log");
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(false)
                    .redirectError(log.toFile())
                    .start();
            byte[] stdout = process.getInputStream().readNBytes(256 * 1024);
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new MediaProcessingException(failure + " (timed out)");
            }
            if (process.exitValue() != 0) {
                throw new MediaProcessingException(failure);
            }
            return new String(stdout, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new MediaProcessingException(failure, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MediaProcessingException(failure + " (interrupted)", exception);
        } finally {
            if (log != null) {
                try {
                    Files.deleteIfExists(log);
                } catch (IOException ignored) {
                    // Best-effort cleanup of a temp file.
                }
            }
        }
    }
}
