package me.ugeno.betlejem.lcalc.tradebot.binance;

import com.fasterxml.jackson.annotation.JsonCreator;
import me.ugeno.betlejem.common.utils.BetlejemException;
import org.springframework.lang.NonNull;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static me.ugeno.betlejem.lcalc.tradebot.binance.LcalcTradebot.OP_DIRECT;
import static me.ugeno.betlejem.lcalc.tradebot.binance.LcalcTradebot.OP_REVERSE;
import static me.ugeno.betlejem.lcalc.tradebot.binance.LcalcTradebot.SCALE_FOR_DIV;
import static me.ugeno.betlejem.lcalc.tradebot.binance.LcalcTradebot.scaled;

/**
 * Created by alwi on 12/12/2021.
 * All rights reserved.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
class Tx {
    @NonNull
    private final String operation;

    @NonNull
    private final BigDecimal tradedSec;

    @NonNull
    private final BigDecimal priceInSec;

    @NonNull
    private int scaleSec;

    @NonNull
    private int scalePri;

    @JsonCreator
    public Tx(String operation, BigDecimal tradedSec, BigDecimal priceInSec, int scalePri, int scaleSec) {
        this.operation = operation;
        this.tradedSec = scaled(tradedSec, scaleSec);
        this.priceInSec = scaled(priceInSec, scaleSec);
        this.scalePri = scalePri;
        this.scaleSec = scaleSec;
    }

    @NonNull
    public String getOperation() {
        return operation;
    }

    @NonNull
    public BigDecimal getTradedSec() {
        return tradedSec;
    }

    @NonNull
    public BigDecimal getPriceInSec() {
        return priceInSec;
    }

    @NonNull
    public int getScaleSec() {
        return scaleSec;
    }

    @NonNull
    public int getScalePri() {
        return scalePri;
    }

    BigDecimal getGainForPrice(BigDecimal currentPriceInSec) {
        if (operation.equals(OP_REVERSE)) {
            BigDecimal priAmountBought = tradedSec.divide(priceInSec, SCALE_FOR_DIV, RoundingMode.HALF_EVEN);
            BigDecimal priAmountCurrentValueInSec = priAmountBought.multiply(currentPriceInSec);
            return (priAmountCurrentValueInSec.subtract(tradedSec)).divide(tradedSec, SCALE_FOR_DIV, RoundingMode.HALF_EVEN);
        } else if (operation.equals(OP_DIRECT)) {
            BigDecimal priAmountSold = tradedSec.divide(priceInSec, SCALE_FOR_DIV, RoundingMode.HALF_EVEN);
            BigDecimal priAmountPossibleToGetNow = tradedSec.divide(currentPriceInSec, SCALE_FOR_DIV, RoundingMode.HALF_EVEN);
            return (priAmountPossibleToGetNow.subtract(priAmountSold)).divide(priAmountSold, SCALE_FOR_DIV, RoundingMode.HALF_EVEN);
        } else {
            throw new BetlejemException(String.format("%s not supported.", operation));
        }
    }

    @Override
    public String toString() {
        return "Tx{" +
                "operation='" + operation + '\'' +
                ", tradedSec=" + tradedSec +
                ", priceInSec=" + priceInSec +
                '}';
    }
}
