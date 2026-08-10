package com.learning.a2a.orchestrator.a2a;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

import io.a2a.A2A;
import io.a2a.client.Client;
import io.a2a.client.ClientEvent;
import io.a2a.client.TaskEvent;
import io.a2a.client.config.ClientConfig;
import io.a2a.client.transport.jsonrpc.JSONRPCTransport;
import io.a2a.client.transport.jsonrpc.JSONRPCTransportConfig;
import io.a2a.spec.A2AClientException;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Artifact;
import io.a2a.spec.Message;

import org.springframework.stereotype.Component;

/**
 * Thin A2A SDK client: resolve card → send message → wait for artifact text.
 */
@Component
public class RemoteAgentClient {

	private static final long TIMEOUT_SECONDS = 60;

	private final AgentRegistry agentRegistry;

	public RemoteAgentClient(AgentRegistry agentRegistry) {
		this.agentRegistry = agentRegistry;
	}

	public String sendMessage(String agentName, String task) {
		AgentCard agentCard = agentRegistry.get(agentName);
		CompletableFuture<String> response = new CompletableFuture<>();

		BiConsumer<ClientEvent, AgentCard> responseConsumer = (event, card) -> {
			if (!(event instanceof TaskEvent taskEvent)) {
				return;
			}
			List<Artifact> artifacts = taskEvent.getTask().getArtifacts();
			response.complete(ArtifactTextExtractor.fromArtifacts(artifacts));
		};

		try {
			Client client = Client.builder(agentCard)
				.clientConfig(new ClientConfig.Builder()
					.setAcceptedOutputModes(List.of("text"))
					.build())
				.withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
				.addConsumers(List.of(responseConsumer))
				.streamingErrorHandler(response::completeExceptionally)
				.build();

			Message message = A2A.toUserMessage(task);
			client.sendMessage(message);
			return response.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		}
		catch (A2AClientException ex) {
			throw new A2aCommunicationException(
					"A2A client failed talking to agent '%s'".formatted(agentName),
					ex);
		}
		catch (TimeoutException ex) {
			throw new A2aCommunicationException(
					"Timed out waiting for agent '%s'".formatted(agentName),
					ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new A2aCommunicationException(
					"Interrupted waiting for agent '%s'".formatted(agentName),
					ex);
		}
		catch (ExecutionException ex) {
			throw new A2aCommunicationException(
					"Remote agent '%s' failed".formatted(agentName),
					ex.getCause());
		}
	}
}
