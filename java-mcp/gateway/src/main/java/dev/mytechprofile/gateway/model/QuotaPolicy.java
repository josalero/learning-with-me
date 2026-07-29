package dev.mytechprofile.gateway.model;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.mytechprofile.gateway.config.GatewayProperties.QuotaProperties;

/**
 * Per-subject quota policy parsed from expressions such as {@code 100/1h}.
 *
 * @param perSubject raw expression; {@code null} means unlimited
 * @param capacity positive token capacity when limited
 * @param window refill window when limited
 */
public record QuotaPolicy(String perSubject, long capacity, Duration window) {

    private static final Pattern EXPRESSION =
            Pattern.compile("^(\\d+)\\s*/\\s*(\\d+)\\s*([smhd])$", Pattern.CASE_INSENSITIVE);

    public static QuotaPolicy none() {
        return new QuotaPolicy(null, 0, Duration.ZERO);
    }

    public static QuotaPolicy from(QuotaProperties properties) {
        if (properties == null || properties.perSubject() == null || properties.perSubject().isBlank()) {
            return none();
        }
        return parse(properties.perSubject().strip());
    }

    public static QuotaPolicy parse(String expression) {
        Matcher matcher = EXPRESSION.matcher(expression);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "quota expression '%s' must look like 100/1h".formatted(expression));
        }
        long capacity = Long.parseLong(matcher.group(1));
        long amount = Long.parseLong(matcher.group(2));
        Duration window = switch (matcher.group(3).toLowerCase(Locale.ROOT)) {
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            case "d" -> Duration.ofDays(amount);
            default -> throw new IllegalArgumentException("unknown quota unit in " + expression);
        };
        if (capacity <= 0 || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("quota expression '%s' must be positive".formatted(expression));
        }
        return new QuotaPolicy(expression, capacity, window);
    }

    public boolean unlimited() {
        return perSubject == null || perSubject.isBlank();
    }
}
