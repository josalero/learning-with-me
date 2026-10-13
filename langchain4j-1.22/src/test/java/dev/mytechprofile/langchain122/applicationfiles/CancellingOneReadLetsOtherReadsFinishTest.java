package dev.mytechprofile.langchain122.applicationfiles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mytechprofile.langchain122.sample.AtsSample;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CancellingOneReadLetsOtherReadsFinishTest {

    @TempDir
    Path applications;

    @Test
    void cancelForRequestSevenDoesNotSuppressRequestEight() throws Exception {
        Files.writeString(applications.resolve(AtsSample.APPLICATION_FILE), AtsSample.APPLICATION_BODY);
        List<String> sent = new CopyOnWriteArrayList<>();
        ApplicationFileServer server = new ApplicationFileServer(applications, Duration.ofMillis(300), sent::add);

        server.handle(readCall(7));
        server.handle(readCall(8));
        server.handle("""
                {"jsonrpc":"2.0","method":"notifications/cancelled","params":{"requestId":7,"reason":"Cancelled"}}""");

        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (sent.stream().noneMatch(line -> line.contains("\"id\":8")) && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        Thread.sleep(100);

        assertTrue(sent.stream().anyMatch(line -> line.contains("\"id\":8") && line.contains("Kubernetes")));
        assertFalse(sent.stream().anyMatch(line -> line.contains("\"id\":7")));
        assertTrue(Files.readString(applications.resolve("cancelled.txt")).startsWith("requestId=7 "));
    }

    private static String readCall(long id) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"tools/call\",\"params\":{\"name\":\""
                + ApplicationFileServer.TOOL_NAME + "\",\"arguments\":{\"applicationId\":\""
                + AtsSample.APPLICATION_ID + "\"}}}";
    }
}
