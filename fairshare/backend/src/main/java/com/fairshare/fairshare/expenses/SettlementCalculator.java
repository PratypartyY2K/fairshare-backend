package com.fairshare.fairshare.expenses;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public final class SettlementCalculator {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal EPS = new BigDecimal("0.005"); // half-cent tolerance

    private SettlementCalculator() {
    }

    public static List<Transfer> compute(Map<Long, BigDecimal> netBalances) {
        // debtors: negative balances; creditors: positive balances
        List<Map.Entry<Long, BigDecimal>> debtors = new ArrayList<>();
        List<Map.Entry<Long, BigDecimal>> creditors = new ArrayList<>();

        for (var e : netBalances.entrySet()) {
            BigDecimal v = scale2(e.getValue());
            if (v.compareTo(ZERO) < 0) debtors.add(Map.entry(e.getKey(), v));
            else if (v.compareTo(ZERO) > 0) creditors.add(Map.entry(e.getKey(), v));
        }

        // Stable ordering: biggest amounts first helps reduce transactions
        // Tie-break by userId ascending to ensure deterministic ordering when values are equal
        Comparator<Map.Entry<Long, BigDecimal>> byValueThenIdAsc = Comparator
                .<Map.Entry<Long, BigDecimal>, BigDecimal>comparing(Map.Entry::getValue)
                .thenComparing(Map.Entry::getKey);

        debtors.sort(byValueThenIdAsc);                 // ascending (more negative first)
        creditors.sort(byValueThenIdAsc.reversed()); // descending


        int i = 0, j = 0;
        List<Transfer> out = new ArrayList<>();

        while (i < debtors.size() && j < creditors.size()) {
            Long debtorId = debtors.get(i).getKey();
            Long creditorId = creditors.get(j).getKey();

            BigDecimal debt = debtors.get(i).getValue().abs();     // amount debtor owes
            BigDecimal credit = creditors.get(j).getValue();       // amount creditor is owed

            BigDecimal pay = debt.min(credit);
            pay = scale2(pay);

            if (pay.compareTo(ZERO) > 0) {
                out.add(new Transfer(debtorId, creditorId, pay));
            }

            BigDecimal newDebt = debt.subtract(pay);
            BigDecimal newCredit = credit.subtract(pay);

            // advance pointers with tolerance
            if (newDebt.compareTo(EPS) <= 0) i++;
            else debtors.set(i, Map.entry(debtorId, newDebt.negate()));

            if (newCredit.compareTo(EPS) <= 0) j++;
            else creditors.set(j, Map.entry(creditorId, newCredit));
        }

        return out;
    }

    private static BigDecimal scale2(BigDecimal x) {
        return x.setScale(2, RoundingMode.HALF_UP);
    }

    public record Transfer(Long fromUserId, Long toUserId, BigDecimal amount) {
    }
}
