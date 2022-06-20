package me.ugeno.betlejem.lcalc.tradebot.binance;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.springframework.lang.NonNull;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static me.ugeno.betlejem.lcalc.tradebot.binance.LcalcTradebot.scaled;

/**
 * Created by alwi on 04/01/2022.
 * All rights reserved.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class TradebotConfig {
    @NonNull
    private final BigDecimal wealthChange;

    @NonNull
    private final BigDecimal minGainDirect;

    @NonNull
    private final BigDecimal minGainReverse;

    @NonNull
    private final BigDecimal certaintyLvlDirect;

    @NonNull
    private final BigDecimal certaintyLvlReverse;

    @NonNull
    private int txAmount;

    @JsonCreator
    public TradebotConfig(BigDecimal minGainDirect, BigDecimal minGainReverse, BigDecimal certaintyLvlDirect, BigDecimal certaintyLvlReverse, BigDecimal wealthChange, int txAmount) {
        this.minGainDirect = minGainDirect;
        this.minGainReverse = minGainReverse;
        this.certaintyLvlDirect = certaintyLvlDirect;
        this.certaintyLvlReverse = certaintyLvlReverse;
        this.wealthChange = wealthChange;
        this.txAmount = txAmount;
    }

    @NonNull
    public BigDecimal getWealthChange() {
        return wealthChange;
    }

    @NonNull
    public BigDecimal getMinGainDirect() {
        return minGainDirect.setScale(3, RoundingMode.HALF_EVEN);
    }

    @NonNull
    public BigDecimal getMinGainReverse() {
        return minGainReverse.setScale(3, RoundingMode.HALF_EVEN);
    }

    @NonNull
    public BigDecimal getCertaintyLvlDirect() {
        return certaintyLvlDirect.setScale(2, RoundingMode.HALF_EVEN);
    }

    @NonNull
    public BigDecimal getCertaintyLvlReverse() {
        return certaintyLvlReverse.setScale(2, RoundingMode.HALF_EVEN);
    }

    @NonNull
    public int getTxAmount() {
        return txAmount;
    }

    @Override
    public String toString() {
        int percentageNumScale = 3;
        return String.format("Config: minGainDir=%s, minGainRev=%s, certLvlDir=%7.2f, certLvlRev=%7.2f, txAmount=%d, wealthChange=%5.3f",
                scaled(minGainDirect, percentageNumScale),
                scaled(minGainReverse, percentageNumScale),
                certaintyLvlDirect,
                certaintyLvlReverse,
                txAmount,
                wealthChange.doubleValue()
        );
    }
}
