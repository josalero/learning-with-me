package dev.mytechprofile.sdlc.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Drops PR-review findings that only complain the truncated git diff omitted a file that tests
 * already cover.
 *
 * <p>Sample: reviewer says {@code UserController.java} is missing from the diff because Gradle
 * reports filled the 20k cap; {@link #reconcile} approves when those planned tests executed.
 */
public final class ReviewEvidence {

    private ReviewEvidence() {}

    /**
     * Approves when blocking findings are empty-diff noise and the build already ran the planned
     * tests.
     *
     * @param llm model verdict
     * @param spec planned tests
     * @param build executed tests from the gate
     * @param gitDiff text the reviewer saw
     * @return {@link ReviewVerdict#approve()} or {@code llm} with empty-diff findings removed
     */
    public static ReviewVerdict reconcile(ReviewVerdict llm, AiSpec spec, BuildResult build, String gitDiff) {
        ReviewVerdict verdict = llm == null ? ReviewVerdict.none() : llm;
        if (verdict.approved()) {
            return verdict;
        }
        AiSpec aiSpec = spec == null ? AiSpec.empty() : spec;
        BuildResult gate = build == null ? BuildResult.none() : build;
        String diff = gitDiff == null ? "" : gitDiff;
        List<ReviewFinding> kept = new ArrayList<>();
        for (ReviewFinding finding : verdict.findings()) {
            if (isEmptyDiffFinding(finding) && (allPlannedTestsRan(aiSpec, gate) || fileAppearsInDiff(finding, diff))) {
                continue;
            }
            kept.add(finding);
        }
        long blocking = kept.stream().filter(ReviewFinding::blocking).count();
        if (blocking == 0 && (allPlannedTestsRan(aiSpec, gate) || kept.isEmpty())) {
            return new ReviewVerdict(ReviewVerdict.APPROVE, kept, 0);
        }
        return new ReviewVerdict(ReviewVerdict.REQUEST_CHANGES, kept, (int) blocking);
    }

    static boolean isEmptyDiffFinding(ReviewFinding finding) {
        String rationale = finding.rationale().toLowerCase(Locale.ROOT);
        return rationale.contains("diff does not include")
                || rationale.contains("does not include any changes")
                || rationale.contains("not in the diff")
                || rationale.contains("diff is empty")
                || rationale.contains("no changes to");
    }

    private static boolean allPlannedTestsRan(AiSpec spec, BuildResult build) {
        if (!build.success() || spec.traceability().isEmpty()) {
            return false;
        }
        return spec.traceability().stream().allMatch(item -> QaEvidence.ran(item.plannedTest(), build));
    }

    private static boolean fileAppearsInDiff(ReviewFinding finding, String gitDiff) {
        String file = finding.file();
        if (file.isBlank() || gitDiff.isBlank()) {
            return false;
        }
        int slash = file.lastIndexOf('/');
        String name = slash < 0 ? file : file.substring(slash + 1);
        return gitDiff.contains(file) || gitDiff.contains(name);
    }
}
