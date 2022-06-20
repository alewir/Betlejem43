package me.ugeno.betlejem.lcalc;

import java.math.BigDecimal;

/**
 * Created by alwi on 25/10/2017.
 * All rights reserved.
 */
class BalanceInternal {
    private static final int BASE_PRI = 10000;
    private static final int BASE_SEC = 100000000;

    private final Balance parent;
    private final Op operation;

    private long subPri;
    private long subSec;
    private double pri;
    private double sec;
    private double price;

    BalanceInternal(Balance parent, Op operation) {
        this.parent = parent;
        this.operation = operation;
    }

    public static double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, BigDecimal.ROUND_FLOOR).doubleValue();
    }

    /**
     * For rounding purposes - used to compare between balances.
     */
    void updateValue(double pri, double sec, Double price) {
        this.pri = pri;
        this.sec = sec;
        this.price = price;

        this.subPri = (long) (pri * BASE_PRI); // e.g. 1 cent = 0.01 USD
        this.subSec = (long) (sec * BASE_SEC); // e.g. 1 Satoshi = 0.00000001 BTC, stocks = 1 [piece]
    }

    boolean isAcceptable() {
        return getSubPri() >= 0 && getSubSec() >= 0;
    }

    public Op getOperation() {
        return operation;
    }

    public Balance getParent() {
        return parent;
    }

    public double getPrice() {
        return price;
    }

    public double getPri() {
        return pri;
    }

    public double getSec() {
        return sec;
    }

    long getSubPri() {
        return subPri;
    }

    long getSubSec() {
        return subSec;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BalanceInternal that = (BalanceInternal) o;

        return subPri == that.subPri && subSec == that.subSec;
    }

    @Override
    public int hashCode() {
        int result = (int) (subPri ^ (subPri >>> 32));
        result = 31 * result + (int) (subSec ^ (subSec >>> 32));
        return result;
    }
}
