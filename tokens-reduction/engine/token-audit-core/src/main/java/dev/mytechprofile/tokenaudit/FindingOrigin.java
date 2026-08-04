package dev.mytechprofile.tokenaudit;

/**
 * Describes how a finding was produced.
 */
public enum FindingOrigin {
	/** Produced by deterministic code or configuration analysis. */
	DETERMINISTIC,
	/** Inferred by an external language model from a bounded evidence bundle. */
	AI_INFERRED
}
