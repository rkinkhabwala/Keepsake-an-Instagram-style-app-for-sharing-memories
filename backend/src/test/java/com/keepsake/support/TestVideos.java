package com.keepsake.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;

/** Generates small test videos with the local FFmpeg; tests using them are skipped where FFmpeg is missing. */
public final class TestVideos {

    private TestVideos() {
    }

    public static void assumeFfmpeg() {
        Assumptions.assumeTrue(run(List.of("ffmpeg", "-version")) && run(List.of("ffprobe", "-version")),
                "FFmpeg is not installed; skipping video tests");
    }

    /**
     * A test-pattern video with a tone, tagged with a GPS location and a creation time (which processing must
     * strip). {@code format} is "mp4" or "mov".
     */
    public static byte[] generate(Path directory, int width, int height, int seconds, int fps, String format)
            throws Exception {
        Path output = directory.resolve("source-" + width + "x" + height + "-" + seconds + "." + format);
        List<String> command = new ArrayList<>(List.of("ffmpeg", "-v", "error", "-y",
                "-f", "lavfi", "-i", "testsrc=duration=" + seconds + ":size=" + width + "x" + height + ":rate=" + fps,
                "-f", "lavfi", "-i", "sine=frequency=440:duration=" + seconds,
                "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", "-c:a", "aac",
                "-metadata", "location=+40.7128-074.0060/",
                "-metadata", "creation_time=2019-07-04T10:00:00Z",
                "-f", format.equals("mov") ? "mov" : "mp4", output.toString()));
        if (!run(command)) {
            throw new IllegalStateException("ffmpeg could not generate a test video");
        }
        return Files.readAllBytes(output);
    }

    /** The JSON ffprobe reports for the file's container and stream tags and codecs. */
    public static String probe(Path file) throws Exception {
        Process process = new ProcessBuilder("ffprobe", "-v", "error", "-show_entries",
                "format=duration:format_tags:stream=codec_name,codec_type,width,height:stream_tags", "-of", "json",
                file.toString()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        process.waitFor(60, TimeUnit.SECONDS);
        return output;
    }

    private static boolean run(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            return process.waitFor(120, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception exception) {
            return false;
        }
    }
}
