package com.gillianbc.pensions.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
class PensionServiceBasicsTest {

    private static final BigDecimal INITIAL_SAVINGS = new BigDecimal("74000.00");
    private static final BigDecimal INITIAL_PENSION = new BigDecimal("425000.00");
    private static final BigDecimal ANNUAL_REQUIRED_NET = new BigDecimal("23000.00");

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

}
