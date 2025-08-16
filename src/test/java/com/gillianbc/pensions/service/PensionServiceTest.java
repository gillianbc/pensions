package com.gillianbc.pensions.service;

import com.gillianbc.pensions.model.Wealth;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
class PensionServiceTest {

    private static final BigDecimal INITIAL_SAVINGS = new BigDecimal("74000.00");
    private static final BigDecimal INITIAL_PENSION = new BigDecimal("425000.00");
    private static final BigDecimal ANNUAL_REQUIRED_NET = new BigDecimal("23000.00");

    private final PensionService service = new PensionService();

    @Test
    @DisplayName("strategy1: returns ages 61..99 and applies savings-first, one-time 25% lump sum, and taxation")
    void strategy1_timeline_and_rules() {
        var timeline = service.strategy1(
                INITIAL_SAVINGS,
                INITIAL_PENSION,
                ANNUAL_REQUIRED_NET
        );

        // Length: ages 61..99 inclusive
        assertEquals(39, timeline.length);
        // Log the full timeline for visibility
        log.info("age, savingsStart, pensionStart, savingsEnd, pensionEnd, totalWealthEnd");
        for (Wealth w : timeline) {
            log.info("{},{},{},{},{},{}",
                    w.getAge(), w.getSavingsStart(), w.getPensionStart(), w.getSavingsEnd(), w.getPensionEnd(), w.totalEnd());
        }

        // Age 61 snapshot (index 0): spend from savings only
        {
            Wealth w = timeline[0];
            int expectedAge = 61;
            BigDecimal expectedPensionStart = new BigDecimal("425000.00");
            BigDecimal expectedPensionEnd = new BigDecimal("442000.00");
            BigDecimal expectedSavingsStart = new BigDecimal("74000.00");
            BigDecimal expectedSavingsEnd = new BigDecimal("51000.00");
            BigDecimal expectedTotalEnd = new BigDecimal("493000.00");
            assertWealth(
                    w,
                    expectedAge,
                    expectedPensionStart,
                    expectedPensionEnd,
                    expectedSavingsStart,
                    expectedSavingsEnd,
                    expectedTotalEnd
            );
        }

        // Age 65 snapshot (index 4): savings insufficient -> lump sum already taken in age 64
        {
            Wealth w = timeline[4];
            int expectedAge = 65;
            BigDecimal expectedPensionStart = new BigDecimal("372892.42");
            BigDecimal expectedPensionEnd = new BigDecimal("387808.11");
            BigDecimal expectedSavingsStart = new BigDecimal("101516.80");
            BigDecimal expectedSavingsEnd = new BigDecimal("78516.80");
            BigDecimal expectedTotalEnd = new BigDecimal("466324.91");
            assertWealth(
                    w,
                    expectedAge,
                    expectedPensionStart,
                    expectedPensionEnd,
                    expectedSavingsStart,
                    expectedSavingsEnd,
                    expectedTotalEnd
            );
        }

        // Age 67 snapshot (index 6): state pension reduces net need to 11,027; no pension draw required yet
        {
            Wealth w = timeline[6];
            int expectedAge = 67;
            BigDecimal expectedPensionStart = new BigDecimal("403320.44");
            BigDecimal expectedPensionEnd = new BigDecimal("419453.25");
            BigDecimal expectedSavingsStart = new BigDecimal("55516.80");
            BigDecimal expectedSavingsEnd = new BigDecimal("44489.80");
            BigDecimal expectedTotalEnd = new BigDecimal("463943.05");
            assertWealth(
                    w,
                    expectedAge,
                    expectedPensionStart,
                    expectedPensionEnd,
                    expectedSavingsStart,
                    expectedSavingsEnd,
                    expectedTotalEnd
            );
        }

        // Age 72 snapshot (index 11): savings depleted; withdraw from pension with tax computation
        {
            Wealth w = timeline[11];
            int expectedAge = 72;
            BigDecimal expectedPensionStart = new BigDecimal("490700.98");
            BigDecimal expectedPensionEnd = new BigDecimal("496645.48");
            BigDecimal expectedSavingsStart = new BigDecimal("381.80");
            BigDecimal expectedSavingsEnd = new BigDecimal("0.00");
            BigDecimal expectedTotalEnd = new BigDecimal("496645.48");
            assertWealth(
                    w,
                    expectedAge,
                    expectedPensionStart,
                    expectedPensionEnd,
                    expectedSavingsStart,
                    expectedSavingsEnd,
                    expectedTotalEnd
            );
        }

        // Age 80 snapshot (index 19)
        {
            Wealth w = timeline[19];
            int expectedAge = 80;
            BigDecimal expectedPensionStart = new BigDecimal("541554.70");
            BigDecimal expectedPensionEnd = new BigDecimal("549037.01");
            BigDecimal expectedSavingsStart = new BigDecimal("0.00");
            BigDecimal expectedSavingsEnd = new BigDecimal("0.00");
            BigDecimal expectedTotalEnd = new BigDecimal("549037.01");
            assertWealth(
                    w,
                    expectedAge,
                    expectedPensionStart,
                    expectedPensionEnd,
                    expectedSavingsStart,
                    expectedSavingsEnd,
                    expectedTotalEnd
            );
        }

        // Age 99 snapshot (index 38)
        {
            Wealth w = timeline[38];
            int expectedAge = 99;
            BigDecimal expectedPensionStart = new BigDecimal("748599.36");
            BigDecimal expectedPensionEnd = new BigDecimal("764363.46");
            BigDecimal expectedSavingsStart = new BigDecimal("0.00");
            BigDecimal expectedSavingsEnd = new BigDecimal("0.00");
            BigDecimal expectedTotalEnd = new BigDecimal("764363.46");
            assertWealth(
                    w,
                    expectedAge,
                    expectedPensionStart,
                    expectedPensionEnd,
                    expectedSavingsStart,
                    expectedSavingsEnd,
                    expectedTotalEnd
            );
        }
    }

