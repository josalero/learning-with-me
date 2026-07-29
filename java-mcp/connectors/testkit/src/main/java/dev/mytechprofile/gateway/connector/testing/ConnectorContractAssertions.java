package dev.mytechprofile.gateway.connector.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import dev.mytechprofile.gateway.connector.ConnectionDefinition;
import dev.mytechprofile.gateway.connector.Connector;
import dev.mytechprofile.gateway.connector.OperationDescriptor;

/**
 * Reusable baseline assertions for third-party connector libraries.
 */
public final class ConnectorContractAssertions {

    private ConnectorContractAssertions() {}

    /**
     * Verifies stable connector metadata and non-null, uniquely named discovery.
     *
     * @param connector connector under test
     * @param connection validated connection fixture
     * @return the discovered descriptors for connector-specific assertions
     */
    public static List<OperationDescriptor> assertDiscoveryContract(
            Connector connector, ConnectionDefinition connection) {
        assertThat(connector.type()).isNotBlank();
        assertThat(connector.capabilities()).isNotNull();
        List<OperationDescriptor> operations = connector.discover(connection);
        assertThat(operations).isNotNull().doesNotContainNull();
        assertThat(operations)
                .extracting(OperationDescriptor::operationId)
                .doesNotHaveDuplicates()
                .allMatch(name -> name != null && !name.isBlank());
        assertThat(operations)
                .extracting(OperationDescriptor::access)
                .doesNotContainNull();
        return operations;
    }
}
