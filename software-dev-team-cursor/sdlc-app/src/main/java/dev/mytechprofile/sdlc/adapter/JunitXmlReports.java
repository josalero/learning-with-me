package dev.mytechprofile.sdlc.adapter;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Reads JUnit XML reports left by JVM test tasks so QA can see method names Gradle omits.
 *
 * <p>Looks under common report directories ({@code build/test-results}, Surefire). Agents cannot
 * see those trees through {@link FileWorkspace}; the build gate can.
 *
 * <p>Sample: {@code readExecutedTests(workspace)} returns {@code
 * UserControllerTest.createWithBlankNameReturns400}.
 */
public final class JunitXmlReports {

    private static final Logger log = LoggerFactory.getLogger(JunitXmlReports.class);
    private static final List<String> REPORT_DIRECTORIES =
            List.of("build/test-results", "target/surefire-reports", "target/failsafe-reports");
    private static final int MAX_FILES = 50;
    private static final int MAX_FILE_BYTES = 256 * 1024;

    private JunitXmlReports() {}

    /**
     * Returns {@code SimpleClass.method} names from JUnit XML under {@code workspaceRoot}.
     *
     * @param workspaceRoot project directory the test command ran in
     * @return executed tests, skipping skipped cases; empty when no reports exist
     */
    public static List<String> readExecutedTests(Path workspaceRoot) {
        if (workspaceRoot == null || !Files.isDirectory(workspaceRoot)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        int files = 0;
        for (String relative : REPORT_DIRECTORIES) {
            Path dir = workspaceRoot.resolve(relative);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(dir, 4)) {
                List<Path> xmlFiles = walk.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".xml"))
                        .filter(path ->
                                !path.getParent().getFileName().toString().equals("binary"))
                        .limit(MAX_FILES)
                        .toList();
                for (Path xml : xmlFiles) {
                    if (files >= MAX_FILES) {
                        break;
                    }
                    names.addAll(parseFile(xml));
                    files++;
                }
            } catch (IOException ex) {
                log.warn("Could not read test reports under {}: {}", dir, ex.getMessage());
            }
        }
        return names.stream().distinct().sorted().toList();
    }

    static List<String> parse(String xml) {
        if (xml == null || xml.isBlank() || !xml.contains("testcase")) {
            return List.of();
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setExpandEntityReferences(false);
            try {
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            } catch (IllegalArgumentException ignored) {
                // Some JDK XML implementations reject these attributes.
            }
            var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            NodeList cases = document.getElementsByTagName("testcase");
            List<String> names = new ArrayList<>();
            for (int i = 0; i < cases.getLength(); i++) {
                if (!(cases.item(i) instanceof Element testCase)) {
                    continue;
                }
                if (testCase.getElementsByTagName("skipped").getLength() > 0) {
                    continue;
                }
                String className = testCase.getAttribute("classname");
                String method = testCase.getAttribute("name");
                int paren = method.indexOf('(');
                if (paren >= 0) {
                    method = method.substring(0, paren);
                }
                if (method.isBlank()) {
                    continue;
                }
                int lastDot = className.lastIndexOf('.');
                String simple = lastDot < 0 ? className : className.substring(lastDot + 1);
                names.add(simple.isBlank() ? method : simple + "." + method);
            }
            return List.copyOf(names);
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            log.warn("Ignoring unreadable JUnit XML: {}", ex.getMessage());
            return List.of();
        }
    }

    private static List<String> parseFile(Path xml) {
        try {
            if (Files.size(xml) > MAX_FILE_BYTES) {
                return List.of();
            }
            return parse(Files.readString(xml, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            log.warn("Could not read {}: {}", xml, ex.getMessage());
            return List.of();
        }
    }
}
