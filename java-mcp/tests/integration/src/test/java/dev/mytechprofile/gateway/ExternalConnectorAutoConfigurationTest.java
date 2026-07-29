package dev.mytechprofile.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.example.mcpgateway.echo.EchoConnector;
import com.example.mcpgateway.echo.EchoConnectorAutoConfiguration;

import dev.mytechprofile.gateway.connector.Connector;

class ExternalConnectorAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EchoConnectorAutoConfiguration.class));

    @Test
    void connectorOutsideGatewayPackageRegistersFromItsJarMetadata() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(EchoConnector.class);
            assertThat(context.getBeansOfType(Connector.class).values())
                    .extracting(Connector::type)
                    .containsExactly("echo");
        });
    }
}
