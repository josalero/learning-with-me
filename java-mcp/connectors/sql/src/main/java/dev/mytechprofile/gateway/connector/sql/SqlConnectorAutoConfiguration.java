package dev.mytechprofile.gateway.connector.sql;

import java.util.Map;

import javax.sql.DataSource;

import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configures the named, read-only SQL connector when JDBC is available.
 */
@AutoConfiguration
public class SqlConnectorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    SqlOperationRegistry sqlOperationRegistry() {
        return new SqlOperationRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    SqlOperationValidator sqlOperationValidator() {
        return new SqlOperationValidator();
    }

    @Bean
    @ConditionalOnMissingBean
    SqlConnector sqlConnector(
            SqlOperationRegistry registry,
            SqlOperationValidator validator,
            ListableBeanFactory beanFactory,
            ObjectMapper objectMapper) {
        Map<String, DataSource> dataSources = beanFactory.getBeansOfType(DataSource.class);
        return new SqlConnector(registry, validator, dataSources, objectMapper);
    }
}
