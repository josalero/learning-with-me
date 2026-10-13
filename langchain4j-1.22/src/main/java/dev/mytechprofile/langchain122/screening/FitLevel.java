package dev.mytechprofile.langchain122.screening;

import dev.langchain4j.model.output.structured.Description;

/**
 * How close an application is to the must-haves, from lowest to highest.
 * {@code @Description} is the text the model sees. The constant name stays the label.
 */
public enum FitLevel {
    @Description("Misses a must-have and should not advance")
    NO_MATCH,

    @Description("Meets some must-haves and misses one")
    PARTIAL,

    @Description("Meets the must-haves named in the posting")
    STRONG
}
