package dev.mytechprofile.aigateway.gateway.budget;

/**
 * A reservation would have exceeded a configured limit. The counter is rolled back first.
 */
public class BudgetExhaustedException extends RuntimeException {

    private final BudgetDimension dimension;

    public BudgetExhaustedException(BudgetDimension dimension) {
        super(dimension.name());
        this.dimension = dimension;
    }

    /** The limit that would have been passed. */
    public BudgetDimension dimension() {
        return dimension;
    }
}
