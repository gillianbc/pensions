package com.gillianbc.pensions.service;

import com.gillianbc.pensions.model.Wealth;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

@Service
public class PensionService {

    private static final MathContext MATH_CONTEXT = new MathContext(12, RoundingMode.HALF_UP);
    // Annual pension growth rate (e.g., 0.04 = 4% per year), applied at end of each year
    private static final BigDecimal PENSION_GROWTH_RATE = new BigDecimal("0.04");

    /**
     * Strategy 1: Use up savings first, then take a 25% tax free lump sum from pensions, then drawdown the remaining pension
     * 
     * If the required amount is greater than the savings, then the savings is reduced by the required amount.
     * Otherwise, a lump sum of 25% of the pension is transferred to the savings account before the required amount is paid.
     * The lump sum transfer from pension to savings can only be done once.
     * If the savings are insufficient to cover the required amount, then any remaining savings are used first and the
     * remainder of the required amount is paid out of the pension.
     * The withdrawals from the pension are subject to tax at 20% if the total income from pension
     * and state pension exceeds £12,570.00 i.e. the first 12570 is tax free, the remainder is taxed at 20%.
     * The state pension will be paid at age 67 onwards and is £11,973 per year.
     *
     * @param savings
     * @param pension
     * @param requiredAmount
     * @param years
     */
    /**
     * Strategy 1: Use up savings first, then take a 25% tax free lump sum from pensions, then drawdown the remaining pension
     *
     * If the required amount is greater than the savings, then the savings is reduced by the required amount.
     * Otherwise, a lump sum of 25% of the pension is transferred to the savings account before the required amount is paid.
     * The lump sum transfer from pension to savings can only be done once.
     * If the savings are insufficient to cover the required amount, then any remaining savings are used first and the
     * remainder of the required amount is paid out of the pension.
     * The withdrawals from the pension are subject to tax at 20% if the total income from pension
     * and state pension exceeds £12,570.00 i.e. the first 12570 is tax free, the remainder is taxed at 20%.
     * The state pension will be paid at age 67 onwards and is £11,973 per age.
     * The pension pot is expected to grow at about 4% above inflation  
     *
     * @param savings         starting savings balance (>= 0)
     * @param pension         starting pension balance (>= 0)
     * @param requiredAmount  required net withdrawal per year (>= 0)
     * @return array of Wealth objects, one per age from 61 through 99 inclusive
     */
    public Wealth[] strategy1(BigDecimal savings, BigDecimal pension, BigDecimal requiredAmount) {
        Objects.requireNonNull(savings, "savings must not be null");
        Objects.requireNonNull(pension, "pension must not be null");
        Objects.requireNonNull(requiredAmount, "requiredAmount must not be null");
        if (savings.signum() < 0) {
            throw new IllegalArgumentException("savings must be >= 0");
        }
        if (pension.signum() < 0) {
            throw new IllegalArgumentException("pension must be >= 0");
        }
        if (requiredAmount.signum() < 0) {
            throw new IllegalArgumentException("requiredAmount must be >= 0");
        }

        final BigDecimal PERSONAL_ALLOWANCE = new BigDecimal("12570.00");
        final BigDecimal STATE_PENSION = new BigDecimal("11973.00");
        final BigDecimal BASIC_RATE = new BigDecimal("0.20");
        final BigDecimal ONE = BigDecimal.ONE;

        final int startAge = 61;
        final int endAge = 99;
        final int len = endAge - startAge + 1;

        Wealth[] timeline = new Wealth[len];

        boolean lumpSumTaken = false;

        int age = startAge;
        for (int idx = 0; idx < len; idx++, age++) {
            // Snapshot start-of-year balances
            BigDecimal pensionStart = pension.setScale(2, RoundingMode.HALF_UP);
            BigDecimal savingsStart = savings.setScale(2, RoundingMode.HALF_UP);
            BigDecimal taxPaidThisYear = BigDecimal.ZERO;

            // State pension from age 67
            BigDecimal statePensionIncome = age >= 67 ? STATE_PENSION : BigDecimal.ZERO;

            // Net amount still required after state pension
            BigDecimal need = requiredAmount.subtract(statePensionIncome);
            if (need.signum() < 0) {
                need = BigDecimal.ZERO;
            }

            // Use savings first
            BigDecimal fromSavings = need.min(savings);
            savings = savings.subtract(fromSavings);
            need = need.subtract(fromSavings);

            // Take a one-time 25% tax-free lump sum from pension if still needed
            if (need.signum() > 0 && !lumpSumTaken && pension.signum() > 0) {
                BigDecimal lump = pension.multiply(new BigDecimal("0.25"), MATH_CONTEXT).setScale(2, RoundingMode.HALF_UP);
                pension = pension.subtract(lump);
                savings = savings.add(lump);
                lumpSumTaken = true;

                // Spend the newly-added savings
                BigDecimal fromSavings2 = need.min(savings);
                savings = savings.subtract(fromSavings2);
                need = need.subtract(fromSavings2);
            }

            // If still needed, withdraw from pension, applying tax rules
            if (need.signum() > 0 && pension.signum() > 0) {
                // Personal allowance remaining this year after state pension
                BigDecimal allowanceLeft = PERSONAL_ALLOWANCE.subtract(statePensionIncome);
                if (allowanceLeft.signum() < 0) {
                    allowanceLeft = BigDecimal.ZERO;
                }

                // Determine gross withdrawal needed to achieve 'need' net
                BigDecimal grossRequired;
                if (need.compareTo(allowanceLeft) <= 0) {
                    // Entirely covered by remaining personal allowance (0% tax)
                    grossRequired = need;
                } else {
                    // Part covered by allowance; remainder taxed at 20% (net = 80% of gross above allowance)
                    BigDecimal remainingNet = need.subtract(allowanceLeft);
                    BigDecimal grossAbove = remainingNet.divide(ONE.subtract(BASIC_RATE), MATH_CONTEXT); // divide by 0.8
                    grossRequired = allowanceLeft.add(grossAbove);
                }

                // Can't withdraw more than available pension
                BigDecimal grossWithdraw = grossRequired.min(pension).setScale(2, RoundingMode.HALF_UP);

                // Compute net received from the gross withdrawal
                BigDecimal zeroTaxPortion = grossWithdraw.min(allowanceLeft);
                BigDecimal basicTaxPortion = grossWithdraw.subtract(zeroTaxPortion);
                if (basicTaxPortion.signum() < 0) {
                    basicTaxPortion = BigDecimal.ZERO;
                }
                BigDecimal netFromPension = zeroTaxPortion.add(
                        basicTaxPortion.multiply(ONE.subtract(BASIC_RATE), MATH_CONTEXT)
                );

                // Compute tax paid this year on pension withdrawals
                BigDecimal taxForThisWithdrawal = basicTaxPortion.multiply(BASIC_RATE, MATH_CONTEXT).setScale(2, RoundingMode.HALF_UP);
                taxPaidThisYear = taxPaidThisYear.add(taxForThisWithdrawal);

                // Reduce need by the net amount achieved
                need = need.subtract(netFromPension);
                if (need.signum() < 0) {
                    need = BigDecimal.ZERO;
                }

                // Deduct gross from pension
                pension = pension.subtract(grossWithdraw);
            }

            // Apply end-of-year pension growth (see PENSION_GROWTH_RATE)
            pension = pension.multiply(BigDecimal.ONE.add(PENSION_GROWTH_RATE, MATH_CONTEXT), MATH_CONTEXT);

            // End-of-year snapshot
            BigDecimal pensionEnd = pension.setScale(2, RoundingMode.HALF_UP);
            BigDecimal savingsEnd = savings.setScale(2, RoundingMode.HALF_UP);
            timeline[idx] = new Wealth(age, pensionStart, pensionEnd, savingsStart, savingsEnd, taxPaidThisYear.setScale(2, RoundingMode.HALF_UP));
        }

        return timeline;
    }

