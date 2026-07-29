package dev.mytechprofile.gateway.pipeline;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import dev.mytechprofile.gateway.connector.ToolExecutionContext;
import dev.mytechprofile.gateway.model.CompiledTool;
import dev.mytechprofile.gateway.model.GatewayException;
import dev.mytechprofile.gateway.model.ToolMode;

class ApprovalGateTest {

    @Test
    void require_failsClosedWithoutExchange() {
        ApprovalGate gate = new ApprovalGate();
        CompiledTool tool = new CompiledTool(
                "advance_candidate_stage",
                "Advance",
                null,
                "advanceCandidateStage",
                ToolMode.WRITE,
                null,
                Map.of(),
                Map.of(),
                null,
                null,
                null,
                true);

        assertThatThrownBy(() -> gate.require(tool, Map.of(), ToolExecutionContext.start(
                        new dev.mytechprofile.gateway.connector.CallerIdentity(
                                "u",
                                "t",
                                java.util.Set.of(),
                                java.util.Set.of(),
                                dev.mytechprofile.gateway.connector.AuthenticationType.NONE)),
                (ToolContext) null))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("no MCP exchange");
    }
}