    @Test
    @DisplayName("strategy2: uses savings first, then pension with 25% tax-free and 75% taxed at 20%")
    void strategy2_timeline_and_rules() {
        var timeline = service.strategy2(
                INITIAL_SAVINGS,
                INITIAL_PENSION,
                ANNUAL_REQUIRED_NET
        );

        // Length: ages 61..99 inclusive
        assertEquals(39, timeline.length);
        // Log the full timeline for visibility (includes taxPaid)
        log.info("age, savingsStart, pensionStart, savingsEnd, pensionEnd, taxPaid, totalWealthEnd");
        for (Wealth w : timeline) {
            log.info("{},{},{},{},{},{},{}",
                    w.getAge(), w.getSavingsStart(), w.getPensionStart(), w.getSavingsEnd(), w.getPensionEnd(), w.getTaxPaid(), w.totalEnd());
        }

        // Age 61 snapshot (same as strategy1: spend from savings only)
        {
            Wealth w = timeline[0];
            assertWealth(
                    w,
                    61,
                    new BigDecimal("425000.00"),
                    new BigDecimal("442000.00"),
                    new BigDecimal("74000.00"),
                    new BigDecimal("51000.00"),
                    new BigDecimal("493000.00")
            );
            assertEquals(new BigDecimal("0.00"), w.getTaxPaid());
        }

        // Age 62 snapshot: still spending savings only; pension compounds
        {
            Wealth w = timeline[1];
            assertWealth(
                    w,
                    62,
                    new BigDecimal("442000.00"),
                    new BigDecimal("459680.00"),
                    new BigDecimal("51000.00"),
                    new BigDecimal("28000.00"),
                    new BigDecimal("487680.00")
            );
            assertEquals(new BigDecimal("0.00"), w.getTaxPaid());
        }

        // Age 63 snapshot: last year before savings deplete
        {
            Wealth w = timeline[2];
            assertWealth(
                    w,
                    63,
                    new BigDecimal("459680.00"),
                    new BigDecimal("478067.20"),
                    new BigDecimal("28000.00"),
                    new BigDecimal("5000.00"),
                    new BigDecimal("483067.20")
            );
            assertEquals(new BigDecimal("0.00"), w.getTaxPaid());
        }

        // Age 64 snapshot: savings deplete and pension withdrawal occurs with tax
        {
            Wealth w = timeline[3];
            assertWealth(
                    w,
                    64,
                    new BigDecimal("478067.20"),
                    new BigDecimal("478242.32"),
                    new BigDecimal("5000.00"),
                    new BigDecimal("0.00"),
                    new BigDecimal("478242.32")
            );
            assertEquals(new BigDecimal("218.82"), w.getTaxPaid());
        }
    }