    /**
     * Strategy 2: Spend savings first. When savings are depleted, draw from pension where
     * 25% of each pension withdrawal is tax-free and the remaining 75% is taxed at 20%.
     *
     * State pension is received from age 67 and offsets the required net withdrawal.
     * Pension grows at PENSION_GROWTH_RATE at the end of each age.
     *
     * @param savings         starting savings balance (>= 0)
     * @param pension         starting pension balance (>= 0)
     * @param requiredAmount  required net withdrawal per year (>= 0)
     * @return array of Wealth objects, one per age from 61 through 99 inclusive
     */
    public Wealth[] strategy2(BigDecimal savings, BigDecimal pension, BigDecimal requiredAmount) {
        Objects.requireNonNull(savings, "savings must not be null");
        Objects.requireNonNull(pension, "pension must not be null");
        Objects.requireNonNull(requiredAmount, "requiredAmount must not be null");
        if (savings.signum() < 0) {
            throw new IllegalArgumentException("savings must be >= 0");
        }
        if (pension.signum() < 0) {
            throw new IllegalArgumentException("pension must be >= 0");
        }
        if (requiredAmount.signum() < 0) {
            throw new IllegalArgumentException("requiredAmount must be >= 0");
        }

        final BigDecimal PERSONAL_ALLOWANCE = new BigDecimal("12570.00");
        final BigDecimal STATE_PENSION = new BigDecimal("11973.00");
        final BigDecimal BASIC_RATE = new BigDecimal("0.20");
        final BigDecimal TAX_FREE_PORTION = new BigDecimal("0.25");
        final BigDecimal TAXED_PORTION = new BigDecimal("0.75");
        final BigDecimal NET_FACTOR = TAX_FREE_PORTION.add(TAXED_PORTION.multiply(BigDecimal.ONE.subtract(BASIC_RATE), MATH_CONTEXT)); // 0.25 + 0.75*0.8 = 0.85
        final int startAge = 61;
        final int endAge = 99;
        final int len = endAge - startAge + 1;

        Wealth[] timeline = new Wealth[len];

        int age = startAge;
        for (int idx = 0; idx < len; idx++, age++) {
            // Snapshot start-of-year balances
            BigDecimal pensionStart = pension.setScale(2, RoundingMode.HALF_UP);
            BigDecimal savingsStart = savings.setScale(2, RoundingMode.HALF_UP);
            BigDecimal taxPaidThisYear = BigDecimal.ZERO;

            // State pension from age 67
            BigDecimal statePensionIncome = age >= 67 ? STATE_PENSION : BigDecimal.ZERO;

            // Net amount still required after state pension
            BigDecimal need = requiredAmount.subtract(statePensionIncome);
            if (need.signum() < 0) {
                need = BigDecimal.ZERO;
            }

            // Use savings first
            BigDecimal fromSavings = need.min(savings);
            savings = savings.subtract(fromSavings);
            need = need.subtract(fromSavings);

            // If still needed, withdraw from pension, with 25% tax-free and 75% taxed at 20% after personal allowance
            if (need.signum() > 0 && pension.signum() > 0) {
                // Remaining personal allowance after state pension
                BigDecimal allowanceLeft = PERSONAL_ALLOWANCE.subtract(statePensionIncome);
                if (allowanceLeft.signum() < 0) {
                    allowanceLeft = BigDecimal.ZERO;
                }

                // Compute gross required: try to keep taxable 75% within allowance; otherwise include tax impact
                BigDecimal thresholdGrossWithinAllowance = allowanceLeft.divide(TAXED_PORTION, MATH_CONTEXT);
                BigDecimal grossRequired;
                if (need.compareTo(thresholdGrossWithinAllowance) <= 0) {
                    // Entire taxable portion remains within allowance -> no tax, net equals gross
                    grossRequired = need;
                } else {
                    // Part of taxable 75% exceeds allowance; tax applies at 20% on the excess
                    // Net N = 0.25G + allowanceLeft + 0.8*(0.75G - allowanceLeft) = 0.85G + 0.2*allowanceLeft
                    // => G = (N - 0.2*allowanceLeft) / 0.85
                    BigDecimal adjustedNeed = need.subtract(allowanceLeft.multiply(BASIC_RATE, MATH_CONTEXT), MATH_CONTEXT);
                    grossRequired = adjustedNeed.divide(NET_FACTOR, MATH_CONTEXT);
                }

                // Can't withdraw more than available pension
                BigDecimal grossWithdraw = grossRequired.min(pension).setScale(2, RoundingMode.HALF_UP);

                // Determine tax on the taxable portion above remaining allowance
                BigDecimal taxablePortion = grossWithdraw.multiply(TAXED_PORTION, MATH_CONTEXT);
                BigDecimal zeroTaxOnTaxable = taxablePortion.min(allowanceLeft);
                BigDecimal taxedAboveAllowance = taxablePortion.subtract(zeroTaxOnTaxable);
                if (taxedAboveAllowance.signum() < 0) {
                    taxedAboveAllowance = BigDecimal.ZERO;
                }
                BigDecimal taxForThisWithdrawal = taxedAboveAllowance.multiply(BASIC_RATE, MATH_CONTEXT).setScale(2, RoundingMode.HALF_UP);
                taxPaidThisYear = taxPaidThisYear.add(taxForThisWithdrawal);

                // Net received is gross minus tax
                BigDecimal netFromPension = grossWithdraw.subtract(taxForThisWithdrawal);

                // Reduce need by the net amount achieved
                need = need.subtract(netFromPension);
                if (need.signum() < 0) {
                    need = BigDecimal.ZERO;
                }

                // Deduct gross from pension
                pension = pension.subtract(grossWithdraw);
            }

            // Apply end-of-year pension growth
            pension = pension.multiply(BigDecimal.ONE.add(PENSION_GROWTH_RATE, MATH_CONTEXT), MATH_CONTEXT);

            // End-of-year snapshot
            BigDecimal pensionEnd = pension.setScale(2, RoundingMode.HALF_UP);
            BigDecimal savingsEnd = savings.setScale(2, RoundingMode.HALF_UP);
            timeline[idx] = new Wealth(age, pensionStart, pensionEnd, savingsStart, savingsEnd, taxPaidThisYear.setScale(2, RoundingMode.HALF_UP));
        }

        return timeline;
    }

