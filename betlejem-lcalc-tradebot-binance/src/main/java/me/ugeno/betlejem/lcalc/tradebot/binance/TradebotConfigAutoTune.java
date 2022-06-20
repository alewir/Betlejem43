package me.ugeno.betlejem.lcalc.tradebot.binance;

import me.ugeno.betlejem.common.crypto.kafka.DataProviderKafka;
import me.ugeno.betlejem.common.crypto.kafka.EvalDeserializer;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Created by alwi on 04/01/2022.
 * All rights reserved.
 */
class TradebotConfigAutoTune {
    private static final Logger LOG = LoggerFactory.getLogger(TradebotConfigAutoTune.class);
    private static final BigDecimal LONG_MIN = BigDecimal.valueOf(Long.MIN_VALUE);

    private BigDecimal currentMaxAvg = LONG_MIN;

    private PairConfig pairConfig;

    TradebotConfigAutoTune(PairConfig pairConfig) {
        this.pairConfig = pairConfig;
    }

    Optional<TradebotConfig> tuneConfig(BigDecimal balancePri, BigDecimal balanceSec, String evalTopic, int simIncludeRecentMinutes, int minDataRequired) {
        currentMaxAvg = LONG_MIN;

        DateTime start = DateTime.now();
        DataProviderKafka dataProviderKafka = new DataProviderKafka();
        List<String[]> predictions = dataProviderKafka.fetchRecentFrom(evalTopic, simIncludeRecentMinutes, new EvalDeserializer());
        int predictionsAmount = predictions.size();
        LOG.info("  " + pairConfig.getPairName() + " AutoTune config start: {} for DATASET length: {}", start.toLocalTime(), predictionsAmount);
        if (predictionsAmount - minDataRequired < 0) { // skip if having predictions from less than XXX hours
            LOG.debug("Too little data to tune config: {}", predictionsAmount);
            return Optional.empty();
        }

        double minGainMinDirect = .010; // NOTE: observed max change in 30 min is ~2.5%
        double minGainMaxDirect = .085;
        double minGainMinReverse = .010;
        double minGainMaxReverse = .085;
        double minGainStep = .003;

        double requiredCertaintyDirectMin = .40;
        double requiredCertaintyDirectMax = .85;
        double requiredCertaintyReverseMin = .40;
        double requiredCertaintyReverseMax = .85;
        double requiredCertaintyStep = .05;

        int iteration = 0;
        Optional<TradebotConfig> lastConfig = Optional.empty();
        LOG.debug(String.format("iteration: %10d", iteration));
        for (double minGainDirect = minGainMaxDirect; minGainDirect >= minGainMinDirect; minGainDirect -= minGainStep) {
            for (double minGainReverse = minGainMaxReverse; minGainReverse >= minGainMinReverse; minGainReverse -= minGainStep) {
                for (double certaintyLvlDirect = requiredCertaintyDirectMax; certaintyLvlDirect >= requiredCertaintyDirectMin; certaintyLvlDirect -= requiredCertaintyStep) {
                    for (double certaintyLvlReverse = requiredCertaintyReverseMax; certaintyLvlReverse >= requiredCertaintyReverseMin; certaintyLvlReverse -= requiredCertaintyStep) {
                        Optional<TradebotConfig> config = check(predictions, certaintyLvlDirect, certaintyLvlReverse, minGainDirect, minGainReverse, iteration, balancePri, balanceSec);
                        lastConfig = config.isEmpty() ? lastConfig : config;
                        iteration++;
                    }
                }
            }
        }

        DateTime finish = DateTime.now();
        LOG.debug("Finish: {}", finish.toLocalTime());
        LOG.info("      {} Config tuning took: {} minutes - checked amount of combinations: {} on {} predictions", pairConfig.getPairName(), new Duration(start.toInstant(), finish.toInstant()).toPeriod().getMinutes(), iteration, predictionsAmount);
        return lastConfig;
    }

    private Optional<TradebotConfig> check(List<String[]> pastPredictions,
                                           double certaintyLvlDirect,
                                           double certaintyLvlReverse,
                                           double minGainDirect,
                                           double minGainReverse,
                                           int index,
                                           BigDecimal initBalancePri,
                                           BigDecimal initBalanceSec) {

        BigDecimal latestPrice = extractLatestPrice(pastPredictions);
        BigDecimal certaintyLvlDirectBd = BigDecimal.valueOf(certaintyLvlDirect);
        BigDecimal certaintyLvlReverseBd = BigDecimal.valueOf(certaintyLvlReverse);
        BigDecimal minGainDirectBd = BigDecimal.valueOf(minGainDirect);
        BigDecimal minGainReverseBd = BigDecimal.valueOf(minGainReverse);

        LcalcTradebot lcalcBot = new LcalcTradebot(pairConfig, true,
                certaintyLvlDirectBd,
                certaintyLvlReverseBd,
                minGainDirectBd,
                minGainReverseBd,
                initBalancePri,
                initBalanceSec,
                latestPrice);
        for (String[] prediction : pastPredictions) {
            recalculate(lcalcBot, prediction);
        }

        BigDecimal wealthChange = lcalcBot.getTxAmount() == 0 ? LONG_MIN : lcalcBot.getWealthChange();
        if (wealthChange.compareTo(currentMaxAvg) > 0) {
            currentMaxAvg = wealthChange;
            TradebotConfig bestConfig = new TradebotConfig(minGainDirectBd, minGainReverseBd, certaintyLvlDirectBd, certaintyLvlReverseBd, wealthChange, lcalcBot.getTxAmount());

            @SuppressWarnings("StringBufferReplaceableByString") StringBuilder sb = new StringBuilder();
            sb.append(String.format("iteration: %10d, ", index));
            sb.append(String.format("minGainDirect: %5.3f, ", minGainDirect));
            sb.append(String.format("minGainReverse: %5.3f, ", minGainReverse));
            sb.append(String.format("certaintyLvlDirect: %5.2f, ", certaintyLvlDirect));
            sb.append(String.format("certaintyLvlReverse: %5.2f, ", certaintyLvlReverse));
            sb.append(String.format("txAmount: %d, ", lcalcBot.getTxAmount()));
            sb.append(String.format("RESULT: %7.2f USD (%+5.1f%% @ price %+5.1f%%)", lcalcBot.getWealth().doubleValue(), wealthChange.doubleValue(), lcalcBot.getPriceChange()));
            LOG.debug(sb.toString());

            return Optional.of(bestConfig);
        }

        return Optional.empty();
    }

    static BigDecimal extractLatestPrice(List<String[]> pastPredictions) {
        return new BigDecimal(pastPredictions.get(pastPredictions.size() - 1)[1]);
    }

    private void recalculate(LcalcTradebot lcalcBot, String[] prediction) {
        String date = prediction[0];
        double price = Double.parseDouble(prediction[1]);
        double sell = Double.parseDouble(prediction[2]);
        double pass = Double.parseDouble(prediction[3]);
        double buy = Double.parseDouble(prediction[4]);
        lcalcBot.receive(date, sell, pass, buy, price);
    }
}
