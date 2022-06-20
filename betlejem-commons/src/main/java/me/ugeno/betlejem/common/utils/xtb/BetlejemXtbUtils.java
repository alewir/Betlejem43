package me.ugeno.betlejem.common.utils.xtb;

import me.ugeno.betlejem.common.data.BuyForPredictionOrderInfo;
import me.ugeno.betlejem.common.data.Prediction;
import me.ugeno.betlejem.common.enc.Base64PropertyPasswordDecoder;
import org.joda.time.DateTimeConstants;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.xstore.api.message.codes.TRADE_OPERATION_CODE;
import pro.xstore.api.message.codes.TRADE_TRANSACTION_TYPE;
import pro.xstore.api.message.records.HoursRecord;
import pro.xstore.api.message.records.StepRecord;
import pro.xstore.api.message.records.SymbolRecord;
import pro.xstore.api.message.response.TradeTransactionResponse;
import pro.xstore.api.sync.ServerData;
import pro.xstore.api.sync.XtbCredentials;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.math.RoundingMode.DOWN;
import static me.ugeno.betlejem.common.utils.BetlejemProperties.getXtbValue;
import static me.ugeno.betlejem.common.utils.BetlejemUtils.cleanSymbolName;
import static me.ugeno.betlejem.common.utils.BetlejemUtils.isWeekend;
import static me.ugeno.betlejem.common.utils.BetlejemUtils.logError;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.BETLEJEM_XTB_LOGIN;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.BETLEJEM_XTB_PASSWORD;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.DAYS_BACK_TO_FRIDAY;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.FRESH_M5_MAX_MINUTES_BACK;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.GPW_MARKET_CLOSE_HOUR;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.GPW_MARKET_OPEN_HOUR;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.PLN_MAX_TOTAL_VAL;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.PLN_MIN_TOTAL_VAL;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.USD_MAX_TOTAL_VAL;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.USD_MIN_TOTAL_VAL;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.US_MARKET_CLOSE_HOUR;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.US_MARKET_OPEN_HOUR;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.VALUABLE_AVG_PRED_VAL;
import static org.joda.time.LocalDateTime.now;
import static pro.xstore.api.sync.ServerData.ServerEnum.DEMO;

/**
 * Created by alwi on 06/03/2021.
 * All rights reserved.
 */
public class BetlejemXtbUtils {
    private static final Logger LOG = LoggerFactory.getLogger(BetlejemXtbUtils.class);

    public static final ServerData.ServerEnum serverType = DEMO;

    //please provide the application details if you received them
    private static String APP_ID = null;
    private static String APP_NAME = null;

    public static final int TYPE_US_STC_REAL = 2724;
    public static final int TYPE_US_STC_DEMO = 2436;
    public static final int TYPE_GPW_STC_REAL = 2730;
    public static final int TYPE_GPW_STC_DEMO = 2429;

    public static final String CATEGORY = "STC";


    public static XtbCredentials fetchXtbCredentials() throws IOException {
        Base64PropertyPasswordDecoder dec = new Base64PropertyPasswordDecoder();
        String login = getXtbValue(BETLEJEM_XTB_LOGIN);
        String password = getXtbValue(BETLEJEM_XTB_PASSWORD);
        return new XtbCredentials(dec.decode(login), dec.decode(password), APP_ID, APP_NAME);
    }

    public static String commandTypeFor(TRADE_TRANSACTION_TYPE commandType) {
        if (commandType.getCode() == 0L) {
            return "OPEN";
        } else if (commandType.getCode() == 2L) {
            return "CLOSE";
        } else if (commandType.getCode() == 3L) {
            return "MODIFY";
        } else if (commandType.getCode() == 4L) {
            return "DELETE";
        } else throw new RuntimeException("Not implemented for: " + commandType);
    }