    @Test
    @DisplayName("Zero years returns the starting balance")
    void projectBalance_zeroYears_returnsStartingBalance() {
        BigDecimal result = service.projectBalance(new BigDecimal("1000.00"), new BigDecimal("5"), 0);
        assertEquals(new BigDecimal("1000.00"), result);
    }

    @Test
    @DisplayName("Zero rate returns the starting balance even after multiple years")
    void projectBalance_zeroRate_returnsStartingBalance() {
        BigDecimal result = service.projectBalance(new BigDecimal("500.00"), new BigDecimal("0"), 10);
        assertEquals(new BigDecimal("500.00"), result);
    }

    @Test
    @DisplayName("Positive rate compounds annually (example: 1000 @ 5% for 2 years)")
    void projectBalance_positiveRate_twoYears_compoundsCorrectly() {
        // 1000 * 1.05^2 = 1102.5 -> 1102.50 after rounding to 2 dp
        BigDecimal result = service.projectBalance(new BigDecimal("1000.00"), new BigDecimal("5"), 2);
        assertEquals(new BigDecimal("1102.50"), result);
    }

    @Test
    @DisplayName("Rounding uses HALF_UP to 2 decimals")
    void projectBalance_rounding_halfUp() {
        // 100 * (1 + 0.02345) = 102.345 -> 102.35 after HALF_UP rounding to 2 dp
        BigDecimal result = service.projectBalance(new BigDecimal("100.00"), new BigDecimal("2.345"), 1);
        assertEquals(new BigDecimal("102.35"), result);
    }

    @Test
    @DisplayName("Negative years throws IllegalArgumentException")
    void projectBalance_negativeYears_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                service.projectBalance(new BigDecimal("100.00"), new BigDecimal("5"), -1));
    }

    @Test
    @DisplayName("Negative starting balance throws IllegalArgumentException")
    void projectBalance_negativeStartingBalance_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                service.projectBalance(new BigDecimal("-0.01"), new BigDecimal("5"), 1));
    }

    @Test
    @DisplayName("Null starting balance throws NullPointerException")
    void projectBalance_nullStartingBalance_throwsNPE() {
        assertThrows(NullPointerException.class, () ->
                service.projectBalance(null, new BigDecimal("5"), 1));
    }

    @Test
    @DisplayName("Null annual rate throws NullPointerException")
    void projectBalance_nullRate_throwsNPE() {
        assertThrows(NullPointerException.class, () ->
                service.projectBalance(new BigDecimal("100.00"), null, 1));
    }

    private void assertWealth(Wealth w,
                              int expectedAge,
                              BigDecimal expectedPensionStart,
                              BigDecimal expectedPensionEnd,
                              BigDecimal expectedSavingsStart,
                              BigDecimal expectedSavingsEnd,
                              BigDecimal expectedTotalEnd) {
        assertEquals(expectedAge, w.getAge());
        assertEquals(expectedPensionStart, w.getPensionStart());
        assertEquals(expectedPensionEnd, w.getPensionEnd());
        assertEquals(expectedSavingsStart, w.getSavingsStart());
        assertEquals(expectedSavingsEnd, w.getSavingsEnd());
        assertEquals(expectedTotalEnd, w.totalEnd());
    }


}
