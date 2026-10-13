package dev.mytechprofile.langchain122.applicationfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CancelledStdioToolSendsNotificationTest {

    @TempDir
    Path applicationsFolder;

    @Test
    void cancellingTheApplicationReadNotifiesTheServerInTheApplicationsFolder() throws Exception {
        CancelledToolCall.Outcome outcome = CancelledToolCall.cancelApplicationRead(applicationsFolder);

        assertTrue(outcome.startedNote().startsWith("applicationId=app-1042 "));
        assertTrue(Pattern.compile("requestId=\\d+ reason=Cancelled")
                .matcher(outcome.cancellationNote())
                .matches());
        assertEquals(
                applicationsFolder.toRealPath().toString(),
                Path.of(Files.readString(applicationsFolder.resolve("cwd.txt")).trim()).toRealPath().toString());
    }
}
