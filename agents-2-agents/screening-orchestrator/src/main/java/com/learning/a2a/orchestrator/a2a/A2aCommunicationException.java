package com.learning.a2a.orchestrator.a2a;

/**
 * Checked-style runtime failure when talking to a remote A2A agent.
 */
public class A2aCommunicationException extends RuntimeException {

	public A2aCommunicationException(String message, Throwable cause) {
		super(message, cause);
	}

	public A2aCommunicationException(String message) {
		super(message);
	}
}
