package dev.mytechprofile.sdlc.domain;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tech Lead output: the AI spec the Developer must execute.
 *
 * <p>Sample: files to change include {@code UserController.java}; traceability maps AC-1 to a
 * controller test.
 */
public record AiSpec(
        String summary,
        List<String> filesToChange,
        String apiContract,
        String dataModel,
        List<String> testPlan,
        List<String> risks,
        List<TraceabilityItem> traceability) {

    public AiSpec {
        summary = Objects.requireNonNullElse(summary, "").trim();
        filesToChange = filesToChange == null ? List.of() : List.copyOf(filesToChange);
        apiContract = Objects.requireNonNullElse(apiContract, "").trim();
        dataModel = Objects.requireNonNullElse(dataModel, "").trim();
        testPlan = testPlan == null ? List.of() : List.copyOf(testPlan);
        risks = risks == null ? List.of() : List.copyOf(risks);
        traceability = traceability == null ? List.of() : List.copyOf(traceability);
    }

    public static AiSpec empty() {
        return new AiSpec("", List.of(), "", "", List.of(), List.of(), List.of());
    }

    /**
     * Returns true when every acceptance criterion id has a non-blank planned test.
     */
    public boolean covers(FeatureBrief brief) {
        if (brief == null || brief.acceptanceCriteria().isEmpty()) {
            return false;
        }
        Set<String> covered = traceability.stream()
                .filter(TraceabilityItem::isComplete)
                .map(TraceabilityItem::acceptanceCriterionId)
                .collect(Collectors.toSet());
        return brief.acceptanceCriteria().stream().map(AcceptanceCriterion::id).allMatch(covered::contains);
    }
}
