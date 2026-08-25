package dev.mytechprofile.sdlc.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JsonPayloadsTest {

    @Test
    void firstObject_whenThinkingWrapsJson_returnsTheObject() {
        String thinking =
                """
                The diff looks fine.
                {"decision":"APPROVE","findings":[],"blockingCount":0}
                """;

        assertThat(JsonPayloads.firstObject(thinking))
                .isEqualTo("{\"decision\":\"APPROVE\",\"findings\":[],\"blockingCount\":0}");
    }

    @Test
    void firstObject_whenFencedJson_returnsInnerObject() {
        String raw =
                """
                ```json
                {"decision":"REQUEST_CHANGES","findings":[],"blockingCount":1}
                ```
                """;

        assertThat(JsonPayloads.firstObject(raw)).contains("\"REQUEST_CHANGES\"");
    }

    @Test
    void firstObject_whenBlank_returnsNull() {
        assertThat(JsonPayloads.firstObject("   ")).isNull();
        assertThat(JsonPayloads.firstObject(null)).isNull();
    }

    @Test
    void hasText_whenNullOrBlank_isFalse() {
        assertThat(JsonPayloads.hasText(null)).isFalse();
        assertThat(JsonPayloads.hasText("  ")).isFalse();
        assertThat(JsonPayloads.hasText("{\"a\":1}")).isTrue();
    }
}
