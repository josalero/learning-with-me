package dev.mytechprofile.sdlc.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.mytechprofile.sdlc.domain.RunCommand;
import dev.mytechprofile.sdlc.domain.RunOutcome;
import dev.mytechprofile.sdlc.orchestration.RunService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = RunController.class)
class RunControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RunService runs;

    @Test
    void start_whenBodyIsValid_returnsAcceptedPendingRun() throws Exception {
        RunCommand command = new RunCommand("default-scrum-team", "users-service-java", "Return 404");
        when(runs.start(any())).thenReturn(RunOutcome.pending("run-1", command));

        mockMvc.perform(
                        post("/api/v1/runs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"teamId\":\"default-scrum-team\",\"projectId\":\"users-service-java\",\"featureRequest\":\"Return 404\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.runId").value("run-1"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void start_whenFeatureRequestIsBlank_returnsValidationProblem() throws Exception {
        mockMvc.perform(
                        post("/api/v1/runs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"teamId\":\"default-scrum-team\",\"projectId\":\"users-service-java\",\"featureRequest\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }
}
