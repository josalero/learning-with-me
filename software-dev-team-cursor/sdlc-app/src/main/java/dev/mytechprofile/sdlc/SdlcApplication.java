package dev.mytechprofile.sdlc;

import dev.mytechprofile.sdlc.config.SdlcProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Spring Boot entry point for the SDLC orchestrator.
 *
 * <p><strong>When to use:</strong> {@code ./gradlew :sdlc-app:bootRun} from the repo root, or the
 * Docker image.
 *
 * <p><strong>Example:</strong> {@code SdlcApplication.main(args)} binds {@code sdlc.*} properties
 * then serves {@code /} and {@code /api/v1}.
 */
@SpringBootApplication
@EnableConfigurationProperties(SdlcProperties.class)
public class SdlcApplication {

    /**
     * Starts the application.
     *
     * @param args Spring arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(SdlcApplication.class, args);
    }
}
