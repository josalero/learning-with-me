package dev.mytechprofile.tokenaudit.review;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.mytechprofile.tokenaudit.Finding;
import dev.mytechprofile.tokenaudit.FindingOrigin;
import dev.mytechprofile.tokenaudit.Framework;
import dev.mytechprofile.tokenaudit.Severity;

/**
 * Builds a bounded review prompt and converts strict model output into validated,
 * explicitly AI-inferred findings.
 */
public final class SemanticReviewService {

	private static final String SYSTEM_PROMPT = """
			You are a token-efficiency auditor for Java AI applications. Review only the
			provided, line-numbered evidence. Find semantic token waste that deterministic
			rules may miss in prompts, tools, RAG, memory, and multi-agent orchestration.
			Do not repeat a deterministic finding already listed. Do not infer code that is
			not present. Use file:line locations from the evidence. Estimates are heuristic,
			so use null unless the evidence supports a defensible token count. Recommend a
			concrete change and mention behavioral validation when optimization could alter
			task success. Return only the required JSON object; do not reveal chain of thought.
			""";
	private static final Set<String> VALID_AREAS = Set.of(
			"prompts", "tools", "rag", "memory", "agents"
	);

	private final LlmReviewClient client;
	private final EvidenceCollector evidenceCollector;
	private final SemanticReviewOptions options;
	private final ObjectMapper objectMapper;

	/**
	 * Creates a semantic-review service.
	 *
	 * @param client external model client
	 * @param evidenceCollector local evidence selector and redactor
	 * @param options evidence and result limits
	 */
	public SemanticReviewService(
			LlmReviewClient client,
			EvidenceCollector evidenceCollector,
			SemanticReviewOptions options
	) {
		this(client, evidenceCollector, options, new ObjectMapper());
	}

	SemanticReviewService(
			LlmReviewClient client,
			EvidenceCollector evidenceCollector,
			SemanticReviewOptions options,
			ObjectMapper objectMapper
	) {
		this.client = Objects.requireNonNull(client, "client");
		this.evidenceCollector = Objects.requireNonNull(evidenceCollector, "evidenceCollector");
		this.options = Objects.requireNonNull(options, "options");
		this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
	}

	/**
	 * Runs one semantic review over sanitized evidence.
	 *
	 * @param projectPath audited project root
	 * @param frameworks configured framework hints
	 * @param deterministicFindings findings already produced locally
	 * @return validated AI-inferred findings and usage metadata
	 */
	public SemanticReviewResult review(
			Path projectPath,
			Set<Framework> frameworks,
			List<Finding> deterministicFindings
	) {
		Objects.requireNonNull(projectPath, "projectPath");
		Objects.requireNonNull(frameworks, "frameworks");
		Objects.requireNonNull(deterministicFindings, "deterministicFindings");

		EvidenceBundle evidence = evidenceCollector.collect(projectPath);
		if (evidence.snippets().isEmpty()) {
			throw new LlmReviewException("No AI-related source evidence was selected for LLM review");
		}

		LlmReviewRequest request = new LlmReviewRequest(
				SYSTEM_PROMPT,
				buildEvidencePrompt(frameworks, deterministicFindings, evidence),
				"token_efficiency_review",
				outputSchema(options.maxFindings())
		);
		LlmReviewResponse response = client.complete(request);
		Set<String> evidencePaths = evidence.snippets().stream()
				.map(EvidenceSnippet::relativePath)
				.collect(java.util.stream.Collectors.toSet());
		List<Finding> findings = parseFindings(response.content(), evidencePaths);
		return new SemanticReviewResult(
				findings,
				response.model(),
				response.promptTokens(),
				response.completionTokens(),
				evidence.snippets().size(),
				evidence.redactionCount(),
				evidence.truncated()
		);
	}

	private String buildEvidencePrompt(
			Set<Framework> frameworks,
			List<Finding> deterministicFindings,
			EvidenceBundle evidence
	) {
		StringBuilder prompt = new StringBuilder(evidence.characterCount() + 4_000);
		prompt.append("Framework hints: ").append(frameworks).append("\n\n");
		prompt.append("Deterministic findings already reported (do not duplicate):\n");
		if (deterministicFindings.isEmpty()) {
			prompt.append("- none\n");
		}
		else {
			deterministicFindings.stream().limit(50).forEach(finding -> prompt
					.append("- ").append(finding.id())
					.append(" @ ").append(finding.location())
					.append(": ").append(finding.message()).append('\n'));
		}
		prompt.append("\nRedacted source evidence:\n");
		for (EvidenceSnippet snippet : evidence.snippets()) {
			prompt.append("\n--- FILE: ").append(snippet.relativePath()).append(" ---\n");
			prompt.append(snippet.content()).append('\n');
		}
		prompt.append("\nReturn at most ").append(options.maxFindings())
				.append(" non-duplicate findings. Prefix every id with AI-.\n");
		return prompt.toString();
	}

