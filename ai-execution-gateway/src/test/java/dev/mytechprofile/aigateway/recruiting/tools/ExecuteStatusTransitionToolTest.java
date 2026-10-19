package dev.mytechprofile.aigateway.recruiting.tools;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.mytechprofile.aigateway.gateway.tools.ToolExecutionContext;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import tools.jackson.databind.json.JsonMapper;

class ExecuteStatusTransitionToolTest {

    @Test
    void neverAppliesAStatusChange() {
        assertThatThrownBy(() -> new ExecuteStatusTransitionTool()
                        .execute(
                                JsonMapper.builder().build().readTree("{\"candidateId\":\"C-102\",\"to\":\"HIRED\"}"),
                                new ToolExecutionContext(UUID.randomUUID(), "acme")))
                .isInstanceOf(IllegalStateException.class);
    }
}
