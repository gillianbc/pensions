package com.gillianbc.pensions.service;

import com.gillianbc.pensions.model.Wealth;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@TestMethodOrder(OrderAnnotation.class)
class PensionServiceTest {

    //  Asserts will only work as checks for 23000, initial savings 74K, initial pension 425K
    public static final String INITIAL_SAVINGS = "74000.00";
    public static final String INITIAL_PENSION = "425000.00";
    public static final String AMOUNT_REQUIRED_NET = "23000.00";

    private final PensionService service = new PensionService();

    @Test
    @Order(10)
    @DisplayName("strategy1: returns ages 61..99 and applies savings-first, one-time 25% lump sum, and taxation")
    void strategy1_timeline_and_rules() {
        var timeline = service.strategy1(
                new BigDecimal(INITIAL_SAVINGS),
                new BigDecimal(INITIAL_PENSION),
                new BigDecimal(AMOUNT_REQUIRED_NET)
        );

        // Length: ages 61..99 inclusive
        assertEquals(39, timeline.length);

        // Log the full timeline for visibility
        logTimeline("Strategy1: applies savings-first - one-time 25% lump sum", timeline);

        if (AMOUNT_REQUIRED_NET.equals("23000.00")) {
            // I've only sorted out the assertions for 23000, initial savings 74K, initial pension 425K - worth keeping though as a check
            // Age 61 snapshot (index 0): spend from savings only
            {
                Wealth w = timeline[0];
                int expectedAge = 61;
                BigDecimal expectedPensionStart = new BigDecimal(INITIAL_PENSION);
                BigDecimal expectedPensionEnd = new BigDecimal("442000.00");
                BigDecimal expectedSavingsStart = new BigDecimal(INITIAL_SAVINGS);
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
    }

    @Test
    @Order(20)
    @DisplayName("strategy2: uses savings first, then pension with 25% tax-free and 75% taxed at 20%")
    void strategy2_timeline_and_rules() {
        var timeline = service.strategy2(
                new BigDecimal(INITIAL_SAVINGS),
                new BigDecimal(INITIAL_PENSION),
                new BigDecimal(AMOUNT_REQUIRED_NET)
        );

        // Length: ages 61..99 inclusive
        assertEquals(39, timeline.length);

        // Log the full timeline for visibility (includes taxPaid)
        logTimeline("Strategy2: uses savings first. Then use pension with 25% tax-free and 75% taxed at 20%", timeline);

        if (AMOUNT_REQUIRED_NET.equals("23000.00")) {
            // Age 61 snapshot (same as strategy1: spend from savings only)
            {
                Wealth w = timeline[0];
                assertWealth(
                        w,
                        61,
                        new BigDecimal(INITIAL_PENSION),
                        new BigDecimal("442000.00"),
                        new BigDecimal(INITIAL_SAVINGS),
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
    }

    @Test
    @Order(30)
    @DisplayName("strategy3: use max tax-free drawdown of £16760 in early years and use savings for the remainder of the required amount")
    void strategy3_timeline_and_rules() {
        var timeline = service.strategy3(
                new BigDecimal(INITIAL_SAVINGS),
                new BigDecimal(INITIAL_PENSION),
                new BigDecimal(AMOUNT_REQUIRED_NET)
        );

        // Length: ages 61..99 inclusive
        assertEquals(39, timeline.length);

        // Log the full timeline for visibility (includes taxPaid)
        logTimeline("Strategy3: use max tax-free drawdown of £16760 in early years and use savings for the remainder of the required amount", timeline);

        if (AMOUNT_REQUIRED_NET.equals("23000.00")) {
            // Age 61 snapshot: withdraw 16,760 from pension (0 tax), remainder 6,240 from savings
            {
                Wealth w = timeline[0];
                int expectedAge = 61;
                BigDecimal expectedPensionStart = new BigDecimal(INITIAL_PENSION);
                BigDecimal expectedPensionEnd = new BigDecimal("424569.60");
                BigDecimal expectedSavingsStart = new BigDecimal(INITIAL_SAVINGS);
                BigDecimal expectedSavingsEnd = new BigDecimal("67760.00");
                BigDecimal expectedTotalEnd = new BigDecimal("492329.60");
                assertWealth(
                        w,
                        expectedAge,
                        expectedPensionStart,
                        expectedPensionEnd,
                        expectedSavingsStart,
                        expectedSavingsEnd,
                        expectedTotalEnd
                );
                assertEquals(new BigDecimal("0.00"), w.getTaxPaid());
            }
        }
    }

    @Test
    @Order(35)
    @DisplayName("strategy3A: use max tax-free drawdown of £16760 in early years and use savings for the remainder of the required amount.  " +
            "Pay £3600 into pension")
    void strategy3A_timeline_and_rules() {
        var timeline = service.strategy3A(
                new BigDecimal(INITIAL_SAVINGS),
                new BigDecimal(INITIAL_PENSION),
                new BigDecimal(AMOUNT_REQUIRED_NET)
        );

        // Length: ages 61..99 inclusive
        assertEquals(39, timeline.length);

        // Log the full timeline for visibility (includes taxPaid)
        logTimeline("Strategy3A: use max tax-free drawdown of £16760 in early years and use savings for the remainder of the required amount.  Pay £3600 into pension", timeline);

        if (AMOUNT_REQUIRED_NET.equals("23000.00")) {
            // Age 61 snapshot:
            {
                Wealth w = timeline[0];
                int expectedAge = 61;
                BigDecimal expectedPensionStart = new BigDecimal(INITIAL_PENSION);
                BigDecimal expectedPensionEnd = new BigDecimal("428313.60");
                BigDecimal expectedSavingsStart = new BigDecimal(INITIAL_SAVINGS);
                BigDecimal expectedSavingsEnd = new BigDecimal("64160.00");
                BigDecimal expectedTotalEnd = new BigDecimal("492473.60");
                assertWealth(
                        w,
                        expectedAge,
                        expectedPensionStart,
                        expectedPensionEnd,
                        expectedSavingsStart,
                        expectedSavingsEnd,
                        expectedTotalEnd
                );
                assertEquals(new BigDecimal("0.00"), w.getTaxPaid());
            }
        }
    }

    @Test
    @Order(40)
    @DisplayName("strategy4: fill basic-rate band; surplus net added to savings")
    void strategy4_timeline_and_rules() {
        var timeline = service.strategy4(
                new BigDecimal(INITIAL_SAVINGS),
                new BigDecimal(INITIAL_PENSION),
                new BigDecimal(AMOUNT_REQUIRED_NET)
        );

        // Length: ages 61..99 inclusive
        assertEquals(39, timeline.length);

        // Log the full timeline for visibility
        logTimeline("Strategy4: fill basic-rate band; surplus net added to savings", timeline);

        if (AMOUNT_REQUIRED_NET.equals("23000.00")) {
            // Age 61 snapshot: zero-tax pension up to allowance, then fill basic-rate band; surplus goes to savings
            {
                Wealth w = timeline[0];
                int expectedAge = 61;
                BigDecimal expectedPensionStart = new BigDecimal(INITIAL_PENSION);
                BigDecimal expectedPensionEnd = new BigDecimal("372292.26");
                BigDecimal expectedSavingsStart = new BigDecimal(INITIAL_SAVINGS);
                BigDecimal expectedSavingsEnd = new BigDecimal("110486.67");
                BigDecimal expectedTotalEnd = new BigDecimal("482778.93");
                assertWealth(
                        w,
                        expectedAge,
                        expectedPensionStart,
                        expectedPensionEnd,
                        expectedSavingsStart,
                        expectedSavingsEnd,
                        expectedTotalEnd
                );
                assertEquals(new BigDecimal("7540.00"), w.getTaxPaid());
            }
        }
    }

    @Test
    @Order(50)
    @DisplayName("strategy5: phased UFPLS meets net need from pension first")
    void strategy5_timeline_and_rules() {
        var timeline = service.strategy5(
                new BigDecimal(INITIAL_SAVINGS),
                new BigDecimal(INITIAL_PENSION),
                new BigDecimal(AMOUNT_REQUIRED_NET)
        );

        // Length: ages 61..99 inclusive
        assertEquals(39, timeline.length);
        // Log the full timeline for visibility (includes taxPaid)
        logTimeline("Strategy5: phased UFPLS meets net need from pension first - (25% tax-free within each withdrawal)", timeline);

        if (AMOUNT_REQUIRED_NET.equals("23000.00")) {
            // Age 61 snapshot: UFPLS from pension to meet net need (tax on taxable portion above allowance); savings unchanged
            {
                Wealth w = timeline[0];
                int expectedAge = 61;
                BigDecimal expectedPensionStart = new BigDecimal(INITIAL_PENSION);
                BigDecimal expectedPensionEnd = new BigDecimal("416934.77");
                BigDecimal expectedSavingsStart = new BigDecimal(INITIAL_SAVINGS);
                BigDecimal expectedSavingsEnd = new BigDecimal(INITIAL_SAVINGS);
                BigDecimal expectedTotalEnd = new BigDecimal("490934.77");
                assertWealth(
                        w,
                        expectedAge,
                        expectedPensionStart,
                        expectedPensionEnd,
                        expectedSavingsStart,
                        expectedSavingsEnd,
                        expectedTotalEnd
                );
                assertEquals(new BigDecimal("1101.18"), w.getTaxPaid());
            }
        }
    }

    @Test
    @Order(100)
    @DisplayName("compares adding varying amounts of savings to pension then applying strategy 2 and 3")
    void savings_and_pension_additions_and_strategy_comparisons() {

        int transferred = 5000;

        showAge80(transferred);

        showAge80(transferred * 2);

        showAge80(transferred * 3);

        showAge80(transferred * 4);


    }

    private void showAge80(int transferred) {
        PensionService.TransferResult transferResult = service.contributeFromSavingsToPension(
                new BigDecimal(INITIAL_SAVINGS),
                new BigDecimal(INITIAL_PENSION),
                new BigDecimal(transferred),
                60,
                false);

        var timeline = service.strategy2(
                transferResult.savingsEnd,
                transferResult.pensionEnd,
                new BigDecimal(AMOUNT_REQUIRED_NET)
        );

        BigDecimal totalWealth80 = timeline[19].getSavingsEnd().add(timeline[19].getPensionEnd());
        log.info("Total wealth strategy 2 age 80 after savings contribution of {} and spend of {} is: {}",
                transferred, AMOUNT_REQUIRED_NET, totalWealth80);

        timeline = service.strategy3(
                transferResult.savingsEnd,
                transferResult.pensionEnd,
                new BigDecimal(AMOUNT_REQUIRED_NET)
        );

        totalWealth80 = timeline[19].getSavingsEnd().add(timeline[19].getPensionEnd());
        log.info("Total wealth strategy 3 age 80 after savings contribution of {} and spend of {} is: {}",
                transferred, AMOUNT_REQUIRED_NET, totalWealth80);
    }

    private static void logTimeline(String strategyDescription, Wealth[] timeline) {
        log.info("\n==== Savings {} Pension {} Required Income {} ====", new BigDecimal(INITIAL_SAVINGS), new BigDecimal(INITIAL_PENSION), new BigDecimal(AMOUNT_REQUIRED_NET));
        log.info("\n==== {} ====", strategyDescription);
        log.info("age, savingsStart, pensionStart, savingsEnd, pensionEnd, taxPaid, totalWealthEnd");
        for (Wealth w : timeline) {
//            if (w.getAge() == 61 || w.getAge() == 67 || w.getAge() == 80 || w.getAge() == 99)
            if (1 == 1)  // show all ages for now
                log.info("{},{},{},{},{},{},{}",
                        w.getAge(),
                        display(w.getSavingsStart()),
                        display(w.getPensionStart()),
                        display(w.getSavingsEnd()),
                        display(w.getPensionEnd()),
                        display(w.getTaxPaid()),
                        display(w.totalEnd()));
        }
    }

    // Sanitize BigDecimals for display/logging only: round to 2dp and suppress tiny +/-0.01 to 0.00
    private static BigDecimal display(BigDecimal v) {
        BigDecimal d = v.setScale(2, RoundingMode.HALF_UP);
        if (d.abs().compareTo(new BigDecimal("0.01")) < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return d;
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