	private List<Finding> parseFindings(String content, Set<String> evidencePaths) {
		try {
			JsonNode root = objectMapper.readTree(content);
			JsonNode findingsNode = root.path("findings");
			if (!findingsNode.isArray()) {
				throw new LlmReviewException("LLM review response is missing a findings array");
			}

			List<Finding> findings = new ArrayList<>();
			Set<String> ids = new LinkedHashSet<>();
			for (JsonNode node : findingsNode) {
				if (findings.size() >= options.maxFindings()) {
					break;
				}
				String id = normalizeId(requiredText(node, "id"));
				if (!ids.add(id)) {
					continue;
				}
				Severity severity = Severity.valueOf(
						requiredText(node, "severity").toUpperCase(Locale.ROOT)
				);
				String area = requiredText(node, "area").toLowerCase(Locale.ROOT);
				if (!VALID_AREAS.contains(area)) {
					throw new LlmReviewException("LLM review finding has unsupported area: " + area);
				}
				String location = requiredText(node, "location");
				if (evidencePaths.stream().noneMatch(path -> location.startsWith(path + ":"))) {
					throw new LlmReviewException(
							"LLM review finding cites a file outside the evidence bundle: " + location
					);
				}
				JsonNode estimateNode = node.path("estimatedTokens");
				if (estimateNode.isMissingNode()
						|| (!estimateNode.isNull() && !estimateNode.isIntegralNumber())) {
					throw new LlmReviewException(
							"LLM review finding has invalid estimatedTokens"
					);
				}
				Integer estimatedTokens = estimateNode.isNull() ? null : estimateNode.intValue();
				findings.add(new Finding(
						id,
						severity,
						area,
						location,
						requiredText(node, "message"),
						requiredText(node, "recommendation"),
						estimatedTokens,
						FindingOrigin.AI_INFERRED
				));
			}
			return findings;
		}
		catch (LlmReviewException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new LlmReviewException("Failed to parse structured LLM review response", ex);
		}
	}

	private static String requiredText(JsonNode node, String field) {
		String value = node.path(field).asText("");
		if (value.isBlank()) {
			throw new LlmReviewException("LLM review finding has blank field: " + field);
		}
		return value;
	}

	private static String normalizeId(String id) {
		String normalized = id.toUpperCase(Locale.ROOT)
				.replaceAll("[^A-Z0-9-]+", "-")
				.replaceAll("^-+|-+$", "");
		if (!normalized.startsWith("AI-")) {
			normalized = "AI-" + normalized;
		}
		if (normalized.length() > 80) {
			normalized = normalized.substring(0, 80);
		}
		return normalized;
	}

	private static Map<String, Object> outputSchema(int maxFindings) {
		Map<String, Object> findingProperties = new LinkedHashMap<>();
		findingProperties.put("id", Map.of(
				"type", "string",
				"description", "Stable uppercase identifier prefixed with AI-"
		));
		findingProperties.put("severity", Map.of(
				"type", "string",
				"enum", List.of("INFO", "LOW", "MEDIUM", "HIGH")
		));
		findingProperties.put("area", Map.of(
				"type", "string",
				"enum", List.of("prompts", "tools", "rag", "memory", "agents")
		));
		findingProperties.put("location", Map.of(
				"type", "string",
				"description", "Evidence file and line in path:line form"
		));
		findingProperties.put("message", Map.of("type", "string"));
		findingProperties.put("recommendation", Map.of("type", "string"));
		findingProperties.put("estimatedTokens", Map.of(
				"type", List.of("integer", "null"),
				"minimum", 0
		));

		Map<String, Object> findingSchema = new LinkedHashMap<>();
		findingSchema.put("type", "object");
		findingSchema.put("properties", findingProperties);
		findingSchema.put("required", List.copyOf(findingProperties.keySet()));
		findingSchema.put("additionalProperties", false);

		return Map.of(
				"type", "object",
				"properties", Map.of(
						"findings", Map.of(
								"type", "array",
								"maxItems", maxFindings,
								"items", findingSchema
						)
				),
				"required", List.of("findings"),
				"additionalProperties", false
		);
	}
}
