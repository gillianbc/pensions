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

    private final PensionService service = new PensionService();

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

    @Test
    @DisplayName("strategy1: returns ages 61..99 and applies savings-first, one-time 25% lump sum, and taxation")
    void strategy1_timeline_and_rules() {
        var timeline = service.strategy1(
                new BigDecimal("74000.00"),
                new BigDecimal("425000.00"),
                new BigDecimal("23000.00")
        );

        // Length: ages 61..99 inclusive
        assertEquals(39, timeline.length);
        // Log the full timeline for visibility
        for (Wealth w : timeline) {
            log.info("age={} savingsStart={} pensionStart={} savingsEnd={} pensionEnd={} totalWealthEnd {}",
                    w.getAge(), w.getSavingsStart(), w.getPensionStart(), w.getSavingsEnd(), w.getPensionEnd(), w.totalEnd());
        }

        // Age 61 snapshot (index 0): spend from savings only
        {
            Wealth w = timeline[0];
            int expectedAge = 61;
            BigDecimal expectedPensionStart = new BigDecimal("400000.00");
            BigDecimal expectedPensionEnd = new BigDecimal("400000.00");
            BigDecimal expectedSavingsStart = new BigDecimal("94000.00");
            BigDecimal expectedSavingsEnd = new BigDecimal("71000.00");
            BigDecimal expectedTotalEnd = new BigDecimal("471000.00");
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

        // Age 65 snapshot (index 4): savings insufficient -> take 25% lump sum (100,000) from pension, then spend remainder
        {
            Wealth w = timeline[4];
            int expectedAge = 65;
            BigDecimal expectedPensionStart = new BigDecimal("400000.00");
            BigDecimal expectedPensionEnd = new BigDecimal("300000.00");
            BigDecimal expectedSavingsStart = new BigDecimal("2000.00");
            BigDecimal expectedSavingsEnd = new BigDecimal("81000.00");
            BigDecimal expectedTotalEnd = new BigDecimal("381000.00");
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
            BigDecimal expectedPensionStart = new BigDecimal("300000.00");
            BigDecimal expectedPensionEnd = new BigDecimal("300000.00");
            BigDecimal expectedSavingsStart = new BigDecimal("58000.00");
            BigDecimal expectedSavingsEnd = new BigDecimal("46973.00");
            BigDecimal expectedTotalEnd = new BigDecimal("346973.00");
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
            BigDecimal expectedPensionStart = new BigDecimal("300000.00");
            BigDecimal expectedPensionEnd = new BigDecimal("289946.75");
            BigDecimal expectedSavingsStart = new BigDecimal("2865.00");
            BigDecimal expectedSavingsEnd = new BigDecimal("0.00");
            BigDecimal expectedTotalEnd = new BigDecimal("289946.75");
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
