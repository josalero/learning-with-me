package dev.mytechprofile.sdlc.api;

import dev.mytechprofile.sdlc.config.SdlcProperties;

/**
 * Dashboard chip for offline vs live LLM mode.
 *
 * <p>Sample: {@code {"offline":true,"llmConfigured":false,"mode":"OFFLINE"}}. Never includes the
 * API key.
 */
public record LabStatusView(boolean offline, boolean llmConfigured, String mode) {

    /**
     * Derives the chip from configuration.
     *
     * @param properties home and model settings
     * @return status without secrets
     */
    public static LabStatusView from(SdlcProperties properties) {
        boolean offline = properties.offline();
        boolean llmConfigured = properties.openrouterApiKey() != null
                && !properties.openrouterApiKey().isBlank();
        String mode;
        if (offline) {
            mode = "OFFLINE";
        } else if (llmConfigured) {
            mode = "LIVE";
        } else {
            mode = "MISSING_KEY";
        }
        return new LabStatusView(offline, llmConfigured, mode);
    }
}
