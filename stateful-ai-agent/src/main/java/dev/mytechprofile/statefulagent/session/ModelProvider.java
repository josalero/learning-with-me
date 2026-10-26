package dev.mytechprofile.statefulagent.session;

public enum ModelProvider {

    STUB("stub"),
    STUB_FOREIGN("stub-foreign"),
    OPENROUTER("openai"),
    DEEPSEEK("deepseek");

    private final String wireName;

    ModelProvider(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return this.wireName;
    }
}
