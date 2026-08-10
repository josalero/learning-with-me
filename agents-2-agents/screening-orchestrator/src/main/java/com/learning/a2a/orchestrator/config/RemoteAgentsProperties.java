package com.learning.a2a.orchestrator.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Base URLs of remote A2A servers used for Agent Card discovery.
 */
@ConfigurationProperties(prefix = "remote.agents")
public record RemoteAgentsProperties(List<String> urls) {
}
