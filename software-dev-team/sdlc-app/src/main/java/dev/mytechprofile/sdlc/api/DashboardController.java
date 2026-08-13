package dev.mytechprofile.sdlc.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the dashboard at the site root.
 *
 * <p><strong>When to use:</strong> open {@code http://localhost:8095/} after {@code bootRun} or
 * {@code docker compose up}.
 *
 * <p><strong>Example:</strong> {@code GET /} forwards to {@code /index.html} on the classpath.
 */
@Controller
public class DashboardController {

    /**
     * Forwards the site root to the dashboard HTML.
     *
     * @return forward to {@code /index.html}
     */
    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }
}
