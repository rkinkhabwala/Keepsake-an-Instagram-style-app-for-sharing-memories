package com.keepsake.hashtag.service;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds #hashtags in captions. A tag is 1–100 letters, digits, or underscores (any script), starting a word, and
 * is compared case-insensitively: tags are stored NFC-normalized and lowercased.
 */
public final class Hashtags {

    public static final int MAX_LENGTH = 100;
    public static final int MAX_PER_CAPTION = 30;
    private static final String TAG_CHARS = "[\\p{L}\\p{M}\\p{N}_]";
    private static final Pattern IN_TEXT = Pattern.compile(
            "(?<![\\p{L}\\p{M}\\p{N}_&#])#(" + TAG_CHARS + "{1," + MAX_LENGTH + "})(?!" + TAG_CHARS + ")");
    private static final Pattern WHOLE_TAG = Pattern.compile(TAG_CHARS + "{1," + MAX_LENGTH + "}");

    private Hashtags() {
    }

    /** The distinct normalized tags in a caption, in order of first appearance, at most 30. */
    public static List<String> parse(String caption) {
        if (caption == null || caption.indexOf('#') < 0) {
            return List.of();
        }
        Set<String> tags = new LinkedHashSet<>();
        Matcher matcher = IN_TEXT.matcher(Normalizer.normalize(caption, Normalizer.Form.NFC));
        while (matcher.find() && tags.size() < MAX_PER_CAPTION) {
            tags.add(normalize(matcher.group(1)));
        }
        return List.copyOf(tags);
    }

    /** Normalizes a tag from a URL or search box (with or without its leading #), or empty if it is not a tag. */
    public static Optional<String> normalizeQuery(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String tag = Normalizer.normalize(raw.strip(), Normalizer.Form.NFC);
        if (tag.startsWith("#")) {
            tag = tag.substring(1);
        }
        return WHOLE_TAG.matcher(tag).matches() ? Optional.of(normalize(tag)) : Optional.empty();
    }

    private static String normalize(String tag) {
        return tag.toLowerCase(Locale.ROOT);
    }
}
