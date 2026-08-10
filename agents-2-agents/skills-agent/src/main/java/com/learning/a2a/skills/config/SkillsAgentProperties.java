package com.learning.a2a.skills.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Public base URL published on the Agent Card (must be reachable by A2A clients).
 */
@ConfigurationProperties(prefix = "a2a.agent")
public record SkillsAgentProperties(String publicUrl) {

	public String normalizedPublicUrl() {
		if (publicUrl == null || publicUrl.isBlank()) {
			throw new IllegalStateException("a2a.agent.public-url must be set");
		}
		return publicUrl.endsWith("/") ? publicUrl : publicUrl + "/";
	}
}
