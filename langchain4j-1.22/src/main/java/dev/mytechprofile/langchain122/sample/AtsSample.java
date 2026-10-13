package dev.mytechprofile.langchain122.sample;

/**
 * One backend application used by every sample. The image is a portfolio screenshot, not a photo of a person.
 */
public final class AtsSample {

    public static final String APPLICATION_ID = "app-1042";

    public static final String RECRUITER_NOTE =
            "Partial match: Java and Spring Boot are present. Kubernetes is missing.";

    public static final String POSTING =
            "Must-have skills for the backend role: Java, Spring Boot, and Kubernetes.";

    public static final String EXTRACTED_SKILLS = "Java, Spring Boot, Kubernetes";

    /** One-pixel PNG standing in for a diagram of a Spring service. */
    public static final String PORTFOLIO_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    public static final String APPLICATION_FILE = APPLICATION_ID + ".txt";

    public static final String APPLICATION_BODY = """
            Application app-1042
            Role: backend
            Must-haves: Java, Spring Boot, Kubernetes
            """;

    private AtsSample() {}
}