    public static String commandNameFor(TRADE_OPERATION_CODE command) {
        if (command.getCode() == 0L) {
            return "BUY";
        } else if (command.getCode() == 1L) {
            return "SELL";
        } else if (command.getCode() == 2L) {
            return "BUY_LIMIT";
        } else if (command.getCode() == 3L) {
            return "SELL_LIMIT";
        } else if (command.getCode() == 4L) {
            return "BUY_STOP";
        } else if (command.getCode() == 5L) {
            return "SELL_STOP";
        } else if (command.getCode() == 6L) {
            return "BALANCE";
        } else if (command.getCode() == 7L) {
            return "CREDIT";
        } else throw new RuntimeException("Not implemented for: " + command);
    }

    public static void waitBeforeNextCall() {
        try {
            Thread.sleep(500); // min interval allowed by XTB
        } catch (InterruptedException e) {
            logError(e);
        }
    }

    public static List<String> extractSymbolNames(List<SymbolRecord> tickers) {
        return tickers.stream()
                .map(SymbolRecord::getSymbol)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
    }

    public static boolean inGpwMarketOpenHours(LocalDateTime now) {
        return inMarketOpenHours(now, GPW_MARKET_OPEN_HOUR, GPW_MARKET_CLOSE_HOUR);
    }

    public static boolean inUsMarketOpenHours(LocalDateTime now) {
        return inMarketOpenHours(now, US_MARKET_OPEN_HOUR, US_MARKET_CLOSE_HOUR);
    }

    public static boolean inUsMarketCloseHours(LocalDateTime now) {
        return inMarketCloseHours(now, US_MARKET_OPEN_HOUR, US_MARKET_CLOSE_HOUR);
    }

    public static boolean inGpwMarketCloseHours(LocalDateTime now) {
        return inMarketCloseHours(now, GPW_MARKET_OPEN_HOUR, GPW_MARKET_CLOSE_HOUR);
    }

    private static boolean inMarketOpenHours(LocalDateTime now, int marketOpenHour, int marketCloseHour) {
        return now.getHourOfDay() >= marketOpenHour && now.getHourOfDay() < marketCloseHour && !isWeekend(now);
    }

    private static boolean inMarketCloseHours(LocalDateTime now, int marketOpenHour, int marketCloseHour) {
        return (now.getHourOfDay() >= 0 && now.getHourOfDay() < marketOpenHour) || now.getHourOfDay() >= marketCloseHour || isWeekend(now);
    }

    public static boolean withinTradingHoursFor(HoursRecord h) {
        LocalTime now = LocalTime.now();
        return now.isAfter(LocalTime.fromMillisOfDay(h.getFromT()).minusMinutes(40)) && now.isBefore(LocalTime.fromMillisOfDay(h.getToT()).plusMinutes(10)); // start (because pending orders might be put already) and finish earlier (so that transaction sending time will not result in error)
    }

    public static Map<String, Prediction> extractValuable(List<Prediction> predictionResults) {
        Map<String, Prediction> valuable = new LinkedHashMap<>();
        predictionResults.stream()
                .filter(p -> p.getAvg().compareTo(new BigDecimal(VALUABLE_AVG_PRED_VAL)) >= 0)
                .sorted(Comparator.comparing(Prediction::getAvg, Comparator.reverseOrder()))
                .forEach(p -> valuable.put(p.getName(), p));
        return valuable;
    }

    public static Double applyStepRule(Map<Integer, List<StepRecord>> stepRules, int stepRuleId, Double price) {
        Optional<Double> stepRule = getStepRule(stepRules, stepRuleId, price);
        return stepRule.map(s -> {
            BigDecimal step = new BigDecimal(s);
            Integer fullStepsWithin = new BigDecimal(price).divide(step, 5, DOWN).intValue();
            Double resultPrice = new BigDecimal(fullStepsWithin).multiply(step).setScale(4, DOWN).doubleValue();
            System.out.printf("Applying step (%.4f) to price %.5f => %.5f%n", s, price, resultPrice);
            return resultPrice;

        }).orElse(price);
    }

