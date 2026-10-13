package dev.mytechprofile.langchain122.applicationfiles;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.mytechprofile.langchain122.sample.AtsSample;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Asks the applications-folder server to read {@code app-1042.txt}, then cancels that read.
 *
 * <pre>{@code
 * Outcome outcome = CancelledToolCall.cancelApplicationRead(Files.createTempDirectory("applications-"));
 * outcome.cancellationNote();  // "requestId=3 reason=Cancelled"
 * }</pre>
 */
public final class CancelledToolCall {

    /** Pinned because this protocol path sends {@code notifications/cancelled}. */
    static final String PROTOCOL_VERSION = "2025-11-25";

    private static final Duration NOTE_TIMEOUT = Duration.ofSeconds(5);

    /**
     * What the server recorded after {@code cancel(true)}.
     *
     * @param workingDirectory the applications folder passed to the stdio transport
     * @param startedNote the application id the server had opened
     * @param cancellationNote line written when {@code notifications/cancelled} arrived
     */
    public record Outcome(Path workingDirectory, String startedNote, String cancellationNote) {}

    private CancelledToolCall() {}

    /**
     * Cancels an in-flight read of {@link AtsSample#APPLICATION_ID}.
     *
     * <p>A modern HTTP transport does not send the cancel notification. The server may still ignore it,
     * so a cancelled future proves only that the client stopped waiting.
     *
     * @param applicationsFolder directory that holds {@code app-1042.txt}
     * @return the notes the server wrote, including the reason {@code Cancelled}
     * @throws IllegalStateException when the read completes or a note never appears
     */
    public static Outcome cancelApplicationRead(Path applicationsFolder) throws Exception {
        Files.writeString(applicationsFolder.resolve(AtsSample.APPLICATION_FILE), AtsSample.APPLICATION_BODY);
        try (McpClient client = client(transport(applicationsFolder))) {
            client.listTools();
            CompletableFuture<?> call = client.executeToolAsync(
                    readApplicationRequest(),
                    InvocationContext.builder().methodName("cancelApplicationRead").build());
            String started = waitForNote(applicationsFolder.resolve("started.txt"));
            call.cancel(true);
            requireCancelled(call);
            return new Outcome(applicationsFolder, started, waitForNote(applicationsFolder.resolve("cancelled.txt")));
        }
    }

    private static StdioMcpTransport transport(Path applicationsFolder) {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return StdioMcpTransport.builder()
                .command(List.of(
                        javaBin,
                        "--enable-final-field-mutation=ALL-UNNAMED",
                        "-cp",
                        System.getProperty("java.class.path"),
                        ApplicationFileServer.class.getName()))
                .workingDirectory(applicationsFolder)
                .build();
    }

    private static McpClient client(StdioMcpTransport transport) {
        return DefaultMcpClient.builder()
                .key("application-files")
                .transport(transport)
                .protocolVersion(PROTOCOL_VERSION)
                .initializationTimeout(Duration.ofSeconds(10))
                .toolExecutionTimeout(Duration.ofSeconds(15))
                .build();
    }

    private static ToolExecutionRequest readApplicationRequest() {
        return ToolExecutionRequest.builder()
                .name(ApplicationFileServer.TOOL_NAME)
                .arguments("{\"applicationId\":\"" + AtsSample.APPLICATION_ID + "\"}")
                .build();
    }

    private static void requireCancelled(CompletableFuture<?> call) throws Exception {
        try {
            call.get(5, TimeUnit.SECONDS);
        } catch (CancellationException expected) {
            return;
        }
        throw new IllegalStateException("The cancelled read completed with the application file");
    }

    private static String waitForNote(Path file) throws Exception {
        long deadline = System.nanoTime() + NOTE_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.exists(file)) {
                return Files.readString(file).trim();
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("Timed out waiting for " + file + ". Check the server's stderr.");
    }
}
