package com.learning.a2a.orchestrator.a2a.tools;

import com.learning.a2a.orchestrator.a2a.RemoteAgentClient;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Spring AI tool that lets the orchestrator LLM reach remote A2A agents.
 */
@Component
public class RemoteAgentTools {

	private final RemoteAgentClient remoteAgentClient;

	public RemoteAgentTools(RemoteAgentClient remoteAgentClient) {
		this.remoteAgentClient = remoteAgentClient;
	}

	@Tool(
			name = "send-message-to-agent",
			description = "Sends a task to a remote A2A agent and returns its response.")
	public String sendMessageToAgent(
			@ToolParam(description = "Exact name of the remote agent") String agentName,
			@ToolParam(description = "The task to perform, in natural language") String task) {
		return remoteAgentClient.sendMessage(agentName, task);
	}
}