    public static Optional<Double> getStepRule(Map<Integer, List<StepRecord>> stepRules, int stepRuleId, Double price) {
        List<StepRecord> stepRecord = stepRules.get(stepRuleId);

        // example for GPW
        // 0.0       step= 0.0001
        // 1.0       step= 0.0002
        // 2.0       step= 0.0005
        // 5.0       step= 0.001
        // 10.0      step= 0.002
        // 20.0      step= 0.005
        // 50.0      step= 0.01
        // 100.0     step= 0.02
        // 200.0     step= 0.05
        // 500.0     step= 0.1
        // 1000.0    step= 0.2
        // 2000.0    step= 0.5
        // 5000.0    step= 1.0
        // 10000.0   step= 2.0
        // 20000.0   step= 5.0
        // 50000.0   step=10.0
        return stepRecord.stream()
                .filter(r -> price > r.getFromValue())
                .max(Comparator.comparing(StepRecord::getFromValue))
                .map(StepRecord::getStep);
    }

    @SuppressWarnings("Duplicates")
    public static double getVolumeFor(String currency, Double price) {
        int volume;
        if (currency.equals("USD")) { // min 135
            if (price < USD_MIN_TOTAL_VAL) {
                volume = ((int) (USD_MIN_TOTAL_VAL / price)) + 1;
            } else if (price >= USD_MIN_TOTAL_VAL && price < USD_MAX_TOTAL_VAL) {
                volume = 1;
            } else {
                volume = 0;
            }
        } else if (currency.equals("PLN")) { // min 500
            if (price < PLN_MIN_TOTAL_VAL) {
                volume = ((int) (PLN_MIN_TOTAL_VAL / price)) + 1;
            } else if (price >= PLN_MIN_TOTAL_VAL && price < PLN_MAX_TOTAL_VAL) {
                volume = 1;
            } else {
                volume = 0;
            }
        } else {
            throw new RuntimeException(String.format("Not implemented for currency: %s", currency));
        }

        LOG.info(String.format("Calculated volume: %d%n", volume));
        return volume;
    }

    public static void addBuyPrediction(List<BuyForPredictionOrderInfo> recentBuyOrders, Prediction predictionUsed, TradeTransactionResponse response) {
        if (response != null && response.getOrder() > 0) {
            System.out.println(String.format("Storing BUY order for id: %d and prediction used: %s", response.getOrder(), predictionUsed));
            recentBuyOrders.add(new BuyForPredictionOrderInfo(response.getOrder(), predictionUsed));
            if (recentBuyOrders.size() > 500) {
                recentBuyOrders.remove(0);
            }
        }
    }

    public static Optional<Prediction> extractRecentPredictionForBuy(long orderNumber, List<BuyForPredictionOrderInfo> recentBuyOrders) {
        System.out.printf("Searching for original BUY for order number: %d%n", orderNumber);
        return recentBuyOrders.stream()
                .filter(info -> info.getOrderNumber() == orderNumber)
                .peek(info -> System.out.println(String.format(" !!! Found stored past BUY order for id: %d - %s", info.getOrderNumber(), info.getPredictionUsed())))
                .map(BuyForPredictionOrderInfo::getPredictionUsed)
                .filter(p -> {
                    if (LocalDateTime.now().getDayOfWeek() == DateTimeConstants.MONDAY) {
                        return p.getTimestamp().isAfter(LocalDateTime.now().minusDays(DAYS_BACK_TO_FRIDAY));
                    } else {
                        return p.getTimestamp().isAfter(LocalDateTime.now().minusDays(1)); // skip too old predictions
                    }
                })
                .findFirst();
    }

    public static Map<String, Prediction> extractLatestPredsD1(Deque<Map<String, Prediction>> recentPredictionsD1, Map<String, Optional<HoursRecord>> tradingHours) {
        Map<String, Integer> cleanSymbolsOpenHours = mappingFromCleanSymbolName(tradingHours);

        LocalDateTime now = now();
        Predicate<Prediction> predicate;
        LocalDateTime latestPossibleDateForDailyPrediction = LocalDate.now().toLocalDateTime(LocalTime.MIDNIGHT).minusDays(1);
        if (now.getDayOfWeek() == DateTimeConstants.MONDAY) {
            predicate = predictionD1 -> !predictionD1.getTimestamp().toLocalDate().isBefore(latestPossibleDateForDailyPrediction.minusDays(DAYS_BACK_TO_FRIDAY).toLocalDate()); // allow these from Friday
        } else {
            int currentHour = now.getHourOfDay();
            predicate = predictionD1 -> {
                String symbolName = predictionD1.getName();
                Integer openHour = cleanSymbolsOpenHours.get(symbolName);
                if (isPreMarketHour(currentHour, openHour)) { // its pre-market...
                    return !predictionD1.getTimestamp().toLocalDate().minusDays(1).isBefore(latestPossibleDateForDailyPrediction); // allow yesterdays predictions
                } else {
                    return !predictionD1.getTimestamp().isBefore(latestPossibleDateForDailyPrediction); // allow these from yesterday only
                }
            };
        }
        return extractLatestPreds(recentPredictionsD1, predicate);
    }

