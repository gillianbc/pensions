package com.gillianbc.pensions.service;

import com.gillianbc.pensions.model.Wealth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class PensionService {

    public static final int START_AGE = 61;
    public static final int END_AGE = 99;
    private static final MathContext MATH_CONTEXT = new MathContext(12, RoundingMode.HALF_UP);
    // Annual pension growth rate (e.g., 0.04 = 4% per year), applied at end of each year
    // The pension pot is modelled to grow at about 4% above inflation
    private static final BigDecimal PENSION_GROWTH_RATE = new BigDecimal("0.04");
    // Tax and income constants
    private static final BigDecimal PERSONAL_ALLOWANCE = new BigDecimal("12570.00");
    // Likely to rise to 12475 from 11973 next year age 61
    private static final BigDecimal STATE_PENSION = new BigDecimal("11973.00");
    private static final BigDecimal BASIC_RATE = new BigDecimal("0.20");
    // Width of the basic-rate taxable band (amount of taxable income taxed at 20%)
    private static final BigDecimal BASIC_RATE_BAND = new BigDecimal("37700.00");
    private static final BigDecimal NO_INCOME_CONTRIBUTION_LIMIT = new BigDecimal("3600.00");


    /**
     * Calls each of the strategy methods then formats the results into an HTML document.
     * Shows the initial savings and pension pot, then for each requested age renders a compact table:
     * - Rows: Strategy1, Strategy2, Strategy3, Strategy3A, Strategy4, Strategy5
     * - Columns: the values from requiredAmounts
     * Each cell shows the total wealth at the end of that specific age for the given strategy and required amount.
     * If no target ages are provided, defaults to a single table at age 99. Tax is not displayed.
     * Params: (BigDecimal savings, BigDecimal pension, BigDecimal[] requiredAmounts, int[] targetAges)
     */
public void generateComparisonReport(BigDecimal savings, BigDecimal pension, BigDecimal[] requiredAmounts, int[] targetAges) {
    // Delegate to the overloaded method with no ad hoc withdrawals
    generateComparisonReport(savings, pension, requiredAmounts, targetAges, Collections.emptyMap());
}

/**
 * Calls each of the strategy methods then formats the results into an HTML document.
 * This overload accepts a per-age map of ad hoc extra withdrawals to be applied in each strategy.
 */
public void generateComparisonReport(BigDecimal savings, BigDecimal pension, BigDecimal[] requiredAmounts, int[] targetAges,
                                     Map<Integer, BigDecimal> adhocWithdrawals) {
    // Basic validation
    Objects.requireNonNull(requiredAmounts, "requiredAmounts must not be null");
    Objects.requireNonNull(adhocWithdrawals, "adhocWithdrawals must not be null");
    if (requiredAmounts.length == 0) {
        throw new IllegalArgumentException("requiredAmounts must not be empty");
    }
    // Validate savings/pension/each requiredAmount >= 0
    for (BigDecimal amt : requiredAmounts) {
        validateParams(savings, pension, Objects.requireNonNull(amt, "requiredAmounts contains null"));
    }

    // Determine which ages to show a table for
    int[] agesToUse;
    if (targetAges != null && targetAges.length > 0) {
        validateTargetAges(targetAges);
        agesToUse = targetAges;
    } else {
        agesToUse = new int[]{END_AGE};
    }

    // Prepare strategy labels and CSS classes
    String[] strategyRowTitles = {
        "Strategy1", "Strategy2", "Strategy3", "Strategy3A", "Strategy4", "Strategy5"
    };
    String[] strategyClasses = {
        "strategy-1", "strategy-2", "strategy-3", "strategy-3a", "strategy-4", "strategy-5"
    };

    // Precompute timelines per required amount to reuse across age tables
    Wealth[][] s1ByAmt = new Wealth[requiredAmounts.length][];
    Wealth[][] s2ByAmt = new Wealth[requiredAmounts.length][];
    Wealth[][] s3ByAmt = new Wealth[requiredAmounts.length][];
    Wealth[][] s3aByAmt = new Wealth[requiredAmounts.length][];
    Wealth[][] s4ByAmt = new Wealth[requiredAmounts.length][];
    Wealth[][] s5ByAmt = new Wealth[requiredAmounts.length][];
    for (int j = 0; j < requiredAmounts.length; j++) {
        BigDecimal req = requiredAmounts[j];
        s1ByAmt[j] = strategy1(savings, pension, req, adhocWithdrawals);
        s2ByAmt[j] = strategy2(savings, pension, req, adhocWithdrawals);
        s3ByAmt[j] = strategy3(savings, pension, req, adhocWithdrawals);
        s3aByAmt[j] = strategy3A(savings, pension, req, adhocWithdrawals);
        s4ByAmt[j] = strategy4(savings, pension, req, adhocWithdrawals);
        s5ByAmt[j] = strategy5(savings, pension, req, adhocWithdrawals);
    }

    // Prepare formatted constants for footer
    String growthRatePercent = PENSION_GROWTH_RATE.multiply(new BigDecimal("100"), MATH_CONTEXT).setScale(2, RoundingMode.HALF_UP).toPlainString();

    // Build HTML
    StringBuilder html = new StringBuilder();
    html.append("<!DOCTYPE html>\n")
        .append("<html lang=\"en\">\n")
        .append("<head>\n")
        .append("    <meta charset=\"UTF-8\">\n")
        .append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
        .append("    <title>Pension Strategy Comparison Report</title>\n")
        .append("    <style>\n")
        .append("        body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }\n")
        .append("        .container { max-width: 1200px; margin: 0 auto; background-color: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n")
        .append("        h1 { color: #2c3e50; text-align: center; margin-bottom: 30px; }\n")
        .append("        .summary { background-color: #ecf0f1; padding: 15px; border-radius: 5px; margin-bottom: 30px; }\n")
        .append("        .summary h2 { color: #34495e; margin-top: 0; }\n")
        .append("        .summary p { margin: 8px 0; font-size: 16px; }\n")
        .append("        h2.age-title { color: #2c3e50; margin-top: 24px; }\n")
        .append("        table { width: 100%; border-collapse: collapse; margin-top: 10px; }\n")
        .append("        th, td { border: 1px solid #ddd; padding: 8px; text-align: right; }\n")
        .append("        th { background-color: #3498db; color: white; font-weight: bold; }\n")
        .append("        tr:nth-child(even) { background-color: #f2f2f2; }\n")
        .append("        tr:hover { background-color: #e8f4f8; }\n")
        .append("        .strategy-1 { background-color: #ffeaa7 !important; }\n")
        .append("        .strategy-2 { background-color: #fab1a0 !important; }\n")
        .append("        .strategy-3 { background-color: #fd79a8 !important; }\n")
        .append("        .strategy-3a { background-color: #fdcb6e !important; }\n")
        .append("        .strategy-4 { background-color: #6c5ce7 !important; color: white; }\n")
        .append("        .strategy-5 { background-color: #00b894 !important; color: white; }\n")
        .append("        .currency { font-family: 'Courier New', monospace; }\n")
        .append("        .strategy-column { text-align: left; font-weight: bold; }\n")
        .append("        .best { outline: 3px solid #ffffff; box-shadow: inset 0 0 0 2px #ffffff; font-weight: bold; }\n")
        .append("    </style>\n")
        .append("</head>\n")
        .append("<body>\n")
        .append("    <div class=\"container\">\n")
        .append("        <h1>Pension Strategy Comparison Report</h1>\n")
        .append("        \n")
        .append("        <div class=\"summary\">\n")
        .append("            <h2>Initial Parameters</h2>\n")
        .append("            <p><strong>Initial Savings:</strong> £").append(String.format("%,.2f", savings)).append("</p>\n")
        .append("            <p><strong>Initial Pension:</strong> £").append(String.format("%,.2f", pension)).append("</p>\n")
        .append("            <p><strong>Example Annual Spending Amounts:</strong> ");

    // Join required amounts for display without extra imports
    StringBuilder raList = new StringBuilder();
    for (int i = 0; i < requiredAmounts.length; i++) {
        if (i > 0) raList.append(", ");
        raList.append("£").append(String.format("%,.2f", requiredAmounts[i]));
    }
    html.append(raList).append("</p>\n");

    // Show target ages summary
    StringBuilder agesList = new StringBuilder();
    for (int i = 0; i < agesToUse.length; i++) {
        if (i > 0) agesList.append(", ");
        agesList.append(agesToUse[i]);
    }
    html.append("            <p><strong>Target Ages:</strong> ").append(agesList).append("</p>\n");

    // Show ad hoc withdrawals summary (at top)
    if (adhocWithdrawals.isEmpty()) {
        html.append("            <p><strong>Ad hoc withdrawals applied:</strong> None</p>\n");
    } else {
        StringBuilder adhocList = new StringBuilder();
        java.util.List<Integer> adhocAges = new java.util.ArrayList<>(adhocWithdrawals.keySet());
        java.util.Collections.sort(adhocAges);
        for (int i = 0; i < adhocAges.size(); i++) {
            Integer wAge = adhocAges.get(i);
            BigDecimal wAmt = adhocWithdrawals.get(wAge);
            if (i > 0) adhocList.append("; ");
            adhocList.append("Age ").append(wAge).append(": £").append(String.format("%,.2f", wAmt));
        }
        html.append("            <p><strong>Ad hoc withdrawals applied:</strong> ").append(adhocList).append("</p>\n");
    }
    html.append("        </div>\n");

    // Render a separate table for each age
    for (int aIdx = 0; aIdx < agesToUse.length; aIdx++) {
        int age = agesToUse[aIdx];
        int idx = Math.max(0, Math.min(END_AGE - START_AGE, age - START_AGE));

        html.append("        <h2 class=\"age-title\">Results at Age ").append(age).append("</h2>\n")
            .append("        <table>\n")
            .append("            <thead>\n")
            .append("                <tr>\n")
            .append("                    <th class=\"strategy-column\">Strategy</th>\n");

        for (BigDecimal amt : requiredAmounts) {
            html.append("                    <th>£").append(String.format("%,.2f", amt)).append("</th>\n");
        }
        html.append("                </tr>\n")
            .append("            </thead>\n")
            .append("            <tbody>\n");

        // Compute the maximum total wealth per spending column for this age index.
        BigDecimal[] maxForCol = new BigDecimal[requiredAmounts.length];
        for (int j = 0; j < requiredAmounts.length; j++) {
            BigDecimal m = s1ByAmt[j][idx].totalEnd();
            m = m.max(s2ByAmt[j][idx].totalEnd());
            m = m.max(s3ByAmt[j][idx].totalEnd());
            m = m.max(s3aByAmt[j][idx].totalEnd());
            m = m.max(s4ByAmt[j][idx].totalEnd());
            m = m.max(s5ByAmt[j][idx].totalEnd());
            maxForCol[j] = m;
        }

        for (int s = 0; s < strategyRowTitles.length; s++) {
            html.append("                <tr class=\"").append(strategyClasses[s]).append("\">\n")
                .append("                    <td class=\"strategy-column\">").append(strategyRowTitles[s]).append("</td>\n");

            for (int j = 0; j < requiredAmounts.length; j++) {
                BigDecimal cell;
                switch (s) {
                    case 0 -> cell = s1ByAmt[j][idx].totalEnd();
                    case 1 -> cell = s2ByAmt[j][idx].totalEnd();
                    case 2 -> cell = s3ByAmt[j][idx].totalEnd();
                    case 3 -> cell = s3aByAmt[j][idx].totalEnd();
                    case 4 -> cell = s4ByAmt[j][idx].totalEnd();
                    case 5 -> cell = s5ByAmt[j][idx].totalEnd();
                    default -> cell = BigDecimal.ZERO;
                }
                boolean isBest = cell.compareTo(maxForCol[j]) == 0;
                String extraClass = isBest ? " best" : "";
                html.append("                    <td class=\"currency").append(extraClass).append("\">£").append(String.format("%,.2f", cell)).append("</td>\n");
            }
            html.append("                </tr>\n");
        }

        html.append("            </tbody>\n")
            .append("        </table>\n");
    }

    // Footer: strategy descriptions
    html.append("        <div style=\"margin-top: 30px; padding: 15px; background-color: #d5dbdb; border-radius: 5px; font-size: 14px; color: #2c3e50;\">\n")
        .append("            <h3>Strategy Descriptions:</h3>\n")
        .append("            <ul>\n")
        .append("                <li><strong>Strategy 1:</strong> Use savings first. When savings gone, take big one-off 25% tax-free lump sum into savings.  When that's spent, drawdown remaining pension 25% tax free, 75% taxed</li>\n")
        .append("                <li><strong>Strategy 2:</strong> Use savings first, then draw from pension with 25% tax-free, 75% taxed</li>\n")
        .append("                <li><strong>Strategy 3:</strong> Drawdown from pension but keep below tax threshold, use savings for remainder of needs.  When savings run out, use pension 25% tax free, 75% taxed.</li>\n")
        .append("                <li><strong>Strategy 3A:</strong> Same as Strategy 3 but also contribute £3,600 annually from savings to pension</li>\n")
        .append("                <li><strong>Strategy 4:</strong> Drawdown max possible without going into higher rate tax threshold. 25% tax-free, 75% taxed Anything not spent goes into savings</li>\n")
        .append("                <li><strong>Strategy 5:</strong> Drawdown from pension, avoid touching savings</li>\n")
        .append("            </ul>\n")
        .append("            <h3>Assumptions</h3>\n")
        .append("            <ul>\n")
        .append("                <li><strong>Pension growth rate above inflation:</strong> ").append(growthRatePercent).append("%</li>\n")
        .append("                <li><strong>Personal allowance:</strong> £").append(String.format("%,.2f", PERSONAL_ALLOWANCE)).append("</li>\n")
        .append("                <li><strong>State pension (annual):</strong> £").append(String.format("%,.2f", STATE_PENSION)).append("</li>\n")
        .append("                <li><strong>Basic-rate band width:</strong> £").append(String.format("%,.2f", BASIC_RATE_BAND)).append("</li>\n")
        .append("                <li><strong>Savings interest:</strong>").append("None - but no inflation either").append("</li>\n")
        .append("            </ul>\n")
        .append("            <p><em>Generated on: ").append(java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("</em></p>\n")
        .append("        </div>\n")
        .append("    </div>\n")
        .append("</body>\n")
        .append("</html>");
    
    // Save to file
    saveHtmlToFile(html.toString());
}

private void saveHtmlToFile(String htmlContent) {
    try {
        // Create target/results directory if it doesn't exist
        java.nio.file.Path resultsDir = java.nio.file.Paths.get("target", "results");
        java.nio.file.Files.createDirectories(resultsDir);
        
        // Generate filename with timestamp
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String filename = "pension-strategy-comparison-" + timestamp + ".html";
        java.nio.file.Path filePath = resultsDir.resolve(filename);
        
        // Write HTML content to file
        java.nio.file.Files.write(filePath, htmlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        
        log.info("Pension strategy comparison report saved to: {}", filePath.toAbsolutePath());
        
    } catch (java.io.IOException e) {
        log.error("Failed to save HTML report to target/results directory", e);
        throw new RuntimeException("Failed to save HTML report", e);
    }
}

    /**
     * Strategy 1: Use up savings first, then take a one off 25% tax-free lump sum from pensions,
     * then drawdown the remaining pension
     * <p>
     * If the required amount is greater than the savings, then the savings is reduced by the required amount.
     * Otherwise, a lump sum of 25% of the pension is transferred to the savings account before the required amount is paid.
     * The lump sum transfer from pension to savings can only be done once.
     * If the savings are insufficient to cover the required amount, then any remaining savings are used first and the
     * remainder of the required amount is paid out of the pension.
     * The withdrawals from the pension are subject to tax at 20% if the total income from pension
     * and state pension exceeds £12,570.00 i.e. the first 12570 is tax free, the remainder is taxed at 20%.
     * The state pension will be paid at age 67 onwards and is £11,973 per age.
     * Pension grows at PENSION_GROWTH_RATE at the end of each year.
     *
     * @param savings        starting savings balance (>= 0)
     * @param pension        starting pension balance (>= 0)
     * @param requiredAmount required net withdrawal per year (>= 0)
     * @return array of Wealth objects, one per age from 61 through 99 inclusive
     */
    public Wealth[] strategy1(BigDecimal savings, BigDecimal pension, BigDecimal requiredAmount,
                              Map<Integer, BigDecimal> adhocWithdrawals) {
        validateParams(savings, pension, requiredAmount);
        Objects.requireNonNull(adhocWithdrawals, "adhocWithdrawals must not be null");

        final BigDecimal ONE = BigDecimal.ONE;

        final int len = END_AGE - START_AGE + 1;

        Wealth[] timeline = new Wealth[len];

        boolean lumpSumTaken = false;

        int age = START_AGE;
        for (int idx = 0; idx < len; idx++, age++) {
            // Snapshot start-of-year balances
            BigDecimal pensionStart = pension.setScale(2, RoundingMode.HALF_UP);
            BigDecimal savingsStart = savings.setScale(2, RoundingMode.HALF_UP);
            BigDecimal taxPaidThisYear = BigDecimal.ZERO;

            // State pension from age 67
            BigDecimal statePensionIncome = age >= 67 ? STATE_PENSION : BigDecimal.ZERO;

            // Ad hoc extra spending for this age
            BigDecimal extraThisYear = adhocWithdrawals.getOrDefault(age, BigDecimal.ZERO);
            if (extraThisYear.signum() < 0) {
                throw new IllegalArgumentException("Ad hoc withdrawal for age " + age + " must be >= 0");
            }

            // Net amount still required after state pension (base required + extra)
            BigDecimal need = requiredAmount.add(extraThisYear).subtract(statePensionIncome);
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
            timeline[idx] = new Wealth(age, pensionStart, pensionEnd, savingsStart, savingsEnd, taxPaidThisYear.setScale(2, RoundingMode.HALF_UP), extraThisYear.setScale(2, RoundingMode.HALF_UP));
        }

        return timeline;
    }

    /**
     * Strategy 2: Spend savings first. When savings are depleted, draw from pension where
     * 25% of each pension withdrawal is tax-free and the remaining 75% is taxed at 20%.
     * <p>
     * State pension is received from age 67 and offsets the required net withdrawal.
     * Pension grows at PENSION_GROWTH_RATE at the end of each year.
     *
     * @param savings        starting savings balance (>= 0)
     * @param pension        starting pension balance (>= 0)
     * @param requiredAmount required net withdrawal per year (>= 0)
     * @return array of Wealth objects, one per age from 61 through 99 inclusive
     */
    public Wealth[] strategy2(BigDecimal savings, BigDecimal pension, BigDecimal requiredAmount, Map<Integer, BigDecimal> adhocWithdrawals) {
        validateParams(savings, pension, requiredAmount);
        Objects.requireNonNull(adhocWithdrawals, "adhocWithdrawals must not be null");

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

            // Ad hoc extra spending for this age
            BigDecimal extraThisYear = adhocWithdrawals.getOrDefault(age, BigDecimal.ZERO);
            if (extraThisYear.signum() < 0) {
                throw new IllegalArgumentException("Ad hoc withdrawal for age " + age + " must be >= 0");
            }

            // Net amount still required after state pension
            BigDecimal need = requiredAmount.add(extraThisYear).subtract(statePensionIncome);
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
            timeline[idx] = new Wealth(age, pensionStart, pensionEnd, savingsStart, savingsEnd, taxPaidThisYear.setScale(2, RoundingMode.HALF_UP), extraThisYear.setScale(2, RoundingMode.HALF_UP));
        }

        return timeline;
    }

    // TODO Add a strategy similar to strategy 2, but pay in £3600 from savings into pension.  Cannot do that age 75.

    /**
     * Strategy 3: Use max tax-free drawdown of £16760 in early years and use savings for the remainder of the required amount.
     * <p>
     * Pension withdrawals are treated as 25% tax-free and 75% taxable at 20%, with the taxable portion
     * using up the annual personal allowance (12,570). Only the amount of the taxable portion above the
     * remaining allowance is taxed. From age 67, the state pension (11,973) both reduces the net need and
     * consumes part of the personal allowance for the year.
     *
     * The pension grows at the PENSION_GROWTH_RATE at the end of each year.
     *
     * @param savings        starting savings balance (>= 0)
     * @param pension        starting pension balance (>= 0)
     * @param requiredAmount required net withdrawal per year (>= 0)
     * @return array of Wealth objects, one per age from 61 through 99 inclusive
     */
    public Wealth[] strategy3(BigDecimal savings, BigDecimal pension, BigDecimal requiredAmount, Map<Integer, BigDecimal> adhocWithdrawals) {
        validateParams(savings, pension, requiredAmount);
        Objects.requireNonNull(adhocWithdrawals, "adhocWithdrawals must not be null");

        final BigDecimal TAX_FREE_PORTION = new BigDecimal("0.25");
        final BigDecimal TAXED_PORTION = new BigDecimal("0.75");
        final BigDecimal NET_FACTOR = TAX_FREE_PORTION.add(TAXED_PORTION.multiply(BigDecimal.ONE.subtract(BASIC_RATE), MATH_CONTEXT)); // 0.85

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

            // Ad hoc extra spending for this age
            BigDecimal extraThisYear = adhocWithdrawals.getOrDefault(age, BigDecimal.ZERO);
            if (extraThisYear.signum() < 0) {
                throw new IllegalArgumentException("Ad hoc withdrawal for age " + age + " must be >= 0");
            }

            // Net amount still required after state pension
            BigDecimal need = requiredAmount.add(extraThisYear).subtract(statePensionIncome);
            if (need.signum() < 0) {
                need = BigDecimal.ZERO;
            }

            // Draw from pension first
            if (need.signum() > 0 && pension.signum() > 0) {
                // Remaining personal allowance after accounting for state pension
                BigDecimal allowanceLeft = PERSONAL_ALLOWANCE.subtract(statePensionIncome);
                if (allowanceLeft.signum() < 0) {
                    allowanceLeft = BigDecimal.ZERO;
                }

                // Step 1: Withdraw up to the amount that keeps the taxable 75% within allowance (zero tax)
                BigDecimal grossCapWithinAllowance = allowanceLeft.divide(TAXED_PORTION, MATH_CONTEXT); // allowanceLeft / 0.75
                BigDecimal grossZeroCandidate = need.min(grossCapWithinAllowance).min(pension);
                BigDecimal grossZero = grossZeroCandidate.setScale(2, RoundingMode.HALF_UP);
                if (grossZero.signum() > 0) {
                    BigDecimal taxableZero = grossZero.multiply(TAXED_PORTION, MATH_CONTEXT);
                    BigDecimal taxZero = BigDecimal.ZERO;
                    BigDecimal netZero = grossZero.subtract(taxZero);

                    // Apply withdrawal
                    pension = pension.subtract(grossZero);
                    need = need.subtract(netZero);
                    if (need.signum() < 0) {
                        need = BigDecimal.ZERO;
                    }

                    // Consume allowance based on taxable portion
                    BigDecimal allowanceConsumed = taxableZero.min(allowanceLeft);
                    allowanceLeft = allowanceLeft.subtract(allowanceConsumed);
                    if (allowanceLeft.signum() < 0) {
                        allowanceLeft = BigDecimal.ZERO;
                    }
                }

                // Step 2: If still needed, try to cover the remainder from savings to avoid tax
                if (need.signum() > 0 && savings.signum() > 0) {
                    BigDecimal fromSavings = need.min(savings);
                    savings = savings.subtract(fromSavings);
                    need = need.subtract(fromSavings);
                    if (need.signum() < 0) {
                        need = BigDecimal.ZERO;
                    }
                }

                // Step 3: If still needed after using savings, withdraw further from pension (tax applies above remaining allowance)
                if (need.signum() > 0 && pension.signum() > 0) {
                    // Solve for gross required considering any remaining allowance:
                    // Net = Gross - 0.20 * max(0, 0.75*Gross - allowanceLeft)
                    // When allowanceLeft is insufficient, Net = 0.85*Gross + 0.20*allowanceLeft -> Gross = (Net - 0.20*allowanceLeft)/0.85
                    BigDecimal adjustedNeed = need.subtract(allowanceLeft.multiply(BASIC_RATE, MATH_CONTEXT), MATH_CONTEXT);
                    if (adjustedNeed.signum() < 0) {
                        adjustedNeed = BigDecimal.ZERO;
                    }
                    BigDecimal grossRequired = adjustedNeed.divide(NET_FACTOR, MATH_CONTEXT);

                    // Withdraw, bounded by available pension
                    BigDecimal grossWithdraw = grossRequired.min(pension).setScale(2, RoundingMode.HALF_UP);

                    // Tax on taxable portion above remaining allowance
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

                    // Apply withdrawal
                    pension = pension.subtract(grossWithdraw);
                    need = need.subtract(netFromPension);
                    if (need.signum() < 0) {
                        need = BigDecimal.ZERO;
                    }

                    // Update allowance
                    allowanceLeft = allowanceLeft.subtract(zeroTaxOnTaxable);
                    if (allowanceLeft.signum() < 0) {
                        allowanceLeft = BigDecimal.ZERO;
                    }
                }
            }

            // If still needed, cover the remainder from savings
            if (need.signum() > 0 && savings.signum() > 0) {
                BigDecimal fromSavings = need.min(savings);
                savings = savings.subtract(fromSavings);
                need = need.subtract(fromSavings);
                if (need.signum() < 0) {
                    need = BigDecimal.ZERO;
                }
            }

            // Apply end-of-year pension growth
            pension = pension.multiply(BigDecimal.ONE.add(PENSION_GROWTH_RATE, MATH_CONTEXT), MATH_CONTEXT);

            // End-of-year snapshot
            BigDecimal pensionEnd = pension.setScale(2, RoundingMode.HALF_UP);
            BigDecimal savingsEnd = savings.setScale(2, RoundingMode.HALF_UP);
            timeline[idx] = new Wealth(age, pensionStart, pensionEnd, savingsStart, savingsEnd, taxPaidThisYear.setScale(2, RoundingMode.HALF_UP), extraThisYear.setScale(2, RoundingMode.HALF_UP));
        }

        return timeline;
    }

    /**
     * Strategy 3A: Use max tax-free drawdown of £16760 in early years and use savings for the remainder of the required amount.
     * Also pay £3600 into pension from savings per year
     * <p>
     * Pension withdrawals are treated as 25% tax-free and 75% taxable at 20%, with the taxable portion
     * using up the annual personal allowance (12,570). Only the amount of the taxable portion above the
     * remaining allowance is taxed. From age 67, the state pension (11,973) both reduces the net need and
     * consumes part of the personal allowance for the year.
     *
     * The pension grows at the PENSION_GROWTH_RATE at the end of each year.
     *
     * @param savings        starting savings balance (>= 0)
     * @param pension        starting pension balance (>= 0)
     * @param requiredAmount required net withdrawal per year (>= 0)
     * @return array of Wealth objects, one per age from 61 through 99 inclusive
     */
    public Wealth[] strategy3A(BigDecimal savings, BigDecimal pension, BigDecimal requiredAmount, Map<Integer, BigDecimal> adhocWithdrawals) {
        validateParams(savings, pension, requiredAmount);
        Objects.requireNonNull(adhocWithdrawals, "adhocWithdrawals must not be null");

        final BigDecimal TAX_FREE_PORTION = new BigDecimal("0.25");
        final BigDecimal TAXED_PORTION = new BigDecimal("0.75");
        final BigDecimal NET_FACTOR = TAX_FREE_PORTION.add(TAXED_PORTION.multiply(BigDecimal.ONE.subtract(BASIC_RATE), MATH_CONTEXT)); // 0.85

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

            // Ad hoc extra spending for this age
            BigDecimal extraThisYear = adhocWithdrawals.getOrDefault(age, BigDecimal.ZERO);
            if (extraThisYear.signum() < 0) {
                throw new IllegalArgumentException("Ad hoc withdrawal for age " + age + " must be >= 0");
            }

            // Net amount still required after state pension
            BigDecimal need = requiredAmount.add(extraThisYear).subtract(statePensionIncome);
            if (need.signum() < 0) {
                need = BigDecimal.ZERO;
            }

            // Pay £3600 from savings into pension
            if (savings.signum() > 0) {
                BigDecimal fromSavingsToPension = NO_INCOME_CONTRIBUTION_LIMIT.min(savings);
                savings = savings.subtract(fromSavingsToPension);
                pension = pension.add(fromSavingsToPension);
            }

            // Draw from pension first
            if (need.signum() > 0 && pension.signum() > 0) {
                // Remaining personal allowance after accounting for state pension
                BigDecimal allowanceLeft = PERSONAL_ALLOWANCE.subtract(statePensionIncome);
                if (allowanceLeft.signum() < 0) {
                    allowanceLeft = BigDecimal.ZERO;
                }

                // Step 1: Withdraw up to the amount that keeps the taxable 75% within allowance (zero tax)
                BigDecimal grossCapWithinAllowance = allowanceLeft.divide(TAXED_PORTION, MATH_CONTEXT); // allowanceLeft / 0.75
                BigDecimal grossZeroCandidate = need.min(grossCapWithinAllowance).min(pension);
                BigDecimal grossZero = grossZeroCandidate.setScale(2, RoundingMode.HALF_UP);
                if (grossZero.signum() > 0) {
                    BigDecimal taxableZero = grossZero.multiply(TAXED_PORTION, MATH_CONTEXT);
                    BigDecimal taxZero = BigDecimal.ZERO;
                    BigDecimal netZero = grossZero.subtract(taxZero);

                    // Apply withdrawal
                    pension = pension.subtract(grossZero);
                    need = need.subtract(netZero);
                    if (need.signum() < 0) {
                        need = BigDecimal.ZERO;
                    }

                    // Consume allowance based on taxable portion
                    BigDecimal allowanceConsumed = taxableZero.min(allowanceLeft);
                    allowanceLeft = allowanceLeft.subtract(allowanceConsumed);
                    if (allowanceLeft.signum() < 0) {
                        allowanceLeft = BigDecimal.ZERO;
                    }
                }

                // Step 2: If still needed, try to cover the remainder from savings to avoid tax
                if (need.signum() > 0 && savings.signum() > 0) {
                    BigDecimal fromSavings = need.min(savings);
                    savings = savings.subtract(fromSavings);
                    need = need.subtract(fromSavings);
                    if (need.signum() < 0) {
                        need = BigDecimal.ZERO;
                    }
                }

                // Step 3: If still needed after using savings, withdraw further from pension (tax applies above remaining allowance)
                if (need.signum() > 0 && pension.signum() > 0) {
                    // Solve for gross required considering any remaining allowance:
                    // Net = Gross - 0.20 * max(0, 0.75*Gross - allowanceLeft)
                    // When allowanceLeft is insufficient, Net = 0.85*Gross + 0.20*allowanceLeft -> Gross = (Net - 0.20*allowanceLeft)/0.85
                    BigDecimal adjustedNeed = need.subtract(allowanceLeft.multiply(BASIC_RATE, MATH_CONTEXT), MATH_CONTEXT);
                    if (adjustedNeed.signum() < 0) {
                        adjustedNeed = BigDecimal.ZERO;
                    }
                    BigDecimal grossRequired = adjustedNeed.divide(NET_FACTOR, MATH_CONTEXT);

                    // Withdraw, bounded by available pension
                    BigDecimal grossWithdraw = grossRequired.min(pension).setScale(2, RoundingMode.HALF_UP);

                    // Tax on taxable portion above remaining allowance
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

                    // Apply withdrawal
                    pension = pension.subtract(grossWithdraw);
                    need = need.subtract(netFromPension);
                    if (need.signum() < 0) {
                        need = BigDecimal.ZERO;
                    }

                    // Update allowance
                    allowanceLeft = allowanceLeft.subtract(zeroTaxOnTaxable);
                    if (allowanceLeft.signum() < 0) {
                        allowanceLeft = BigDecimal.ZERO;
                    }
                }
            }

            // If still needed, cover the remainder from savings
            if (need.signum() > 0 && savings.signum() > 0) {
                BigDecimal fromSavings = need.min(savings);
                savings = savings.subtract(fromSavings);
                need = need.subtract(fromSavings);
                if (need.signum() < 0) {
                    need = BigDecimal.ZERO;
                }
            }

            // Apply end-of-year pension growth
            pension = pension.multiply(BigDecimal.ONE.add(PENSION_GROWTH_RATE, MATH_CONTEXT), MATH_CONTEXT);

            // End-of-year snapshot
            BigDecimal pensionEnd = pension.setScale(2, RoundingMode.HALF_UP);
            BigDecimal savingsEnd = savings.setScale(2, RoundingMode.HALF_UP);
            timeline[idx] = new Wealth(age, pensionStart, pensionEnd, savingsStart, savingsEnd, taxPaidThisYear.setScale(2, RoundingMode.HALF_UP), extraThisYear.setScale(2, RoundingMode.HALF_UP));
        }

        return timeline;
    }

    // TODO Add a strategy similar to strategy 3, but pay in £3600 from savings into pension.  Cannot do that age 75.

    /**
     * Strategy 4: Basic-Rate Band Filler.
     * <p>
     * Each year:
     * - Withdraw from pension up to the zero-tax limit (25% tax-free / 75% taxable within remaining personal allowance).
     * - Then withdraw further to fully use the basic-rate band (20% on the taxable portion above allowance).
     * - Use any surplus net (beyond this year's spending need) to increase savings.
     * - If pension net is insufficient for spending, top up from savings.
     * This strategy should be used if I wanted to draw out a big lump sum.  Anything above £50,271 is liable
     * for tax at 40% rather than 20%, so if I needed 80,000, I would spread the withdrawals out over multiple
     * financial years.
     * Likewise, use this model to move money out of pensions as fast as possible without paying excessive tax
     * e.g. to spend frivolously or give away
     */
    public Wealth[] strategy4(BigDecimal savings, BigDecimal pension, BigDecimal requiredAmount, Map<Integer, BigDecimal> adhocWithdrawals) {
        validateParams(savings, pension, requiredAmount);
        Objects.requireNonNull(adhocWithdrawals, "adhocWithdrawals must not be null");

        final BigDecimal TAX_FREE_PORTION = new BigDecimal("0.25");
        final BigDecimal TAXED_PORTION = new BigDecimal("0.75");

        final int len = END_AGE - START_AGE + 1;
        Wealth[] timeline = new Wealth[len];

        int age = START_AGE;
        for (int idx = 0; idx < len; idx++, age++) {
            BigDecimal pensionStart = pension.setScale(2, RoundingMode.HALF_UP);
            BigDecimal savingsStart = savings.setScale(2, RoundingMode.HALF_UP);
            BigDecimal taxPaidThisYear = BigDecimal.ZERO;

            BigDecimal statePensionIncome = age >= 67 ? STATE_PENSION : BigDecimal.ZERO;

            // Ad hoc extra spending for this age
            BigDecimal extraThisYear = adhocWithdrawals.getOrDefault(age, BigDecimal.ZERO);
            if (extraThisYear.signum() < 0) {
                throw new IllegalArgumentException("Ad hoc withdrawal for age " + age + " must be >= 0");
            }

            // Net spending need after state pension
            BigDecimal need = requiredAmount.add(extraThisYear).subtract(statePensionIncome);
            if (need.signum() < 0) need = BigDecimal.ZERO;

            // Remaining personal allowance after state pension
            BigDecimal allowanceLeft = PERSONAL_ALLOWANCE.subtract(statePensionIncome);
            if (allowanceLeft.signum() < 0) allowanceLeft = BigDecimal.ZERO;

            BigDecimal netFromPensionTotal = BigDecimal.ZERO;

            // Step A: Zero-tax UFPLS withdrawal within remaining allowance
            if (pension.signum() > 0 && allowanceLeft.signum() > 0) {
                BigDecimal grossCapWithinAllowance = allowanceLeft.divide(TAXED_PORTION, MATH_CONTEXT);
                BigDecimal grossZero = grossCapWithinAllowance.min(pension).setScale(2, RoundingMode.HALF_UP);

                if (grossZero.signum() > 0) {
                    // No tax within allowance
                    BigDecimal taxableZero = grossZero.multiply(TAXED_PORTION, MATH_CONTEXT);
                    BigDecimal netZero = grossZero; // zero tax
                    pension = pension.subtract(grossZero);

                    // Consume allowance by the taxable portion
                    BigDecimal consumed = taxableZero.min(allowanceLeft);
                    allowanceLeft = allowanceLeft.subtract(consumed);
                    if (allowanceLeft.signum() < 0) allowanceLeft = BigDecimal.ZERO;

                    netFromPensionTotal = netFromPensionTotal.add(netZero);
                }
            }

            // Step B: Withdraw more to fill the basic-rate band (taxed at 20% above any remaining allowance)
            if (pension.signum() > 0) {
                // Portion of basic-rate band already used by state pension (above allowance)
                BigDecimal taxableFromStatePension = statePensionIncome.subtract(PERSONAL_ALLOWANCE);
                if (taxableFromStatePension.signum() < 0) taxableFromStatePension = BigDecimal.ZERO;

                BigDecimal remainingBasicBand = BASIC_RATE_BAND.subtract(taxableFromStatePension);
                if (remainingBasicBand.signum() < 0) remainingBasicBand = BigDecimal.ZERO;

                if (remainingBasicBand.signum() > 0) {
                    // Solve for gross so that taxable above remaining allowance equals remainingBasicBand:
                    // max(0, 0.75*G - allowanceLeft) = remainingBasicBand -> G = (remainingBasicBand + allowanceLeft) / 0.75
                    BigDecimal grossFillTarget = remainingBasicBand.add(allowanceLeft, MATH_CONTEXT).divide(TAXED_PORTION, MATH_CONTEXT);
                    BigDecimal grossFill = grossFillTarget.min(pension).setScale(2, RoundingMode.HALF_UP);

                    if (grossFill.signum() > 0) {
                        BigDecimal taxablePortion = grossFill.multiply(TAXED_PORTION, MATH_CONTEXT);
                        BigDecimal zeroTaxOnTaxable = taxablePortion.min(allowanceLeft);
                        BigDecimal taxedAboveAllowance = taxablePortion.subtract(zeroTaxOnTaxable);
                        if (taxedAboveAllowance.signum() < 0) taxedAboveAllowance = BigDecimal.ZERO;

                        BigDecimal tax = taxedAboveAllowance.multiply(BASIC_RATE, MATH_CONTEXT).setScale(2, RoundingMode.HALF_UP);
                        taxPaidThisYear = taxPaidThisYear.add(tax);
                        BigDecimal netFill = grossFill.subtract(tax);

                        pension = pension.subtract(grossFill);

                        // Update allowance
                        allowanceLeft = allowanceLeft.subtract(zeroTaxOnTaxable);
                        if (allowanceLeft.signum() < 0) allowanceLeft = BigDecimal.ZERO;

                        netFromPensionTotal = netFromPensionTotal.add(netFill);
                    }
                }
            }

            // Apply pension net to spending; deposit surplus to savings
            if (netFromPensionTotal.signum() > 0) {
                BigDecimal spendFromPension = netFromPensionTotal.min(need);
                need = need.subtract(spendFromPension);
                BigDecimal surplus = netFromPensionTotal.subtract(spendFromPension);
                if (surplus.signum() > 0) {
                    savings = savings.add(surplus);
                }
            }

            // If still needed, draw from savings
            if (need.signum() > 0 && savings.signum() > 0) {
                BigDecimal fromSavings = need.min(savings);
                savings = savings.subtract(fromSavings);
                need = need.subtract(fromSavings);
                if (need.signum() < 0) need = BigDecimal.ZERO;
            }

            // Apply end-of-year pension growth
            pension = pension.multiply(BigDecimal.ONE.add(PENSION_GROWTH_RATE, MATH_CONTEXT), MATH_CONTEXT);

            BigDecimal pensionEnd = pension.setScale(2, RoundingMode.HALF_UP);
            BigDecimal savingsEnd = savings.setScale(2, RoundingMode.HALF_UP);
            timeline[idx] = new Wealth(age, pensionStart, pensionEnd, savingsStart, savingsEnd, taxPaidThisYear.setScale(2, RoundingMode.HALF_UP), extraThisYear.setScale(2, RoundingMode.HALF_UP));
        }

        return timeline;
    }

    /**
     * Strategy 5: Withdraw required amount from pensions 25% tax free 75% taxed.  Don't touch savings.
     * <p>
     * Each year, meet the net need primarily from pension using UFPLS rules
     * i.e. use pension before tapping into savings.
     * - 25% of each withdrawal is tax-free; 75% is taxable against remaining allowance then at 20%.
     * - Any shortfall is covered from savings.
     */
    public Wealth[] strategy5(BigDecimal savings, BigDecimal pension, BigDecimal requiredAmount, Map<Integer, BigDecimal> adhocWithdrawals) {
        validateParams(savings, pension, requiredAmount);
        Objects.requireNonNull(adhocWithdrawals, "adhocWithdrawals must not be null");

        final BigDecimal TAX_FREE_PORTION = new BigDecimal("0.25");
        final BigDecimal TAXED_PORTION = new BigDecimal("0.75");
        final BigDecimal NET_FACTOR = TAX_FREE_PORTION.add(TAXED_PORTION.multiply(BigDecimal.ONE.subtract(BASIC_RATE), MATH_CONTEXT)); // 0.85

        final int len = END_AGE - START_AGE + 1;
        Wealth[] timeline = new Wealth[len];

        int age = START_AGE;
        for (int idx = 0; idx < len; idx++, age++) {
            BigDecimal pensionStart = pension.setScale(2, RoundingMode.HALF_UP);
            BigDecimal savingsStart = savings.setScale(2, RoundingMode.HALF_UP);
            BigDecimal taxPaidThisYear = BigDecimal.ZERO;

            BigDecimal statePensionIncome = age >= 67 ? STATE_PENSION : BigDecimal.ZERO;

            // Ad hoc extra spending for this age
            BigDecimal extraThisYear = adhocWithdrawals.getOrDefault(age, BigDecimal.ZERO);
            if (extraThisYear.signum() < 0) {
                throw new IllegalArgumentException("Ad hoc withdrawal for age " + age + " must be >= 0");
            }

            // Net amount still required after state pension
            BigDecimal need = requiredAmount.add(extraThisYear).subtract(statePensionIncome);
            if (need.signum() < 0) need = BigDecimal.ZERO;

            // Withdraw from pension using UFPLS
            if (need.signum() > 0 && pension.signum() > 0) {
                BigDecimal allowanceLeft = PERSONAL_ALLOWANCE.subtract(statePensionIncome);
                if (allowanceLeft.signum() < 0) allowanceLeft = BigDecimal.ZERO;

                // Compute gross required to meet 'need' net:
                // If within allowance: gross = need; else gross = (need - 0.2*allowanceLeft)/0.85
                BigDecimal thresholdGrossWithinAllowance = allowanceLeft.divide(TAXED_PORTION, MATH_CONTEXT);
                BigDecimal grossRequired;
                if (need.compareTo(thresholdGrossWithinAllowance) <= 0) {
                    grossRequired = need;
                } else {
                    BigDecimal adjustedNeed = need.subtract(allowanceLeft.multiply(BASIC_RATE, MATH_CONTEXT), MATH_CONTEXT);
                    grossRequired = adjustedNeed.divide(NET_FACTOR, MATH_CONTEXT);
                }

                BigDecimal grossWithdraw = grossRequired.min(pension).setScale(2, RoundingMode.HALF_UP);

                BigDecimal taxablePortion = grossWithdraw.multiply(TAXED_PORTION, MATH_CONTEXT);
                BigDecimal zeroTaxOnTaxable = taxablePortion.min(allowanceLeft);
                BigDecimal taxedAboveAllowance = taxablePortion.subtract(zeroTaxOnTaxable);
                if (taxedAboveAllowance.signum() < 0) taxedAboveAllowance = BigDecimal.ZERO;

                BigDecimal tax = taxedAboveAllowance.multiply(BASIC_RATE, MATH_CONTEXT).setScale(2, RoundingMode.HALF_UP);
                taxPaidThisYear = tax.add(taxPaidThisYear);

                BigDecimal netFromPension = grossWithdraw.subtract(tax);

                pension = pension.subtract(grossWithdraw);

                // Apply to need; if a rounding surplus occurs, add to savings
                if (netFromPension.compareTo(need) >= 0) {
                    BigDecimal surplus = netFromPension.subtract(need);
                    need = BigDecimal.ZERO;
                    if (surplus.signum() > 0) {
                        savings = savings.add(surplus);
                    }
                } else {
                    need = need.subtract(netFromPension);
                }
            }

            // If still needed, cover from savings
            if (need.signum() > 0 && savings.signum() > 0) {
                BigDecimal fromSavings = need.min(savings);
                savings = savings.subtract(fromSavings);
                need = need.subtract(fromSavings);
                if (need.signum() < 0) need = BigDecimal.ZERO;
            }

            // Apply end-of-year pension growth
            pension = pension.multiply(BigDecimal.ONE.add(PENSION_GROWTH_RATE, MATH_CONTEXT), MATH_CONTEXT);

            BigDecimal pensionEnd = pension.setScale(2, RoundingMode.HALF_UP);
            BigDecimal savingsEnd = savings.setScale(2, RoundingMode.HALF_UP);
            timeline[idx] = new Wealth(age, pensionStart, pensionEnd, savingsStart, savingsEnd, taxPaidThisYear.setScale(2, RoundingMode.HALF_UP), extraThisYear.setScale(2, RoundingMode.HALF_UP));
        }

        return timeline;
    }

    private static void validateParams(BigDecimal savings, BigDecimal pension, BigDecimal requiredAmount) {
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
    }

    private static void validateTargetAges(int[] targetAges) {
        Objects.requireNonNull(targetAges, "targetAges must not be null");
        if (targetAges.length == 0) {
            throw new IllegalArgumentException("targetAges must not be empty");
        }
        for (int age : targetAges) {
            if (age < START_AGE || age > END_AGE) {
                throw new IllegalArgumentException("target age " + age + " must be between " + START_AGE + " and " + END_AGE + " inclusive");
            }
        }
    }

    /**
     * Moves a net contribution from savings to pension, grossing up with 20% basic-rate tax relief
     * (relief at source) when eligible.
     *
     * Examples:
     *   - Age < 75: paying £2,880 from savings -> £3,600 added to pension (+£720 relief).
     *   - Age >= 75: no tax relief, so £X from savings -> £X added to pension.
     *
     * Optionally enforces the £3,600 gross (£2,880 net) “no income” annual cap.
     */
    public TransferResult contributeFromSavingsToPension(
            BigDecimal savings,
            BigDecimal pension,
            BigDecimal requestedNetFromSavings,
            int age,
            boolean applyNoIncomeGrossCap
    ) {
        Objects.requireNonNull(savings, "savings must not be null");
        Objects.requireNonNull(pension, "pension must not be null");
        Objects.requireNonNull(requestedNetFromSavings, "requestedNetFromSavings must not be null");
        if (savings.signum() < 0 || pension.signum() < 0 || requestedNetFromSavings.signum() < 0) {
            throw new IllegalArgumentException("balances and requested amount must be >= 0");
        }

        // Nothing to do if no savings or no request
        if (savings.signum() == 0 || requestedNetFromSavings.signum() == 0) {
            return new TransferResult(savings.setScale(2, RoundingMode.HALF_UP),
                    pension.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }

        // Net we can actually take from savings
        BigDecimal availableNet = requestedNetFromSavings.min(savings);

        // If applying the £3,600 GROSS (i.e., £2,880 NET) cap,
        // cap the NET contribution accordingly (only relevant where relief applies).
        if (applyNoIncomeGrossCap && age < 75) {
            BigDecimal noIncomeNetCap =
                    NO_INCOME_CONTRIBUTION_LIMIT.multiply(BigDecimal.ONE.subtract(BASIC_RATE), MATH_CONTEXT); // 3600 * 0.8 = 2880
            availableNet = availableNet.min(noIncomeNetCap);
        }

        // If age >= 75, no relief: £net -> £net
        if (age >= 75) {
            BigDecimal newSavings = savings.subtract(availableNet);
            BigDecimal newPension = pension.add(availableNet);
            return new TransferResult(newSavings.setScale(2, RoundingMode.HALF_UP),
                    newPension.setScale(2, RoundingMode.HALF_UP),
                    availableNet.setScale(2, RoundingMode.HALF_UP), // gross added equals net
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }

        // Age < 75: relief at 20% of GROSS (i.e., gross-up the NET by dividing by 0.8)
        BigDecimal gross = availableNet.divide(BigDecimal.ONE.subtract(BASIC_RATE), MATH_CONTEXT); // net / 0.8
        BigDecimal relief = gross.subtract(availableNet, MATH_CONTEXT);

        BigDecimal newSavings = savings.subtract(availableNet);
        BigDecimal newPension = pension.add(gross);

        return new TransferResult(newSavings.setScale(2, RoundingMode.HALF_UP),
                newPension.setScale(2, RoundingMode.HALF_UP),
                gross.setScale(2, RoundingMode.HALF_UP),
                relief.setScale(2, RoundingMode.HALF_UP));
    }

    /** Result object for a savings->pension transfer with tax relief. */
    public static final class TransferResult {
        public final BigDecimal savingsEnd;
        public final BigDecimal pensionEnd;
        /** Total added to pension (gross). */
        public final BigDecimal grossContributionAdded;
        /** Portion of the gross that was tax relief. */
        public final BigDecimal taxReliefAdded;

        public TransferResult(BigDecimal savingsEnd,
                              BigDecimal pensionEnd,
                              BigDecimal grossContributionAdded,
                              BigDecimal taxReliefAdded) {
            this.savingsEnd = savingsEnd;
            this.pensionEnd = pensionEnd;
            this.grossContributionAdded = grossContributionAdded;
            this.taxReliefAdded = taxReliefAdded;
        }
    }


    /**
     * Projects a balance with compound growth applied once per age.
     * <p>
     * For each age:
     * balance = balance * (1 + annualRatePercent/100)
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
