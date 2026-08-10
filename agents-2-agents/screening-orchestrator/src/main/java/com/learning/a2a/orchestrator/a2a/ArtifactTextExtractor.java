package com.learning.a2a.orchestrator.a2a;

import java.util.List;
import java.util.stream.Collectors;

import io.a2a.spec.Artifact;
import io.a2a.spec.Part;
import io.a2a.spec.TextPart;

/**
 * Extracts plain text from A2A artifacts / parts.
 */
final class ArtifactTextExtractor {

	private ArtifactTextExtractor() {
	}

	static String fromArtifacts(List<Artifact> artifacts) {
		if (artifacts == null || artifacts.isEmpty()) {
			return "(no artifacts returned)";
		}
		return artifacts.stream()
			.map(Artifact::parts)
			.map(ArtifactTextExtractor::fromParts)
			.collect(Collectors.joining("\n"));
	}

	static String fromParts(List<Part<?>> parts) {
		if (parts == null || parts.isEmpty()) {
			return "";
		}
		return parts.stream()
			.filter(TextPart.class::isInstance)
			.map(TextPart.class::cast)
			.map(TextPart::getText)
			.collect(Collectors.joining("\n"));
	}
}
