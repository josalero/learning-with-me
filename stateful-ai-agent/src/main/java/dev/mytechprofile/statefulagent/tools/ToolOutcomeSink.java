package dev.mytechprofile.statefulagent.tools;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class ToolOutcomeSink {

    private static final ThreadLocal<List<Failure>> FAILURES = ThreadLocal.withInitial(ArrayList::new);

    public void start() {
        FAILURES.get().clear();
    }

    public void failure(String code, String message) {
        FAILURES.get().add(new Failure(code, message));
    }

    public List<Failure> failures() {
        return List.copyOf(FAILURES.get());
    }

    public void clear() {
        FAILURES.remove();
    }

    public record Failure(String code, String message) {
    }
}
