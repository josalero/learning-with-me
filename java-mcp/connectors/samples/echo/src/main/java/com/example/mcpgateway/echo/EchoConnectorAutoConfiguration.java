package com.example.mcpgateway.echo;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Auto-registration entry point for the example external connector JAR. */
@AutoConfiguration
public class EchoConnectorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    EchoConnector echoConnector() {
        return new EchoConnector();
    }
}
