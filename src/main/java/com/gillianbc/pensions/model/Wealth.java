package com.gillianbc.pensions.model;

import lombok.Getter;
import lombok.NonNull;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable value representing wealth for a single age, including
 * pension and savings balances at the start and end of that age.
 */
@Getter
public class Wealth {

    private final int age;
    @NonNull private final BigDecimal pensionStart;
    @NonNull private final BigDecimal pensionEnd;
    @NonNull private final BigDecimal savingsStart;
    @NonNull private final BigDecimal savingsEnd;
    @NonNull private final BigDecimal taxPaid;
    /**
     * Additional ad hoc spending applied at this age (e.g., car, bathroom).
     */
    @NonNull private final BigDecimal extraSpending;

    /**
     * Explicit validating constructor with clear parameter order:
     * (age, pensionStart, pensionEnd, savingsStart, savingsEnd, taxPaid, extraSpending)
     */
    public Wealth(int age,
                  BigDecimal pensionStart,
                  BigDecimal pensionEnd,
                  BigDecimal savingsStart,
                  BigDecimal savingsEnd,
                  BigDecimal taxPaid,
                  BigDecimal extraSpending) {
        if (age < 55) {
            throw new IllegalArgumentException("age must be >= 55");
        }
        this.age = age;
        this.pensionStart = Objects.requireNonNull(pensionStart, "pensionStart must not be null");
        this.pensionEnd = Objects.requireNonNull(pensionEnd, "pensionEnd must not be null");
        this.savingsStart = Objects.requireNonNull(savingsStart, "savingsStart must not be null");
        this.savingsEnd = Objects.requireNonNull(savingsEnd, "savingsEnd must not be null");
        this.taxPaid = Objects.requireNonNull(taxPaid, "taxPaid must not be null");
        this.extraSpending = Objects.requireNonNull(extraSpending, "extraSpending must not be null");
    }



    /**
     * @return total ending wealth (pensionEnd + savingsEnd)
     */
    public BigDecimal totalEnd() {
        return pensionEnd.add(savingsEnd);
    }

}
