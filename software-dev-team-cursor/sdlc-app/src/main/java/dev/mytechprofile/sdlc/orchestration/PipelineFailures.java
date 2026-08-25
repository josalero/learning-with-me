package dev.mytechprofile.sdlc.orchestration;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Turns LangChain4j wrapper exceptions into a short message the Results tab can show.
 *
 * <p>Sample: {@code AgentInvocationException(UntypedAgent.invoke)} wrapping {@code
 * OutputParsingException} becomes {@code "Pipeline failed at pr-reviewer: Failed to parse..."}.
 */
final class PipelineFailures {

    private static final int MAX_DETAIL_CHARS = 400;

    private PipelineFailures() {}

    /**
     * Returns a user-facing pipeline error that names the inner agent when possible.
     *
     * @param ex thrown from {@code pipeline.invokeWithAgenticScope}
     * @return one-line message
     */
    static String userMessage(Throwable ex) {
        String agent = namedAgent(ex);
        String detail = truncate(messageOf(rootCause(ex)));
        if (agent != null) {
            return "Pipeline failed at " + agent + ": " + detail + ". Check runs/<id>/run.json and the Results tab.";
        }
        return "Pipeline failed: " + detail + ". Check runs/<id>/run.json and the Results tab.";
    }

    static Throwable rootCause(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current && isWrapper(current)) {
            current = current.getCause();
        }
        while (current.getCause() != null && current.getCause() != current && isWrapper(current.getCause())) {
            current = current.getCause();
        }
        if (isWrapper(current) && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    static String namedAgent(Throwable ex) {
        Throwable current = ex;
        String found = null;
        while (current != null) {
            String parsed = parseAgent(current.getMessage());
            if (parsed != null && !"UntypedAgent".equals(parsed)) {
                found = parsed;
            }
            current = current.getCause();
        }
        return found;
    }

    private static boolean isWrapper(Throwable ex) {
        if (ex instanceof InvocationTargetException
                || ex instanceof CompletionException
                || ex instanceof ExecutionException) {
            return true;
        }
        String name = ex.getClass().getSimpleName();
        if ("UndeclaredThrowableException".equals(name) || "AgentInvocationException".equals(name)) {
            return true;
        }
        String message = ex.getMessage();
        return message != null && message.startsWith("Failed to invoke agent method:");
    }

    private static String parseAgent(String message) {
        if (message == null || !message.contains("Failed to invoke agent method:")) {
            return null;
        }
        int paren = message.indexOf('(');
        String withoutArgs = paren > 0 ? message.substring(0, paren) : message;
        int methodDot = withoutArgs.lastIndexOf('.');
        if (methodDot < 0) {
            return null;
        }
        String qualified = withoutArgs.substring(0, methodDot);
        int simpleStart = Math.max(qualified.lastIndexOf('.'), qualified.lastIndexOf(' ')) + 1;
        if (simpleStart <= 0 || simpleStart >= qualified.length()) {
            return null;
        }
        String simple = qualified.substring(simpleStart).trim();
        if (simple.isBlank()) {
            return null;
        }
        return switch (simple) {
            case "PrReviewerAgent" -> "pr-reviewer";
            case "QaAgent" -> "qa";
            case "DeveloperAgent" -> "developer";
            case "TechLeadAgent" -> "tech-lead";
            case "ProductOwnerAgent" -> "product-owner";
            case "StakeholderAgent" -> "stakeholder";
            default -> simple;
        };
    }

    private static String messageOf(Throwable ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.replace('\n', ' ').trim();
    }

    private static String truncate(String text) {
        if (text.length() <= MAX_DETAIL_CHARS) {
            return text;
        }
        return text.substring(0, MAX_DETAIL_CHARS - 3) + "...";
    }
}
