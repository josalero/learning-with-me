package dev.mytechprofile.sdlc.domain;

import java.util.List;
import java.util.Objects;

/**
 * QA output scored against the AI Spec acceptance criteria.
 *
 * <p>Sample: {@code QaVerdict.pass(100)} or FAIL with missing tests listed. When {@code results}
 * is non-empty, {@code score} and {@code decision} are derived from those rows so a model cannot
 * mark every AC PASS and still FAIL the loop with {@code score=0}.
 */
public record QaVerdict(String decision, int score, List<QaResult> results, List<String> missingTests) {

    public static final String PASS = "PASS";
    public static final String FAIL = "FAIL";

    public QaVerdict {
        results = results == null ? List.of() : List.copyOf(results);
        missingTests = missingTests == null ? List.of() : List.copyOf(missingTests);
        decision = Objects.requireNonNullElse(decision, FAIL).trim().toUpperCase();
        if (score < 0) {
            score = 0;
        }
        if (score > 100) {
            score = 100;
        }
        if (!results.isEmpty()) {
            long passed = results.stream().filter(QaResult::passed).count();
            score = (int) Math.round(passed * 100.0 / results.size());
            boolean allPassed = passed == results.size();
            decision = allPassed && missingTests.isEmpty() ? PASS : FAIL;
        } else if (!missingTests.isEmpty()) {
            decision = FAIL;
        }
    }

    public static QaVerdict none() {
        return new QaVerdict(PASS, 100, List.of(), List.of());
    }

    public static QaVerdict pass(int score) {
        return new QaVerdict(PASS, score, List.of(), List.of());
    }

    public boolean passed(int threshold) {
        return PASS.equals(decision) && score >= threshold && missingTests.isEmpty();
    }

    public boolean failed(int threshold) {
        return !passed(threshold);
    }
}
