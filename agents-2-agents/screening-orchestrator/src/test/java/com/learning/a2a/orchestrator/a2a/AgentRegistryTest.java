package com.learning.a2a.orchestrator.a2a;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.learning.a2a.orchestrator.config.RemoteAgentsProperties;

import org.junit.jupiter.api.Test;

class AgentRegistryTest {

	@Test
	void constructor_whenUrlsMissing_failsFast() {
		assertThatThrownBy(() -> new AgentRegistry(new RemoteAgentsProperties(null)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("remote.agents.urls");
	}

	@Test
	void constructor_whenUrlsEmpty_failsFast() {
		assertThatThrownBy(() -> new AgentRegistry(new RemoteAgentsProperties(List.of())))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("remote.agents.urls");
	}
}
