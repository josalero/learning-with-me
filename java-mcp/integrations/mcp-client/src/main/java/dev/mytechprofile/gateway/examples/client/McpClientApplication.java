package dev.mytechprofile.gateway.examples.client;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpElicitation;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;

/**
 * End-to-end MCP client for the governed gateway examples.
 */
@SpringBootApplication
public class McpClientApplication {

    private static final Logger log = LoggerFactory.getLogger(McpClientApplication.class);

    private static final String[] PII_MARKERS = {
        "personalEmail", "phoneNumber", "address", "governmentIdentifier", "salaryExpectation", "notes"
    };

    public static void main(String[] args) {
        SpringApplication.run(McpClientApplication.class, args);
    }

    @Bean
    CommandLineRunner exampleRunner(
            ToolCallbackProvider toolCallbackProvider, ApprovalElicitationHandler elicitationHandler) {
        return args -> {
            ToolCallback[] callbacks = toolCallbackProvider.getToolCallbacks();
            log.info("CHECK A — discovered {} tool(s)", callbacks.length);
            Arrays.stream(callbacks).forEach(callback -> {
                var definition = callback.getToolDefinition();
                log.info(
                        "CHECK A — tool name='{}' description='{}' schema={}",
                        definition.name(),
                        definition.description(),
                        definition.inputSchema());
            });

            boolean hasSearch = Arrays.stream(callbacks)
                    .anyMatch(callback -> "search_candidates".equals(callback.getToolDefinition().name()));
            boolean hasSql = Arrays.stream(callbacks)
                    .anyMatch(callback -> "find_low_stock_products".equals(callback.getToolDefinition().name()));
            boolean hasWrite = Arrays.stream(callbacks)
                    .anyMatch(callback -> "advance_candidate_stage".equals(callback.getToolDefinition().name()));
            boolean hasSecure = Arrays.stream(callbacks)
                    .anyMatch(callback -> "secure_ping".equals(callback.getToolDefinition().name()));

            log.info(
                    "CHECK I4 — catalog has search={} sql={} write={} secure={}",
                    hasSearch,
                    hasSql,
                    hasWrite,
                    hasSecure);

            Arrays.stream(callbacks)
                    .filter(callback -> "search_candidates".equals(callback.getToolDefinition().name()))
                    .findFirst()
                    .ifPresent(callback -> {
                        String ok = callback.call("""
                                {"skill":"Java","location":"Austin","limit":5}""");
                        log.info("CHECK A — search_candidates result={}", ok);
                        assertNoPii(ok);

                        for (int i = 0; i < 4; i++) {
                            String quotaResult = callback.call("""
                                    {"skill":"Java","limit":1}""");
                            log.info("CHECK I4 — quota call {} result={}", i + 1, quotaResult);
                            if (i == 3
                                    && quotaResult != null
                                    && quotaResult.contains("quota_exceeded")) {
                                log.info("CHECK I4 — quota exceeded after 3 successful search calls");
                            }
                        }
                    });

            elicitationHandler.setApprove(true);
            Arrays.stream(callbacks)
                    .filter(callback -> "advance_candidate_stage".equals(callback.getToolDefinition().name()))
                    .findFirst()
                    .ifPresent(callback -> {
                        String accepted = callback.call("""
                                {"candidateReference":"CAND-1001","targetStage":"PHONE_SCREEN"}""");
                        log.info("CHECK I5 — approve write result={}", accepted);
                        // CallToolResult text embeds escaped JSON, so match payload markers not raw "ok":true.
                        if (accepted != null
                                && accepted.contains("PHONE_SCREEN")
                                && !accepted.contains("access_denied")) {
                            log.info("CHECK I5 — write approved and executed");
                        } else {
                            log.error("CHECK I5 FAILED — approved write did not succeed");
                        }

                        elicitationHandler.setApprove(false);
                        String declined = callback.call("""
                                {"candidateReference":"CAND-1001","targetStage":"ONSITE"}""");
                        log.info("CHECK I5 — decline write result={}", declined);
                        if (declined != null && declined.contains("access_denied")) {
                            log.info("CHECK I5 — write declined without downstream mutation");
                        } else {
                            log.error("CHECK I5 FAILED — decline path unexpected");
                        }
                    });

            Arrays.stream(callbacks)
                    .filter(callback -> "secure_ping".equals(callback.getToolDefinition().name()))
                    .findFirst()
                    .ifPresent(callback -> {
                        String ping = callback.call("{}");
                        log.info("CHECK I5 — secure_ping result={}", ping);
                        if (ping != null && ping.contains("requestId")) {
                            log.info("CHECK I5 — outbound bearer protected call succeeded");
                        } else {
                            log.error("CHECK I5 FAILED — secure_ping unexpected");
                        }
                    });

            Arrays.stream(callbacks)
                    .filter(callback -> "find_low_stock_products".equals(callback.getToolDefinition().name()))
                    .findFirst()
                    .ifPresent(callback -> {
                        String sqlResult = callback.call("""
                                {"warehouse":"AUS-1","threshold":10}""");
                        log.info("CHECK I3 — find_low_stock_products result={}", sqlResult);
                        if (sqlResult != null && sqlResult.contains("product_reference")) {
                            log.info("CHECK I3 — SQL tool returned projected inventory rows");
                        }
                    });
        };
    }

    private static void assertNoPii(String payload) {
        if (payload == null) {
            log.error("CHECK I3 FAILED — empty search_candidates payload");
            return;
        }
        for (String marker : PII_MARKERS) {
            if (payload.contains(marker)) {
                log.error("CHECK I3 FAILED — PII field '{}' present in tool output", marker);
                return;
            }
        }
        log.info("CHECK I3 — search_candidates output has no PII field names");
    }

    @Component
    static class ApprovalElicitationHandler {

        private final AtomicBoolean approve = new AtomicBoolean(true);

        void setApprove(boolean value) {
            approve.set(value);
        }

        @McpElicitation(clients = "gateway")
        ElicitResult handle(ElicitRequest request) {
            log.info("Elicitation received: {}", request.message());
            if (approve.get()) {
                return new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("approved", true));
            }
            return new ElicitResult(ElicitResult.Action.DECLINE, Map.of());
        }
    }
}
