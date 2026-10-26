package dev.mytechprofile.statefulagent.platform;

import java.util.List;

public class ProviderIncompatibleException extends RuntimeException {

    private final List<String> issues;

    public ProviderIncompatibleException(List<String> issues) {
        super("Stored messages are not compatible with the selected model provider.");
        this.issues = List.copyOf(issues);
    }

    public List<String> issues() {
        return this.issues;
    }
}
