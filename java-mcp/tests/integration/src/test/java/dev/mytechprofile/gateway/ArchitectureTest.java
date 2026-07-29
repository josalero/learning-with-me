package dev.mytechprofile.gateway;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import dev.mytechprofile.gateway.connector.Connector;
import dev.mytechprofile.gateway.connector.openapi.OpenApiConnector;
import dev.mytechprofile.gateway.connector.sql.SqlConnector;
import dev.mytechprofile.gateway.pipeline.ToolInvocationPipeline;
import dev.mytechprofile.gateway.transport.CatalogToolCallback;

/**
 * Framework boundary tests spanning SPI, connectors, pipeline, and transport.
 *
 * <p>Requires ArchUnit 1.4.2+ for Java 26 class files (major version 70).
 *
 * <p>{@code packagesOf} imports classes from every module JAR on the test classpath.
 */
@AnalyzeClasses(
        packagesOf = {
            Connector.class,
            OpenApiConnector.class,
            SqlConnector.class,
            ToolInvocationPipeline.class,
            CatalogToolCallback.class
        },
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule connectors_are_transport_free = noClasses()
            .that()
            .resideInAPackage("dev.mytechprofile.gateway.connector..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework.ai.mcp..", "io.modelcontextprotocol..");

    @ArchTest
    static final ArchRule gateway_compilation_and_execution_are_connector_agnostic = noClasses()
            .that()
            .resideInAnyPackage(
                    "dev.mytechprofile.gateway.config..",
                    "dev.mytechprofile.gateway.pipeline..",
                    "dev.mytechprofile.gateway.transport..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "dev.mytechprofile.gateway.connector.openapi..",
                    "dev.mytechprofile.gateway.connector.sql..");

    @ArchTest
    static final ArchRule spi_does_not_depend_on_spring_ai = noClasses()
            .that()
            .resideInAPackage("dev.mytechprofile.gateway.connector")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework.ai..");
}
