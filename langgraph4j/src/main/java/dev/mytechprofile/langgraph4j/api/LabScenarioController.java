package dev.mytechprofile.langgraph4j.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.mytechprofile.langgraph4j.domain.Decision;
import dev.mytechprofile.langgraph4j.graph.TaskOrchestrator;

/**
 * Lists and runs one-click lab recipes against the implementation graph.
 *
 * <pre>{@code
 * POST /api/v1/scenarios/reject-ping/run
 * }</pre>
 */
@RestController
@RequestMapping("/api/v1/scenarios")
public class LabScenarioController {

    private final TaskOrchestrator orchestrator;

    public LabScenarioController(TaskOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * Returns the ordered scenario cards for the lab UI.
     *
     * @return catalog recipes, step 1 first
     */
    @GetMapping
    public List<LabScenario> list() {
        return LabScenarioCatalog.all();
    }

    /**
     * Starts a recipe and applies its auto-decision when present.
     *
     * <p>Scenario {@code interrupt-only} only plans. {@code reject-ping} plans then rejects.
     *
     * @param id catalog id
     * @return latest checkpoint after the recipe finishes
     */
    @PostMapping("/{id}/run")
    public TaskOrchestrator.TaskView run(@PathVariable String id) {
        LabScenario scenario = LabScenarioCatalog.require(id);
        String brief = scenario.brief() == null ? "" : scenario.brief();
        TaskOrchestrator.TaskView started = orchestrator.start(brief, "", "main");
        Decision decision = scenario.autoDecision();
        if (decision == null) {
            return started;
        }
        return orchestrator.resume(started.threadId(), decision, scenario.promptOverride());
    }
}