    /**
     * Projects a balance with compound growth applied once per age.
     *
     * For each age:
     *   balance = balance * (1 + annualRatePercent/100)
     *
     * @param startingBalance   initial balance (>= 0)
     * @param annualRatePercent annual growth rate in percent (e.g., 5 for 5%)
     * @param years             number of years to project (>= 0)
     * @return projected balance rounded to 2 decimal places
     */
    public BigDecimal projectBalance(
            BigDecimal startingBalance,
            BigDecimal annualRatePercent,
            int years
    ) {
        Objects.requireNonNull(startingBalance, "startingBalance must not be null");
        Objects.requireNonNull(annualRatePercent, "annualRatePercent must not be null");

        if (startingBalance.signum() < 0) {
            throw new IllegalArgumentException("startingBalance must be >= 0");
        }
        if (years < 0) {
            throw new IllegalArgumentException("years must be >= 0");
        }

        BigDecimal rate = annualRatePercent.divide(BigDecimal.valueOf(100), MATH_CONTEXT);
        BigDecimal balance = startingBalance;

        for (int i = 0; i < years; i++) {
            balance = balance.multiply(BigDecimal.ONE.add(rate, MATH_CONTEXT), MATH_CONTEXT);
        }

        return balance.setScale(2, RoundingMode.HALF_UP);
    }
}
