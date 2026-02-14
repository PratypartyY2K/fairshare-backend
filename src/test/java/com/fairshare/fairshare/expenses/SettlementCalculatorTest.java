package com.fairshare.fairshare.expenses;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SettlementCalculatorTest {

    @Test
    void computesSimpleSettlement() {
        // User1 +20, User2 -10, User3 -10
        Map<Long, BigDecimal> net = Map.of(
                1L, new BigDecimal("20.00"),
                2L, new BigDecimal("-10.00"),
                3L, new BigDecimal("-10.00")
        );

        List<SettlementCalculator.Transfer> tx = SettlementCalculator.compute(net);

        assertEquals(2, tx.size());
        assertEquals(2L, tx.get(0).fromUserId());
        assertEquals(1L, tx.get(0).toUserId());
        assertEquals(new BigDecimal("10.00"), tx.get(0).amount());

        assertEquals(3L, tx.get(1).fromUserId());
        assertEquals(1L, tx.get(1).toUserId());
        assertEquals(new BigDecimal("10.00"), tx.get(1).amount());
    }

    @Test
    void handlesRoundingSafely() {
        Map<Long, BigDecimal> net = Map.of(
                1L, new BigDecimal("0.01"),
                2L, new BigDecimal("-0.01")
        );

        var tx = SettlementCalculator.compute(net);
        assertEquals(1, tx.size());
        assertEquals(new BigDecimal("0.01"), tx.get(0).amount());
    }
}
