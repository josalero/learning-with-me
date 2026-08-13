package dev.mytechprofile.sdlc.api;

import dev.mytechprofile.sdlc.config.SdlcProperties;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lab status and numbered Samples-tab recipes for the dashboard.
 *
 * <p><strong>When to use:</strong> show offline vs live mode, and list recipes a reviewer can run
 * without typing a feature request.
 *
 * <p><strong>Example:</strong> {@code GET /api/v1/status} then {@code GET /api/v1/scenarios}.
 */
@RestController
@RequestMapping("/api/v1")
public class LabController {

    private final SdlcProperties properties;

    /**
     * Creates the lab API.
     *
     * @param properties offline flag and whether an API key is present
     */
    public LabController(SdlcProperties properties) {
        this.properties = properties;
    }

    /**
     * Returns offline vs live mode without exposing secrets.
     *
     * @return status chip payload
     */
    @GetMapping("/status")
    public LabStatusView status() {
        return LabStatusView.from(properties);
    }

    /**
     * Lists built-in Samples-tab recipes in walkthrough order.
     *
     * @return scenarios, step 1 first
     */
    @GetMapping("/scenarios")
    public List<LabScenario> scenarios() {
        return LabScenarioCatalog.all();
    }
}
