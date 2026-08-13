package dev.mytechprofile.sdlc.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JunitXmlReportsTest {

    @Test
    void parse_whenGradleJunitXml_returnsSimpleClassAndMethod() {
        String xml =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="dev.demo.users.UserControllerTest" tests="2">
                  <testcase name="createWithBlankNameReturns400()" classname="dev.demo.users.UserControllerTest"/>
                  <testcase name="unknownIdReturns404ProblemDetail()" classname="dev.demo.users.UserControllerTest">
                    <skipped/>
                  </testcase>
                </testsuite>
                """;

        assertThat(JunitXmlReports.parse(xml)).containsExactly("UserControllerTest.createWithBlankNameReturns400");
    }

    @Test
    void readExecutedTests_whenReportLivesUnderBuildTestResults_findsIt(@TempDir Path root) throws Exception {
        Path report = root.resolve("build/test-results/test/TEST-UserControllerTest.xml");
        Files.createDirectories(report.getParent());
        Files.writeString(
                report,
                """
                <testsuite>
                  <testcase name="createWithBlankNameReturns400" classname="UserControllerTest"/>
                </testsuite>
                """);

        List<String> tests = JunitXmlReports.readExecutedTests(root);

        assertThat(tests).containsExactly("UserControllerTest.createWithBlankNameReturns400");
    }
}
