package dev.mytechprofile.research.langchain4j;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Langchain4jResearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(Langchain4jResearchApplication.class, args);
    }
}
