package dev.mytechprofile.statefulagent.session;

public final class SessionAccess {

    private static final ThreadLocal<Boolean> SYSTEM = ThreadLocal.withInitial(() -> false);

    private SessionAccess() {
    }

    public static boolean isSystem() {
        return Boolean.TRUE.equals(SYSTEM.get());
    }

    public static void runAsSystem(Runnable action) {
        boolean previous = isSystem();
        SYSTEM.set(true);
        try {
            action.run();
        }
        finally {
            if (previous) {
                SYSTEM.set(true);
            }
            else {
                SYSTEM.remove();
            }
        }
    }

    public static <T> T callAsSystem(java.util.function.Supplier<T> action) {
        boolean previous = isSystem();
        SYSTEM.set(true);
        try {
            return action.get();
        }
        finally {
            if (previous) {
                SYSTEM.set(true);
            }
            else {
                SYSTEM.remove();
            }
        }
    }
}
