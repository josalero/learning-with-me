package dev.mytechprofile.statefulagent.platform;

public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException() {
        super("Session was not found.");
    }
}
