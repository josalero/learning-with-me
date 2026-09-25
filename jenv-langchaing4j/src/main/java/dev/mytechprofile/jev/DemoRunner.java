package dev.mytechprofile.jev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import dev.mytechprofile.jev.decide.JevClientException;
import dev.mytechprofile.jev.decide.ResumeAssessment;
import dev.mytechprofile.jev.decide.ResumeSeniorityService;
import dev.mytechprofile.jev.decide.SampleResumes;

/**
 * Prints each classpath sample on startup so {@code bootRun} shows a result without curl.
 */
@Component
@ConditionalOnProperty(prefix = "app", name = "demo", havingValue = "true")
public class DemoRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoRunner.class);

    private final ResumeSeniorityService service;

    /**
     * @param service classifies each classpath sample
     */
    public DemoRunner(ResumeSeniorityService service) {
        this.service = service;
    }

    /**
     * Classifies every fixture from {@link SampleResumes#all()}.
     *
     * <p>Runs only when {@code app.demo=true}. Each success is logged as
     * {@code junior.txt -> JUNIOR confidence=0.99 review=false decider=offline-years}.
     * The resume body is not logged. One {@link JevClientException} is logged by file
     * name and the loop continues.
     *
     * @param args ignored
     */
    @Override
    public void run(ApplicationArguments args) {
        for (SampleResumes.Sample sample : SampleResumes.all()) {
            try {
                ResumeAssessment assessment = service.assess(sample.text());
                log.info("{} -> {} confidence={} review={} decider={}",
                        sample.name(),
                        assessment.level(),
                        assessment.confidence(),
                        assessment.needsHumanReview(),
                        assessment.decider());
            } catch (JevClientException ex) {
                log.warn("Sample classification failed for {}", sample.name());
            }
        }
    }
}
