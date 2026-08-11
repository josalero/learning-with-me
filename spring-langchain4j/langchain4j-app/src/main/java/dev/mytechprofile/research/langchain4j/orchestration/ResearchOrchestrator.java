package dev.mytechprofile.research.langchain4j.orchestration;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.mytechprofile.research.langchain4j.agents.PlannerAgent;
import dev.mytechprofile.research.langchain4j.agents.ResearcherAgent;
import dev.mytechprofile.research.langchain4j.config.ResearchProperties;
import dev.mytechprofile.research.langchain4j.domain.Critique;
import dev.mytechprofile.research.langchain4j.domain.ResearchCommand;
import dev.mytechprofile.research.langchain4j.domain.ResearchReport;
import dev.mytechprofile.research.langchain4j.domain.StepEvent;

/**
 * Declarative LangChain4j orchestration via sequenceBuilder + loopBuilder.
 *
 * <p>planner → researcher → (writer → critic)* until score ≥ threshold or max revisions.
 */
@Service
public class ResearchOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ResearchOrchestrator.class);

    private final PlannerAgent planner;
    private final ResearcherAgent researcher;
    private final UntypedAgent reviewLoop;
    private final ReportAssembler reportAssembler;
    private final ResearchProperties properties;

    public ResearchOrchestrator(
            PlannerAgent planner,
            ResearcherAgent researcher,
            @Qualifier("reviewLoop") UntypedAgent reviewLoop,
            ReportAssembler reportAssembler,
            ResearchProperties properties) {
        this.planner = planner;
        this.researcher = researcher;
        this.reviewLoop = reviewLoop;
        this.reportAssembler = reportAssembler;
        this.properties = properties;
    }

    public ResearchReport run(ResearchCommand command) {
        return run(command, event -> {
        });
    }

    public ResearchReport run(ResearchCommand command, Consumer<StepEvent> stepListener) {
        long started = System.currentTimeMillis();
        StepCollectingListener listener = new StepCollectingListener();
        listener.addListener(stepListener);

        UntypedAgent pipeline = AgenticServices.sequenceBuilder()
                .subAgents(planner, researcher, reviewLoop)
                .outputKey("draft")
                .listener(listener)
                .build();

        String topic = command.topic().trim();
        int depth = command.depth() == null ? properties.defaultDepth() : command.depth();

        log.info("Starting research pipeline topicLength={} depth={} engine={}",
                topic.length(), depth, properties.engine());

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("topic", topic);
        inputs.put("depth", depth);
        // Writer requires critique in scope even on the first draft.
        inputs.put("critique", Critique.none());

        ResultWithAgenticScope<String> result = pipeline.invokeWithAgenticScope(inputs);
        ResearchReport report = reportAssembler.assemble(
                topic,
                result.agenticScope(),
                listener.steps(),
                started);

        log.info("Finished research pipeline elapsedMs={} criticScore={} steps={}",
                report.elapsedMs(), report.critique().score(), report.steps().size());
        return report;
    }
}
