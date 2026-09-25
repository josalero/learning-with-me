package dev.mytechprofile.jev.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.mytechprofile.jev.decide.InvalidResumeException;
import dev.mytechprofile.jev.decide.ResumeAssessment;
import dev.mytechprofile.jev.decide.ResumeFiles;
import dev.mytechprofile.jev.decide.ResumeSeniorityService;
import dev.mytechprofile.jev.decide.SampleResumes;

/**
 * Classifies a resume. Example: {@code POST /api/v1/seniority} with
 * {@code {"resume":"4 years of experience. Delivers features independently."}}.
 */
@RestController
@RequestMapping("/api/v1")
public class SeniorityController {

    private final ResumeSeniorityService service;

    /**
     * @param service classification and explanation pipeline
     */
    public SeniorityController(ResumeSeniorityService service) {
        this.service = service;
    }

    /**
     * Classifies one pasted resume or one text file.
     *
     * <p>Send exactly one of {@code resume} or {@code path}. A body of
     * {@code {"resume":"4 years of experience. Delivers features independently."}}
     * is {@code INTERMEDIATE} when the offline decider is active.
     *
     * @param request pasted text or a path inside the working directory
     * @return level, confidence, probabilities, review flag, explanation, and decider id
     * @throws InvalidResumeException when both fields are set, neither is set, the text is
     *     blank or larger than 64KB, or the path is illegal; the API returns 400
     * @throws dev.mytechprofile.jev.decide.JevClientException when the live decision call fails;
     *     the API returns 502 with {@code Seniority decision failed}
     */
    @PostMapping("/seniority")
    public ResumeAssessment classify(@RequestBody SeniorityRequest request) {
        return service.assess(textOf(request));
    }

    /**
     * Classifies every classpath fixture.
     * With a live key this calls Jev once per file. The lab page uses
     * {@link #sampleResumes()} instead, and classifies only when you submit.
     *
     * @return one assessment per classpath fixture, in {@link SampleResumes#all()} order
     * @throws dev.mytechprofile.jev.decide.JevClientException when a live call fails; the API returns 502
     */
    @GetMapping("/samples")
    public List<SampleAssessment> samples() {
        return SampleResumes.all().stream()
                .map(sample -> new SampleAssessment(sample.name(), service.assess(sample.text())))
                .toList();
    }

    /**
     * Returns fixture text without classifying it.
     * Example: the lab page fills the textarea from {@code GET /api/v1/sample-resumes}.
     *
     * @return classpath fixtures in {@link SampleResumes#all()} order, text only
     */
    @GetMapping("/sample-resumes")
    public List<SampleResumes.Sample> sampleResumes() {
        return SampleResumes.all();
    }

    private static String textOf(SeniorityRequest request) {
        boolean hasText = request.resume() != null && !request.resume().isBlank();
        boolean hasPath = request.path() != null && !request.path().isBlank();
        if (hasText == hasPath) {
            throw new InvalidResumeException("Send either resume text or a path, not both");
        }
        if (hasText) {
            return request.resume();
        }
        return ResumeFiles.read(request.path());
    }
}
