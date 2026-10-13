package dev.mytechprofile.langchain122.applicationfiles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Stdio MCP server for one applications folder. {@code readApplication} opens {@code app-1042.txt} and waits.
 * {@code notifications/cancelled} stops the wait for that request id only; other reads still finish.
 *
 * <p>Stdout is reserved for JSON-RPC. The folder receives {@code cwd.txt}, {@code started.txt}, and
 * {@code cancelled.txt}. A test can drive one instance line by line:
 *
 * <pre>{@code
 * List<String> sent = new CopyOnWriteArrayList<>();
 * ApplicationFileServer server = new ApplicationFileServer(folder, Duration.ofMillis(300), sent::add);
 * server.handle("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",...}");
 * server.handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\",\"params\":{\"requestId\":7}}");
 * // sent never receives a result for id 7
 * }</pre>
 */
public final class ApplicationFileServer {

    static final String TOOL_NAME = "readApplication";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path folder;
    private final Duration readDelay;
    private final Consumer<String> output;
    private final Map<Long, CountDownLatch> pendingReads = new ConcurrentHashMap<>();

    /**
     * @param folder where application files are read and notes are written
     * @param readDelay how long a read waits before answering, so the client has time to cancel
     * @param output receives one JSON-RPC line per response; must be safe to call from several threads
     */
    public ApplicationFileServer(Path folder, Duration readDelay, Consumer<String> output) {
        this.folder = folder;
        this.readDelay = readDelay;
        this.output = output;
    }

    public static void main(String[] args) throws IOException {
        Path folder = Path.of("").toAbsolutePath().normalize();
        Files.writeString(folder.resolve("cwd.txt"), folder.toString());
        ApplicationFileServer server = new ApplicationFileServer(folder, Duration.ofSeconds(4), line -> {
            System.out.println(line);
            System.out.flush();
        });
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            server.handle(line);
        }
    }

    /**
     * Handles one JSON-RPC line from the client. Blank lines are ignored.
     *
     * @throws IOException when the line is not JSON or a note cannot be written
     */
    public void handle(String line) throws IOException {
        if (line.isBlank()) {
            return;
        }
        JsonNode message = JSON.readTree(line);
        String method = message.path("method").asText("");
        switch (method) {
            case "initialize" -> send(initializeResult(message.get("id")));
            case "tools/list" -> send(toolsResult(message.get("id")));
            case "tools/call" -> startRead(message);
            case "notifications/cancelled" -> cancel(message.path("params"));
            case "notifications/initialized" -> {
                // The client sends this after initialize. It has no id and expects no result.
            }
            default -> {
                if (message.hasNonNull("id")) {
                    send(error(message.get("id"), "Method not handled: " + method));
                }
            }
        }
    }

    private void startRead(JsonNode message) throws IOException {
        long id = message.get("id").asLong();
        String applicationId = message.path("params").path("arguments").path("applicationId").asText("");
        CountDownLatch cancelled = new CountDownLatch(1);
        pendingReads.put(id, cancelled);
        Files.writeString(folder.resolve("started.txt"), "applicationId=" + applicationId + " requestId=" + id);
        Thread.ofVirtual().start(() -> finishUnlessCancelled(id, applicationId, cancelled));
    }

    private void finishUnlessCancelled(long id, String applicationId, CountDownLatch cancelled) {
        try {
            if (cancelled.await(readDelay.toMillis(), TimeUnit.MILLISECONDS)) {
                return;
            }
            send(toolResult(id, Files.readString(folder.resolve(applicationId + ".txt"))));
        } catch (IOException failure) {
            System.err.println("readApplication failed for request " + id + ": " + failure.getMessage());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            pendingReads.remove(id);
        }
    }

    private void cancel(JsonNode params) throws IOException {
        long requestId = params.path("requestId").asLong(-1);
        CountDownLatch pending = pendingReads.get(requestId);
        if (pending != null) {
            pending.countDown();
        }
        Files.writeString(
                folder.resolve("cancelled.txt"),
                "requestId=" + requestId + " reason=" + params.path("reason").asText(""));
    }

    private void send(ObjectNode message) {
        try {
            output.accept(JSON.writeValueAsString(message));
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not write a JSON-RPC response", failure);
        }
    }

    private static ObjectNode initializeResult(JsonNode id) {
        ObjectNode response = base(id);
        ObjectNode result = response.putObject("result");
        result.put("protocolVersion", "2025-11-25");
        result.putObject("capabilities").putObject("tools").put("listChanged", false);
        ObjectNode server = result.putObject("serverInfo");
        server.put("name", "application-files");
        server.put("version", "1.0.0");
        return response;
    }

    private static ObjectNode toolsResult(JsonNode id) {
        ObjectNode response = base(id);
        ObjectNode tool = response.putObject("result").putArray("tools").addObject();
        tool.put("name", TOOL_NAME);
        tool.put("description", "Reads one application file from the applications folder");
        ObjectNode schema = tool.putObject("inputSchema");
        schema.put("type", "object");
        ObjectNode applicationId = schema.putObject("properties").putObject("applicationId");
        applicationId.put("type", "string");
        applicationId.put("description", "Id of the application file, without the .txt suffix");
        schema.putArray("required").add("applicationId");
        return response;
    }

    private static ObjectNode toolResult(long id, String text) {
        ObjectNode response = JSON.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        ObjectNode result = response.putObject("result");
        ObjectNode content = result.putArray("content").addObject();
        content.put("type", "text");
        content.put("text", text);
        result.put("isError", false);
        return response;
    }

    private static ObjectNode error(JsonNode id, String message) {
        ObjectNode response = base(id);
        ObjectNode error = response.putObject("error");
        error.put("code", -32601);
        error.put("message", message);
        return response;
    }

    private static ObjectNode base(JsonNode id) {
        ObjectNode response = JSON.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        return response;
    }
}
