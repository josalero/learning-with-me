package dev.mytechprofile.jev.decide;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.ClassPathResource;

/**
 * Classpath resumes used by the startup demo and {@code GET /api/v1/samples}.
 *
 * <p>Order is the three level fixtures, then the rubric edges in {@code docs/samples.md}.
 * The people in those files are fictional.
 * The list is loaded once, the first time {@link #all()} is called.
 */
public final class SampleResumes {

    private SampleResumes() {
    }

    /**
     * @param name classpath file name, for example {@code junior.txt}
     * @param text full UTF-8 contents
     */
    public record Sample(String name, String text) {
    }

    /**
     * Returns the fixtures from {@code classpath:resumes/}.
     *
     * @return level fixtures first, then rubric-edge fixtures
     * @throws java.io.UncheckedIOException when a fixture file is missing from the jar
     */
    public static List<Sample> all() {
        return Cache.SAMPLES;
    }

    private static Sample load(String name) {
        ClassPathResource resource = new ClassPathResource("resumes/" + name);
        try {
            String text = resource.getContentAsString(StandardCharsets.UTF_8);
            return new Sample(name, text);
        } catch (IOException ex) {
            throw new UncheckedIOException("Missing sample resume " + name, ex);
        }
    }

    private static final class Cache {
        private static final List<Sample> SAMPLES = List.of(
                load("junior.txt"),
                load("intermediate.txt"),
                load("senior.txt"),
                load("no-years.txt"),
                load("three-years.txt"),
                load("seven-years.txt"),
                load("mixed-years.txt"),
                load("cinco-anos.txt"));
    }
}
