package dev.mytechprofile.langgraph4j.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class TaskApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void startThenRejectLeavesCursorUncalled() throws Exception {
        MvcResult started = mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brief\":\"Return ok from PingService\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING_APPROVAL"))
                .andExpect(jsonPath("$.prompt").isNotEmpty())
                .andReturn();

        String threadId = JsonPath.read(started.getResponse().getContentAsString(), "$.threadId");

        mockMvc.perform(post("/api/v1/tasks/" + threadId + "/resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void unknownThreadIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void graphReturnsMermaid() throws Exception {
        mockMvc.perform(get("/api/v1/graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mermaid").isNotEmpty());
    }

    @Test
    void scenariosListsLabRecipes() throws Exception {
        mockMvc.perform(get("/api/v1/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("interrupt-only"))
                .andExpect(jsonPath("$[2].id").value("reject-ping"))
                .andExpect(jsonPath("$[2].expectedStatus").value("REJECTED"));
    }

    @Test
    void runningRejectScenarioEndsWithoutImplementing() throws Exception {
        mockMvc.perform(post("/api/v1/scenarios/reject-ping/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void runningInterruptScenarioStopsForApproval() throws Exception {
        mockMvc.perform(post("/api/v1/scenarios/interrupt-only/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING_APPROVAL"))
                .andExpect(jsonPath("$.prompt").isNotEmpty());
    }

    @Test
    void unknownScenarioIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/scenarios/does-not-exist/run"))
                .andExpect(status().isBadRequest());
    }
}
