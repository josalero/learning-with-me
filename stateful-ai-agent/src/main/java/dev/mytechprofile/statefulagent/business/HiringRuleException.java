package dev.mytechprofile.statefulagent.business;

public class HiringRuleException extends RuntimeException {

    private final String code;

    public HiringRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return this.code;
    }
}
