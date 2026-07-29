package dev.mytechprofile.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import dev.mytechprofile.gateway.transport.ToolRegistry;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:catalog-binding",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.flyway.enabled=false",
    "gateway.observability.audit-enabled=false"
})
class GatewayCatalogBindingTest {

    @Autowired
    ToolRegistry registry;

    @Test
    void bindsBundledCatalogAndAutoConfiguredConnectors() {
        assertThat(registry.all())
                .extracting(tool -> tool.name())
                .containsExactlyInAnyOrder(
                        "search_candidates",
                        "advance_candidate_stage",
                        "find_low_stock_products",
                        "secure_ping");
    }
}
