package dev.mytechprofile.sdlc.api;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.mytechprofile.sdlc.config.SdlcProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = LabController.class)
class LabControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SdlcProperties properties;

    @Test
    void status_whenOffline_returnsOfflineModeWithoutAKeyField() throws Exception {
        when(properties.offline()).thenReturn(true);
        when(properties.openrouterApiKey()).thenReturn("should-not-appear");

        mockMvc.perform(get("/api/v1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offline").value(true))
                .andExpect(jsonPath("$.llmConfigured").value(true))
                .andExpect(jsonPath("$.mode").value("OFFLINE"))
                .andExpect(jsonPath("$.openrouterApiKey").doesNotExist());
    }

    @Test
    void scenarios_includeJavaDevOnlyAndHitl() throws Exception {
        mockMvc.perform(get("/api/v1/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("java-dev-only"))
                .andExpect(jsonPath("$[0].step").value(1))
                .andExpect(jsonPath("$[0].watchFor[0]").isString())
                .andExpect(jsonPath("$[*].id", hasItem("java-hitl")))
                .andExpect(jsonPath("$[*].id", hasItem("java-full-demo")));
    }

    @Test
    void status_whenLiveWithoutKey_returnsMissingKey() throws Exception {
        when(properties.offline()).thenReturn(false);
        when(properties.openrouterApiKey()).thenReturn("  ");

        mockMvc.perform(get("/api/v1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("MISSING_KEY"))
                .andExpect(jsonPath("$.llmConfigured").value(false));
    }
}
