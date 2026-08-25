package dev.mytechprofile.sdlc.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.mytechprofile.sdlc.catalog.CatalogException;
import dev.mytechprofile.sdlc.catalog.ProjectProfile;
import dev.mytechprofile.sdlc.config.CatalogLoader;
import dev.mytechprofile.sdlc.config.CatalogWriter;
import dev.mytechprofile.sdlc.config.SdlcProperties;
import dev.mytechprofile.sdlc.config.WorkspaceSeeder;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CatalogController.class)
class CatalogControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogLoader loader;

    @MockitoBean
    private CatalogWriter writer;

    @MockitoBean
    private WorkspaceSeeder seeder;

    @MockitoBean
    private SdlcProperties properties;

    @Test
    void roleKinds_returnsDeveloper() throws Exception {
        mockMvc.perform(get("/api/v1/role-kinds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@=='DEVELOPER')]").exists());
    }

    @Test
    void teams_whenCatalogIsInvalid_returnsRfc9457Problem() throws Exception {
        when(loader.loadTeams()).thenThrow(new CatalogException("Unknown team 'nope'. Check config/teams."));

        mockMvc.perform(get("/api/v1/teams"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Catalog error"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Unknown team 'nope'. Check config/teams."));
    }

    @Test
    void reset_whenProjectExists_returnsNoContent() throws Exception {
        ProjectProfile project = new ProjectProfile(
                "users-service-java",
                Path.of("seeds/users-service-java"),
                Path.of("workspace/users-service-java"),
                "feature/",
                List.of("**/*"),
                null,
                Map.of("test", List.of("./gradlew", "test")),
                Duration.ofSeconds(30),
                null);
        when(loader.project("users-service-java")).thenReturn(project);

        mockMvc.perform(post("/api/v1/workspace/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"users-service-java\"}"))
                .andExpect(status().isNoContent());

        verify(seeder).resetWorkspace(project);
    }
}
