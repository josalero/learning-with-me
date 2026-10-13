package dev.mytechprofile.langchain122.posting;

import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.service.UserMessage;

/**
 * Pulls must-have skills from a job posting. 1.22 sends this {@link TextContent} once. 1.21 appended the same text again.
 */
public interface PostingSkills {

    String extract(@UserMessage TextContent posting);
}
