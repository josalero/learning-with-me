package dev.mytechprofile.sdlc.catalog;

import java.util.List;

/**
 * Formats a numbered catalog shopping list so operators can fix every problem at once.
 *
 * <p><strong>When to use:</strong> {@code CatalogLoader} collected more than one YAML error.
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * throw CatalogProblems.of("Team catalog has problems:", List.of("unknown kind X", "missing prompt"));
 * }</pre>
 */
public final class CatalogProblems {

    private CatalogProblems() {}

    /**
     * Builds a {@link CatalogException} whose message lists every problem.
     *
     * @param title first line
     * @param problems one line per defect
     * @return exception to throw
     */
    public static CatalogException of(String title, List<String> problems) {
        if (problems == null || problems.isEmpty()) {
            throw new IllegalArgumentException("problems must be non-empty");
        }
        StringBuilder message = new StringBuilder(title.trim()).append('\n');
        int index = 1;
        for (String problem : problems) {
            message.append(index++).append(". ").append(problem).append('\n');
        }
        return new CatalogException(message.toString().trim());
    }
}
