package dev.mytechprofile.langgraph4j.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.junit.jupiter.api.Test;

import dev.mytechprofile.langgraph4j.cursor.ImplementationPort;
import dev.mytechprofile.langgraph4j.domain.Decision;
import dev.mytechprofile.langgraph4j.domain.ImplementationResult;
import dev.mytechprofile.langgraph4j.domain.TaskStatus;

class ImplementationGraphTest {

    @Test
    void startInterruptsBeforeCursorRuns() throws GraphStateException {
        RecordingPort port = new RecordingPort(true);
        TaskOrchestrator orchestrator = orchestrator(port);

        TaskOrchestrator.TaskView view = orchestrator.start("Return ok from PingService", "", "main");

        assertEquals(TaskStatus.WAITING_APPROVAL, view.status());
        assertTrue(view.prompt().contains("Return ok from PingService"));
        assertEquals(0, port.calls.get());
    }

    @Test
    void rejectEndsWithoutCallingCursor() throws GraphStateException {
        RecordingPort port = new RecordingPort(true);
        TaskOrchestrator orchestrator = orchestrator(port);

        TaskOrchestrator.TaskView started = orchestrator.start("Return ok from PingService", "", "main");
        TaskOrchestrator.TaskView rejected = orchestrator.resume(started.threadId(), Decision.REJECT, null);

        assertEquals(TaskStatus.REJECTED, rejected.status());
        assertEquals(0, port.calls.get());
    }

    @Test
    void approveRunsImplementAndFinishesWhenVerifyPasses() throws GraphStateException {
        RecordingPort port = new RecordingPort(true);
        TaskOrchestrator orchestrator = orchestrator(port);

        TaskOrchestrator.TaskView started = orchestrator.start("Return ok from PingService", "", "main");
        TaskOrchestrator.TaskView done = orchestrator.resume(started.threadId(), Decision.APPROVE, null);

        assertEquals(TaskStatus.DONE, done.status());
        assertTrue(done.verifyPassed());
        assertEquals(1, port.calls.get());
        assertTrue(done.steps().stream().anyMatch(step -> step.startsWith("implement:")));
    }

    @Test
    void failedVerifyRetriesOnceThenFails() throws GraphStateException {
        RecordingPort port = new RecordingPort(false);
        TaskOrchestrator orchestrator = orchestrator(port);

        TaskOrchestrator.TaskView started = orchestrator.start("Return ok from PingService", "", "main");
        TaskOrchestrator.TaskView failed = orchestrator.resume(started.threadId(), Decision.APPROVE, null);

        assertEquals(TaskStatus.FAILED, failed.status());
        assertFalse(failed.verifyPassed());
        assertEquals(2, port.calls.get());
        assertEquals(1, failed.retryCount());
    }

    private static TaskOrchestrator orchestrator(ImplementationPort port) throws GraphStateException {
        MemorySaver saver = new MemorySaver();
        CompiledGraph<TaskState> graph = ImplementationGraphFactory.compile(port, saver);
        return new TaskOrchestrator(graph);
    }

    private static final class RecordingPort implements ImplementationPort {
        private final boolean pass;
        private final AtomicInteger calls = new AtomicInteger();

        private RecordingPort(boolean pass) {
            this.pass = pass;
        }

        @Override
        public ImplementationResult implement(
                dev.mytechprofile.langgraph4j.domain.ImplementationRequest request) {
            calls.incrementAndGet();
            return ImplementationResult.stub(pass ? "ok" : "broken", pass, pass ? "ok" : "failed");
        }
    }
}
