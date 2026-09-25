package dev.mytechprofile.jev.decide;

import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline stand-in used when {@code OPENROUTER_API_KEY} is absent.
 *
 * <p>The highest {@code N years} or {@code N años} phrase picks the level:
 * under 3 is junior, 3 to 6 is intermediate, 7 or more is senior. No phrase
 * is junior. Confidence is always {@code 0.99}, so the default review threshold
 * does not flag these results. {@code Java 21} is ignored because it is not
 * followed by year/año.
 *
 * <pre>{@code
 * new YearsSeniorityDecider().decide("4 years of experience.");
 * // INTERMEDIATE, decider offline-years
 * }</pre>
 */
public final class YearsSeniorityDecider implements SeniorityDecider {

    static final String DECIDER = "offline-years";

    private static final Pattern YEARS = Pattern.compile(
            "(\\d{1,2})\\s*\\+?\\s*(?:years?|años?)",
            Pattern.CASE_INSENSITIVE);

    /**
     * Counts years in {@code resumeText} and returns a fixed-confidence decision.
     *
     * @param resumeText plain resume; a missing year phrase yields {@link Seniority#JUNIOR}
     * @return decision whose {@code decider} is {@code offline-years}
     */
    @Override
    public SeniorityDecision decide(String resumeText) {
        int years = maxYears(resumeText);
        Seniority level = levelFor(years);
        return new SeniorityDecision(level, 0.99, probabilities(level), DECIDER);
    }

    static int maxYears(String resumeText) {
        Matcher matcher = YEARS.matcher(resumeText);
        int max = -1;
        while (matcher.find()) {
            max = Math.max(max, Integer.parseInt(matcher.group(1)));
        }
        return max;
    }

    private static Seniority levelFor(int years) {
        if (years >= 7) {
            return Seniority.SENIOR;
        }
        if (years >= 3) {
            return Seniority.INTERMEDIATE;
        }
        return Seniority.JUNIOR;
    }

    private static Map<Seniority, Double> probabilities(Seniority chosen) {
        Map<Seniority, Double> probabilities = new EnumMap<>(Seniority.class);
        for (Seniority seniority : Seniority.values()) {
            probabilities.put(seniority, seniority == chosen ? 0.99 : 0.005);
        }
        return Map.copyOf(probabilities);
    }

    static String rubricSentence(Seniority level, int years) {
        String yearsLabel = years < 0
                ? "sin años explícitos"
                : years + (years == 1 ? " año" : " años");
        return switch (level) {
            case JUNIOR -> "Perfil junior (" + yearsLabel + "): menos de 3 años de experiencia profesional.";
            case INTERMEDIATE -> "Perfil intermedio (" + yearsLabel + "): entre 3 y 6 años, entrega con poca supervisión.";
            case SENIOR -> "Perfil senior (" + yearsLabel + "): 7 años o más, con diseño o mentoría.";
        };
    }

    /**
     * Spanish template sentence for a level, citing the year count found in the resume.
     *
     * <p>Used by {@code TemplateExplainer}. Example: four years and
     * {@link Seniority#INTERMEDIATE} produces a sentence that starts with
     * {@code Perfil intermedio (4 años)}.
     *
     * @param resumeText scanned only for the year phrase
     * @param level label already chosen; this method does not recompute it
     * @return one Spanish sentence
     */
    public static String rubricSentence(String resumeText, Seniority level) {
        return rubricSentence(level, maxYears(resumeText));
    }
}
