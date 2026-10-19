package dev.mytechprofile.langgraph4j.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.mytechprofile.langgraph4j.domain.Decision;
import dev.mytechprofile.langgraph4j.domain.TaskStatus;

class LabScenarioCatalogTest {

    @Test
    void all_areNumberedStepsWithWatchLists() {
        List<LabScenario> scenarios = LabScenarioCatalog.all();

        assertEquals(5, scenarios.size());
        assertEquals(List.of(1, 2, 3, 4, 5), scenarios.stream().map(LabScenario::step).toList());
        assertEquals("interrupt-only", scenarios.getFirst().id());

        Set<String> ids = new HashSet<>();
        for (LabScenario scenario : scenarios) {
            assertTrue(ids.add(scenario.id()), "duplicate id " + scenario.id());
            assertFalse(scenario.watchFor().isEmpty());
            assertFalse(scenario.title().isBlank());
            assertFalse(scenario.accent().isBlank());
            assertTrue(scenario.worksInStub());
        }

        assertEquals(Decision.APPROVE, LabScenarioCatalog.require("approve-ping").autoDecision());
        assertEquals(TaskStatus.REJECTED.name(), LabScenarioCatalog.require("reject-ping").expectedStatus());
    }

    @Test
    void require_unknownId_throws() {
        assertThrows(IllegalArgumentException.class, () -> LabScenarioCatalog.require("does-not-exist"));
    }
}
