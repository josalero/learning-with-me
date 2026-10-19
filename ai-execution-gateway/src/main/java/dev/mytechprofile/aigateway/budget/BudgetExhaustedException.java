package dev.mytechprofile.aigateway.budget;

/**
 * A reservation would have exceeded a configured limit. The counter is rolled back first.
 */
public class BudgetExhaustedException extends RuntimeException {

    private final BudgetDimension dimension;

    public BudgetExhaustedException(BudgetDimension dimension) {
        super(dimension.name());
        this.dimension = dimension;
    }

    public BudgetDimension dimension() {
        return dimension;
    }
}
