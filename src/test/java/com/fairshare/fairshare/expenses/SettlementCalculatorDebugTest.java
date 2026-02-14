package com.fairshare.fairshare.expenses;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class SettlementCalculatorDebugTest {

    @Test
    void dumpCompute() {
        Map<Long, BigDecimal> net = Map.of(
                1L, new BigDecimal("20.00"),
                2L, new BigDecimal("-10.00"),
                3L, new BigDecimal("-10.00")
        );

        List<SettlementCalculator.Transfer> tx = SettlementCalculator.compute(net);
        System.out.println("Transfers size: " + tx.size());
        for (var t : tx) System.out.println(t.fromUserId() + " -> " + t.toUserId() + " : " + t.amount());
    }
}
