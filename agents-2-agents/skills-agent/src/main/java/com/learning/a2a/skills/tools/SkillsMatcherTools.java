package com.learning.a2a.skills.tools;

import com.learning.a2a.skills.domain.SkillsMatchResult;
import com.learning.a2a.skills.domain.SkillsMatcher;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Spring AI tool surface over {@link SkillsMatcher}.
 *
 * <p>Keeps {@code @Tool} metadata at the framework boundary so the domain stays free of Spring AI
 * annotations.
 */
@Component
public class SkillsMatcherTools {

	private final SkillsMatcher skillsMatcher;

	public SkillsMatcherTools(SkillsMatcher skillsMatcher) {
		this.skillsMatcher = skillsMatcher;
	}

	@Tool(
			name = "match-skills",
			description = "Compares a candidate's skills against a job's required skills and returns a fit score")
	public SkillsMatchResult matchSkills(
			@ToolParam(description = "Candidate skills, comma-separated") String candidateSkills,
			@ToolParam(description = "Required job skills, comma-separated") String requiredSkills) {
		return skillsMatcher.match(candidateSkills, requiredSkills);
	}
}