    public static Map<String, Prediction> extractLatestPredsM5(Deque<Map<String, Prediction>> recentPredictionsM5, Map<String, Optional<HoursRecord>> tradingHours) {
        Map<String, Integer> cleanSymbolsOpenHours = mappingFromCleanSymbolName(tradingHours);

        LocalDateTime now = now();
        Predicate<Prediction> predicate;
        if (now.getDayOfWeek() == DateTimeConstants.MONDAY) {
            predicate = predictionM5 -> !predictionM5.getTimestamp().isBefore(now.minusDays(DAYS_BACK_TO_FRIDAY).minusMinutes(FRESH_M5_MAX_MINUTES_BACK)); // allow from Fridays most recent
        } else {
            int currentHour = now.getHourOfDay();
            predicate = predictionM5 -> {
                Integer openHour = cleanSymbolsOpenHours.get(predictionM5.getName());
                if (isPreMarketHour(currentHour, openHour)) { // its pre-market...
                    return !predictionM5.getTimestamp().minusDays(1).plusHours(4).isBefore(now); // allow most recent yesterdays predictions
                } else {
                    return !predictionM5.getTimestamp().isBefore(now.minusMinutes(FRESH_M5_MAX_MINUTES_BACK)); // allow most recent from today only
                }
            };
        }
        return extractLatestPreds(recentPredictionsM5, predicate);
    }

    private static boolean isPreMarketHour(int currentHour, Integer openHour) {
        return openHour > 0 && openHour - 1 < currentHour && currentHour < openHour;
    }

    private static Map<String, Integer> mappingFromCleanSymbolName(Map<String, Optional<HoursRecord>> tradingHours) {
        return tradingHours.entrySet().stream()
                .map(entry -> new HashMap.SimpleEntry<>(cleanSymbolName(entry.getKey()), entry.getValue().map(hoursRecord -> LocalTime.fromMillisOfDay(hoursRecord.getFromT()).getHourOfDay()).orElse(-1)))
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
    }

    private static Map<String, Prediction> extractLatestPreds(Deque<Map<String, Prediction>> recentPredictions, Predicate<Prediction> timeConstraints) {
        return Optional.ofNullable(recentPredictions.size() > 0 ? recentPredictions.peekLast() : null)
                .orElse(new HashMap<>())
                .values()
                .stream()
                .filter(timeConstraints)
                .collect(Collectors.toMap(Prediction::getName, Function.identity()));
    }

    public static String findSymbolForCleanSymbol(List<SymbolRecord> tickers, String cleanSymbol) {
        return tickers.stream()
                .filter(tickerRecord -> tickerRecord.getSymbol().startsWith(cleanSymbol))
                .findFirst()
                .map(SymbolRecord::getSymbol)
                .orElse(null);
    }

    public static List<String> extractSymbols(List<SymbolRecord> tickers) {
        return tickers.stream()
                .map(SymbolRecord::getSymbol)
                .collect(Collectors.toList());
    }

    public static Set<String> concatSymbols(Deque<Map<String, Prediction>> recentPredictionsM5, Deque<Map<String, Prediction>> recentPredictionsD1) {
        return Stream.concat(
                Optional.ofNullable(recentPredictionsM5.size() > 0 ? recentPredictionsM5.peekLast() : null).orElse(new HashMap<>()).keySet().stream(),
                Optional.ofNullable(recentPredictionsD1.size() > 0 ? recentPredictionsD1.peekLast() : null).orElse(new HashMap<>()).keySet().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
