package com.learning.a2a.skills;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * A2A skills-matching server.
 *
 * <p>Package layout: {@code config} (Spring wiring), {@code domain} (pure scoring),
 * {@code tools} (Spring AI {@code @Tool} adapters).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SkillsAgentApplication {

	public static void main(String[] args) {
		SpringApplication.run(SkillsAgentApplication.class, args);
	}
}
