package com.keepsake.hashtag;

import static org.assertj.core.api.Assertions.assertThat;

import com.keepsake.hashtag.service.Hashtags;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class HashtagsTest {

    @Test
    void findsTagsInOrderLowercasedAndDistinct() {
        assertThat(Hashtags.parse("Beach day #Summer #sun_2024 and more #summer!"))
                .containsExactly("summer", "sun_2024");
    }

    @Test
    void supportsAnyScriptAndFoldsUnicodeForms() {
        String decomposed = "#café";
        assertThat(Hashtags.parse("Tokyo #日本 " + decomposed + " #Café")).containsExactly("日本", "café");
    }

    @Test
    void onlyCountsTagsThatStartAWord() {
        assertThat(Hashtags.parse("mail me at ana#keepsake, or see &#38; and ##double")).isEmpty();
        assertThat(Hashtags.parse("(#paren) end.#dot\n#line")).containsExactly("paren", "dot", "line");
    }

    @Test
    void ignoresOverlongTagsAndCapsTheCount() {
        assertThat(Hashtags.parse("#" + "a".repeat(101))).isEmpty();
        assertThat(Hashtags.parse("#" + "a".repeat(100))).hasSize(1);
        String many = IntStream.range(0, 40).mapToObj(i -> "#t" + i).collect(Collectors.joining(" "));
        assertThat(Hashtags.parse(many)).hasSize(30).startsWith("t0").endsWith("t29");
        assertThat(Hashtags.parse(null)).isEmpty();
    }

    @Test
    void normalizesTagsFromUrlsAndSearch() {
        assertThat(Hashtags.normalizeQuery("#Summer")).contains("summer");
        assertThat(Hashtags.normalizeQuery(" Sun_2024 ")).contains("sun_2024");
        assertThat(Hashtags.normalizeQuery("two words")).isEmpty();
        assertThat(Hashtags.normalizeQuery("%")).isEmpty();
        assertThat(Hashtags.normalizeQuery("")).isEmpty();
    }
}
