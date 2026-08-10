package com.learning.a2a.skills.config;

import com.learning.a2a.skills.tools.SkillsMatcherTools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the skills agent's ChatClient and system prompt.
 */
@Configuration
public class SkillsAiConfiguration {

	@Bean
	ChatClient skillsChatClient(ChatClient.Builder chatClientBuilder, SkillsMatcherTools skillsMatcherTools) {
		return chatClientBuilder
			.defaultSystem("""
					You are a skills-matching assistant for recruiters.
					Always use the match-skills tool to compare a candidate's skills
					against a job's required skills, then summarize the result briefly.
					""")
			.defaultTools(skillsMatcherTools)
			.build();
	}
}
