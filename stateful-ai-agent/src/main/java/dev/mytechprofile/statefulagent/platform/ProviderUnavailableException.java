package dev.mytechprofile.statefulagent.platform;

public class ProviderUnavailableException extends RuntimeException {

    public ProviderUnavailableException(String provider) {
        super("Model provider " + provider + " is not configured. Set its API key and restart with the live profile.");
    }
}
