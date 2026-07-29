package dev.mytechprofile.gateway.connector.sql;

import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.DOTALL;

import java.util.regex.Pattern;


/**
 * Startup validation for named SQL operations (defense in depth; DB role is the real control).
 */
public class SqlOperationValidator {

    private static final Pattern SINGLE_SELECT =
            Pattern.compile("^\\s*select\\s.+$", CASE_INSENSITIVE | DOTALL);
    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(insert|update|delete|merge|truncate|drop|alter|create|grant|revoke|call|do)\\b",
            CASE_INSENSITIVE);

    /**
     * Validates a SQL operation declaration.
     *
     * @param name operation name
     * @param sql SQL text
     * @param maxRows configured max rows
     */
    public void validate(String name, String sql, Integer maxRows) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL operation '%s' is missing sql".formatted(name));
        }
        String stripped = sql.strip();
        if (stripped.replaceAll(";\\s*$", "").contains(";")) {
            throw new IllegalArgumentException(
                    "SQL operation '%s' contains more than one statement".formatted(name));
        }
        if (!SINGLE_SELECT.matcher(stripped).matches()) {
            throw new IllegalArgumentException("SQL operation '%s' is not a SELECT".formatted(name));
        }
        if (FORBIDDEN.matcher(stripped).find()) {
            throw new IllegalArgumentException(
                    "SQL operation '%s' contains a forbidden keyword".formatted(name));
        }
        if (stripped.contains("${") || stripped.contains("\" +")) {
            throw new IllegalArgumentException(
                    "SQL operation '%s' appears to interpolate identifiers".formatted(name));
        }
        if (maxRows == null || maxRows <= 0) {
            throw new IllegalArgumentException(
                    "SQL operation '%s' must declare a positive max-rows".formatted(name));
        }
    }
}
