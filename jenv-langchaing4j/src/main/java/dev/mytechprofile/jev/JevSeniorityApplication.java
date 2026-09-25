package dev.mytechprofile.jev;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starts the resume seniority lab on port {@code 8094}.
 *
 * <p>When {@code OPENROUTER_API_KEY} is set, Jev classifies the resume and a chat
 * model explains the label. When the key is blank, a local year count does both.
 * {@code app.demo=true} also classifies the classpath samples at startup.
 *
 * <pre>{@code
 * jenv shell 26
 * ./gradlew bootRun
 * curl -s http://localhost:8094/api/v1/samples
 * }</pre>
 */
@SpringBootApplication
public class JevSeniorityApplication {

    /**
     * Boots the servlet app. Arguments are ignored; use HTTP or {@code app.demo}.
     *
     * @param args unused command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(JevSeniorityApplication.class, args);
    }
}
