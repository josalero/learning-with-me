package dev.mytechprofile.gateway.connector.sql;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SqlOperationValidatorTest {

    private final SqlOperationValidator validator = new SqlOperationValidator();

    @Test
    void validate_acceptsParameterizedSelect() {
        assertThatCode(() -> validator.validate(
                        "find_low_stock_products",
                        """
                        select product_reference
                          from inventory
                         where warehouse_code = :warehouse
                           and quantity_available <= :threshold
                        """,
                        50))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_rejectsInsert() {
        assertThatThrownBy(() -> validator.validate("bad_write", "insert into inventory values (1)", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bad_write");
    }

    @Test
    void validate_rejectsMultiStatement() {
        assertThatThrownBy(() -> validator.validate(
                        "multi", "select 1; select 2", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("more than one statement");
    }

    @Test
    void validate_rejectsNonPositiveMaxRows() {
        assertThatThrownBy(() -> validator.validate("zero_rows", "select 1", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-rows");
    }
}
