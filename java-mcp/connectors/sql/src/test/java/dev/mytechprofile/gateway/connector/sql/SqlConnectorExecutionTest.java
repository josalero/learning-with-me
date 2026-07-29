package dev.mytechprofile.gateway.connector.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import dev.mytechprofile.gateway.connector.AuthenticationType;
import dev.mytechprofile.gateway.connector.CallerIdentity;
import dev.mytechprofile.gateway.connector.ConnectionDefinition;
import dev.mytechprofile.gateway.connector.ExecutionResult;
import dev.mytechprofile.gateway.connector.OperationInvocation;
import dev.mytechprofile.gateway.connector.ToolExecutionContext;

class SqlConnectorExecutionTest {

    @Test
    void configuresNamedDataSourceAndAppliesStatementRowLimit() {
        JdbcDataSource inventory = new JdbcDataSource();
        inventory.setURL("jdbc:h2:mem:inventory;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(inventory);
        jdbc.execute("create table item(id int primary key, quantity int)");
        jdbc.update("insert into item values (1, 1), (2, 2), (3, 3)");

        ObjectMapper mapper = new ObjectMapper();
        SqlOperationRegistry registry = new SqlOperationRegistry();
        SqlConnector connector = new SqlConnector(
                registry,
                new SqlOperationValidator(),
                Map.of("inventory", inventory),
                mapper);
        ConnectionDefinition connection = new ConnectionDefinition(
                "inventory-db",
                "sql",
                URI.create("about:blank"),
                null,
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                Map.of("datasource", "inventory"));

        ObjectNode configuration = mapper.createObjectNode();
        ObjectNode operation = configuration
                .putObject("operations")
                .putObject("lowStock");
        operation.put("sql", "select id, quantity from item where quantity <= :threshold order by id");
        operation.put("maxRows", 2);
        operation.put("timeout", "PT1S");
        operation.putObject("parameters")
                .putObject("threshold")
                .put("type", "integer");

        connector.configure(connection, configuration);
        ExecutionResult result = connector.execute(
                new OperationInvocation(connection, "lowStock", Map.of("threshold", 3)),
                ToolExecutionContext.start(new CallerIdentity(
                        "test", "tenant", Set.of(), Set.of(), AuthenticationType.NONE)));

        assertThat(result).isInstanceOf(ExecutionResult.Success.class);
        assertThat(((ExecutionResult.Success) result).payload()).hasSize(2);
    }
}
