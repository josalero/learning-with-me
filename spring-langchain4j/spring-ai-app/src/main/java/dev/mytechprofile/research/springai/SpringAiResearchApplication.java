package dev.mytechprofile.research.springai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SpringAiResearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiResearchApplication.class, args);
    }
}
