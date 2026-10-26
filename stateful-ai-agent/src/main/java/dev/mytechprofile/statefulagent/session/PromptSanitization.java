package dev.mytechprofile.statefulagent.session;

public final class PromptSanitization {

    private static final ThreadLocal<String> PROVIDER = new ThreadLocal<>();

    private PromptSanitization() {
    }

    public static String currentProvider() {
        return PROVIDER.get();
    }

    public static void enable(String providerWireName) {
        PROVIDER.set(providerWireName);
    }

    public static void clear() {
        PROVIDER.remove();
    }
}
