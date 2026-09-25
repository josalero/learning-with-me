package dev.mytechprofile.jev.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import dev.mytechprofile.jev.decide.JevClientException;
import dev.mytechprofile.jev.decide.SeniorityDecider;

@SpringBootTest(properties = {
        "app.demo=false",
        "openrouter.api-key="
})
@AutoConfigureMockMvc
class SeniorityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pastedResumeWithOneYearIsJunior() throws Exception {
        mockMvc.perform(post("/api/v1/seniority")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resume":"Intern. 1 year of professional experience after a bootcamp."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("JUNIOR"))
                .andExpect(jsonPath("$.decider").value("offline-years"))
                .andExpect(jsonPath("$.needsHumanReview").value(false));
    }

    @Test
    void sendingBothTextAndPathIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/seniority")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resume":"4 years of experience.","path":"resumes/junior.txt"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Send either resume text or a path, not both"));
    }

    @Test
    void sampleResumesReturnTextWithoutALevel() throws Exception {
        mockMvc.perform(get("/api/v1/sample-resumes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("junior.txt"))
                .andExpect(jsonPath("$[0].text").exists())
                .andExpect(jsonPath("$[0].level").doesNotExist())
                .andExpect(jsonPath("$[2].name").value("senior.txt"))
                .andExpect(jsonPath("$[3].name").value("no-years.txt"))
                .andExpect(jsonPath("$[7].name").value("cinco-anos.txt"));
    }

    @Test
    void homePageOffersTheClassifier() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Classify resume")));
    }

    @Test
    void samplesCoverJuniorIntermediateAndSenior() throws Exception {
        String body = mockMvc.perform(get("/api/v1/samples"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("JUNIOR", "INTERMEDIATE", "SENIOR");
    }

    @Test
    void malformedJsonIsRejectedWithAFixedMessage() throws Exception {
        mockMvc.perform(post("/api/v1/seniority")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body must be JSON with resume or path"));
    }
}

@SpringBootTest(properties = {
        "app.demo=false",
        "openrouter.api-key="
})
@AutoConfigureMockMvc
class SeniorityControllerJevFailureTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SeniorityDecider decider;

    @Test
    void jevFailureReturnsBadGateway() throws Exception {
        when(decider.decide(anyString())).thenThrow(new JevClientException("OpenRouter decisions HTTP 503"));

        mockMvc.perform(post("/api/v1/seniority")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resume":"10 years of experience."}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Seniority decision failed"));
    }
}
