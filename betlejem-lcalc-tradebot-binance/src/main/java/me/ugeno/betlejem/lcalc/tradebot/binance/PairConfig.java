package me.ugeno.betlejem.lcalc.tradebot.binance;

import java.math.BigDecimal;

/**
 * Created by alwi on 16/01/2022.
 * All rights reserved.
 */
class PairConfig {
    private final String symbolPri;
    private final String symbolSec;
    private final int scalePri;
    private final int scaleSec;
    private BigDecimal minTxOrderInSec;

    PairConfig(String symbolPri, String symbolSec, int scalePri, int scaleSec, BigDecimal minTxOrderInSec) {
        this.symbolPri = symbolPri;
        this.symbolSec = symbolSec;
        this.scalePri = scalePri;
        this.scaleSec = scaleSec;
        this.minTxOrderInSec = minTxOrderInSec;
    }

    String getSymbolPri() {
        return symbolPri;
    }

    String getSymbolSec() {
        return symbolSec;
    }

    int getScalePri() {
        return scalePri;
    }

    int getScaleSec() {
        return scaleSec;
    }

    String getPairName() {
        return getSymbolPri() + getSymbolSec();
    }

    BigDecimal minOrderInSec() {
        return minTxOrderInSec;
    }
}
