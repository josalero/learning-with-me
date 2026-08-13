package dev.mytechprofile.sdlc.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class LabScenarioCatalogTest {

    @Test
    void all_areNumberedStepsWithWatchLists() {
        List<LabScenario> scenarios = LabScenarioCatalog.all();
        assertThat(scenarios).hasSize(6);
        assertThat(scenarios).extracting(LabScenario::id).doesNotHaveDuplicates();
        assertThat(scenarios).extracting(LabScenario::step).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(scenarios)
                .extracting(LabScenario::teamId)
                .contains("dev-only", "lean-pair", "default-scrum-team", "hitl-lean");
        assertThat(scenarios).extracting(LabScenario::projectId).contains("users-service-java", "users-service-node");
        assertThat(scenarios.getFirst().id()).isEqualTo("java-dev-only");
        assertThat(scenarios).allSatisfy((scenario) -> {
            assertThat(scenario.watchFor()).isNotEmpty();
            assertThat(scenario.accent()).isNotBlank();
            assertThat(scenario.track()).isNotBlank();
        });
    }
}
