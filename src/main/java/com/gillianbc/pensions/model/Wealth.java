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

    /**
     * Explicit validating constructor with clear parameter order:
     * (age, pensionStart, pensionEnd, savingsStart, savingsEnd)
     */
    public Wealth(int age,
                  BigDecimal pensionStart,
                  BigDecimal pensionEnd,
                  BigDecimal savingsStart,
                  BigDecimal savingsEnd) {
        if (age < 61) {
            throw new IllegalArgumentException("age must be >= 61");
        }
        this.age = age;
        this.pensionStart = Objects.requireNonNull(pensionStart, "pensionStart must not be null");
        this.pensionEnd = Objects.requireNonNull(pensionEnd, "pensionEnd must not be null");
        this.savingsStart = Objects.requireNonNull(savingsStart, "savingsStart must not be null");
        this.savingsEnd = Objects.requireNonNull(savingsEnd, "savingsEnd must not be null");
    }

    /**
     * Convenience constructor when only starting balances are known; end balances default to zero.
     * Order: (age, savingsStart, pensionStart)
     */
    public Wealth(int age, BigDecimal savingsStart, BigDecimal pensionStart) {
        this(age,
             Objects.requireNonNull(pensionStart, "pensionStart must not be null"),
             BigDecimal.ZERO,
             Objects.requireNonNull(savingsStart, "savingsStart must not be null"),
             BigDecimal.ZERO);
    }

    /**
     * @return total starting wealth (pensionStart + savingsStart)
     */
    public BigDecimal totalStart() {
        return pensionStart.add(savingsStart);
    }

    /**
     * @return total ending wealth (pensionEnd + savingsEnd)
     */
    public BigDecimal totalEnd() {
        return pensionEnd.add(savingsEnd);
    }

}
