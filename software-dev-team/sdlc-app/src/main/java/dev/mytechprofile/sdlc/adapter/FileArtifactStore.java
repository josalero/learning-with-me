package dev.mytechprofile.sdlc.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.mytechprofile.sdlc.domain.RunOutcome;
import dev.mytechprofile.sdlc.domain.StepEvent;
import dev.mytechprofile.sdlc.port.ArtifactStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Writes numbered artifacts under {@code runs/<runId>/}.
 *
 * <p>Sample: {@code store.writeJson(runId, "01-feature-brief.json", brief)}.
 */
public final class FileArtifactStore implements ArtifactStore {

    private final Path runsDir;
    private final ObjectMapper mapper;
    private final Map<String, List<StepEvent>> recordedSteps = new ConcurrentHashMap<>();

    public FileArtifactStore(Path runsDir) {
        this.runsDir = runsDir;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public Path runDirectory(String runId) {
        try {
            return Files.createDirectories(runsDir.resolve(runId));
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot create run directory for " + runId, ex);
        }
    }

    @Override
    public void writeJson(String runId, String fileName, Object value) {
        try {
            Path file = runDirectory(runId).resolve(fileName);
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), value);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot write JSON artifact " + fileName, ex);
        }
    }

    @Override
    public void writeText(String runId, String fileName, String text) {
        try {
            Files.writeString(runDirectory(runId).resolve(fileName), text == null ? "" : text, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot write text artifact " + fileName, ex);
        }
    }

    @Override
    public void writeOutcome(RunOutcome outcome) {
        writeJson(outcome.runId(), "run.json", outcome);
    }

    @Override
    public List<StepEvent> appendStep(String runId, StepEvent event) {
        List<StepEvent> list = recordedSteps.computeIfAbsent(runId, key -> new CopyOnWriteArrayList<>());
        list.add(event);
        writeJson(runId, "steps.json", new ArrayList<>(list));
        return List.copyOf(list);
    }

    @Override
    public List<StepEvent> steps(String runId) {
        List<StepEvent> list = recordedSteps.get(runId);
        return list == null ? List.of() : List.copyOf(list);
    }

    @Override
    public List<String> listFiles(String runId) {
        Path dir = runDirectory(runId);
        try (var stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot list artifacts for run " + runId, ex);
        }
    }

    @Override
    public String readText(String runId, String fileName) {
        if (fileName == null
                || fileName.isBlank()
                || fileName.contains("/")
                || fileName.contains("\\")
                || fileName.contains("..")) {
            throw new IllegalArgumentException("Artifact file name must be a plain file, not a path: " + fileName);
        }
        Path file = runDirectory(runId).resolve(fileName);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Artifact not found: " + fileName);
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot read artifact " + fileName, ex);
        }
    }
}
