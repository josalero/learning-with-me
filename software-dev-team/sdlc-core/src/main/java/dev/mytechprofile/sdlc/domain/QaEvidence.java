package dev.mytechprofile.sdlc.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Deterministic QA scoring against executed tests, so a model cannot FAIL a green build.
 *
 * <p>Sample: QA marks AC-3 FAIL with {@code missingTests=[UserControllerTest.createWithBlankNameReturns400]}
 * while JUnit XML listed that method — {@link #reconcile} turns the verdict into PASS.
 */
public final class QaEvidence {

    private QaEvidence() {}

    /**
     * Upgrades LLM rows when the build already ran the planned test for that criterion.
     *
     * <p>Does not invent PASS on a failed build. Keeps an LLM PASS when the catalog has no
     * matching name (echo-style projects with no JUnit XML).
     *
     * @param llm model verdict
     * @param brief acceptance criteria to score
     * @param spec planned tests per criterion
     * @param build gate result, including {@link BuildResult#executedTests()}
     * @return a verdict whose {@code score} and {@code decision} are derived from the rows
     */
    public static QaVerdict reconcile(QaVerdict llm, FeatureBrief brief, AiSpec spec, BuildResult build) {
        QaVerdict verdict = llm == null ? QaVerdict.none() : llm;
        FeatureBrief criteria = brief == null ? FeatureBrief.fromRequest("") : brief;
        AiSpec aiSpec = spec == null ? AiSpec.empty() : spec;
        BuildResult gate = build == null ? BuildResult.none() : build;
        if (criteria.acceptanceCriteria().isEmpty()) {
            return verdict;
        }

        List<QaResult> results = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (AcceptanceCriterion criterion : criteria.acceptanceCriteria()) {
            String planned = plannedTest(aiSpec, criterion.id());
            QaResult llmRow = row(verdict, criterion.id());
            if (gate.success() && ran(planned, gate)) {
                results.add(new QaResult(criterion.id(), QaVerdict.PASS, evidence(planned, gate)));
                continue;
            }
            if (llmRow != null && llmRow.passed()) {
                results.add(llmRow);
                continue;
            }
            results.add(llmRow != null ? llmRow : new QaResult(criterion.id(), QaVerdict.FAIL, ""));
            if (planned != null && !planned.isBlank() && !ran(planned, gate)) {
                missing.add(normalize(planned));
            }
        }
        missing.removeIf(name -> ran(name, gate));
        return new QaVerdict(QaVerdict.FAIL, 0, results, missing);
    }

    /**
     * Returns whether {@code plannedTest} appears in the executed-test catalog or build output.
     *
     * @param plannedTest traceability name such as {@code UserControllerTest.createWithBlankNameReturns400}
     * @param build gate result
     * @return {@code true} when the method ran
     */
    public static boolean ran(String plannedTest, BuildResult build) {
        if (plannedTest == null || plannedTest.isBlank() || build == null) {
            return false;
        }
        String want = normalize(plannedTest);
        for (String executed : build.executedTests()) {
            if (sameTest(want, normalize(executed))) {
                return true;
            }
        }
        return appearsInText(want, build.truncatedOutput());
    }

    static String normalize(String name) {
        String text = Objects.requireNonNullElse(name, "").trim();
        int paren = text.indexOf('(');
        if (paren >= 0) {
            text = text.substring(0, paren);
        }
        int methodDot = text.lastIndexOf('.');
        if (methodDot < 0) {
            return text;
        }
        String method = text.substring(methodDot + 1);
        String type = text.substring(0, methodDot);
        int classDot = type.lastIndexOf('.');
        String simple = classDot < 0 ? type : type.substring(classDot + 1);
        if (simple.isBlank()) {
            return method;
        }
        return simple + "." + method;
    }

    static boolean sameTest(String planned, String executed) {
        if (planned.isBlank() || executed.isBlank()) {
            return false;
        }
        if (planned.equalsIgnoreCase(executed)) {
            return true;
        }
        String left = planned.toLowerCase(Locale.ROOT);
        String right = executed.toLowerCase(Locale.ROOT);
        return right.endsWith("." + left) || left.endsWith("." + right) || right.endsWith(left);
    }

    static boolean appearsInText(String planned, String haystack) {
        if (planned.isBlank() || haystack == null || haystack.isBlank()) {
            return false;
        }
        String text = haystack.toLowerCase(Locale.ROOT);
        String want = planned.toLowerCase(Locale.ROOT);
        if (text.contains(want)) {
            return true;
        }
        int dot = want.lastIndexOf('.');
        return dot >= 0 && text.contains(want.substring(dot + 1));
    }

    private static String plannedTest(AiSpec spec, String criterionId) {
        return spec.traceability().stream()
                .filter(item -> item.acceptanceCriterionId().equals(criterionId))
                .map(TraceabilityItem::plannedTest)
                .filter(name -> !name.isBlank())
                .findFirst()
                .orElse("");
    }

    private static QaResult row(QaVerdict verdict, String criterionId) {
        return verdict.results().stream()
                .filter(result -> result.acceptanceCriterionId().equals(criterionId))
                .findFirst()
                .orElse(null);
    }

    private static String evidence(String planned, BuildResult build) {
        String want = normalize(planned);
        for (String executed : build.executedTests()) {
            if (sameTest(want, normalize(executed))) {
                return normalize(executed);
            }
        }
        return want.isBlank() ? "build succeeded" : want;
    }
}
