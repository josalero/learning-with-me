package dev.mytechprofile.langchain122.screening;

import dev.langchain4j.data.message.ImageContent;

/**
 * Screens one application. The portfolio screenshot is an {@link ImageContent}. 1.22.0 sends that image to the model.
 */
public interface ApplicationScreen {

    /**
     * @param portfolio a diagram or work sample, never a photo of the candidate
     * @param recruiterNote what a recruiter already wrote about the resume
     * @return whether to advance, and the fit on the must-have scale
     */
    ScreenResult screen(ImageContent portfolio, String recruiterNote);
}
