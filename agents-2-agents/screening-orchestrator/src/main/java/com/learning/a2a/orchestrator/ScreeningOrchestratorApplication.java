package com.learning.a2a.orchestrator;

import com.learning.a2a.orchestrator.config.RemoteAgentsProperties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * A2A screening orchestrator (client) + recruiter REST API.
 *
 * <p>Package layout: {@code api} (HTTP), {@code application} (use cases), {@code a2a} (protocol
 * client), {@code config} (wiring).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableConfigurationProperties(RemoteAgentsProperties.class)
public class ScreeningOrchestratorApplication {

	public static void main(String[] args) {
		SpringApplication.run(ScreeningOrchestratorApplication.class, args);
	}
}
