package com.learning.a2a.orchestrator.a2a;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.learning.a2a.orchestrator.config.RemoteAgentsProperties;

import io.a2a.A2A;
import io.a2a.spec.A2AClientError;
import io.a2a.spec.AgentCard;

import org.springframework.stereotype.Component;

/**
 * In-memory catalog of remote Agent Cards discovered at startup.
 */
@Component
public class AgentRegistry {

	private final Map<String, AgentCard> agentCards = new LinkedHashMap<>();

	public AgentRegistry(RemoteAgentsProperties properties) {
		if (properties.urls() == null || properties.urls().isEmpty()) {
			throw new IllegalStateException("remote.agents.urls must list at least one A2A server base URL");
		}
		for (String url : properties.urls()) {
			try {
				URI uri = URI.create(url);
				String path = uri.getPath() == null || uri.getPath().isBlank() ? "/" : uri.getPath();
				if (!path.endsWith("/")) {
					path = path + "/";
				}
				AgentCard card = A2A.getAgentCard(url, path + ".well-known/agent-card.json", null);
				agentCards.put(card.name(), card);
			}
			catch (A2AClientError ex) {
				throw new IllegalStateException(
						"Failed to load agent card from %s. Is the skills-agent running?".formatted(url),
						ex);
			}
		}
	}

	public AgentCard get(String agentName) {
		AgentCard card = agentCards.get(agentName);
		if (card == null) {
			throw new IllegalArgumentException(
					"Unknown agent '%s'. Known agents: %s".formatted(agentName, agentCards.keySet()));
		}
		return card;
	}

	public String describeAgents() {
		return agentCards.values()
			.stream()
			.map(card -> "- " + card.name() + ": " + card.description())
			.collect(Collectors.joining("\n"));
	}
}
