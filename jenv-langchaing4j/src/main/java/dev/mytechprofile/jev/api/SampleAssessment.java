package dev.mytechprofile.jev.api;

import dev.mytechprofile.jev.decide.ResumeAssessment;

/**
 * One classpath sample and the assessment produced for it.
 *
 * <p>Returned by {@code GET /api/v1/samples}. Example element:
 * {@code {"name":"junior.txt","assessment":{"level":"JUNIOR","decider":"offline-years"}}}.
 *
 * @param name classpath file name, for example {@code junior.txt}
 * @param assessment same fields as {@code POST /api/v1/seniority}
 */
public record SampleAssessment(String name, ResumeAssessment assessment) {
}
