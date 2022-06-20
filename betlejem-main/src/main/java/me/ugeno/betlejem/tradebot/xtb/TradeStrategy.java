package me.ugeno.betlejem.tradebot.xtb;

import com.tictactec.ta.lib.Core;
import com.tictactec.ta.lib.MAType;
import com.tictactec.ta.lib.MInteger;
import com.tictactec.ta.lib.RetCode;
import me.ugeno.betlejem.common.data.BuyForPredictionOrderInfo;
import me.ugeno.betlejem.common.data.Prediction;
import me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants;
import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.xstore.api.message.codes.PERIOD_CODE;
import pro.xstore.api.message.codes.TRADE_OPERATION_CODE;
import pro.xstore.api.message.codes.TRADE_TRANSACTION_TYPE;
import pro.xstore.api.message.command.APICommandFactory;
import pro.xstore.api.message.error.APICommandConstructionException;
import pro.xstore.api.message.error.APICommunicationException;
import pro.xstore.api.message.error.APIReplyParseException;
import pro.xstore.api.message.records.HoursRecord;
import pro.xstore.api.message.records.RateInfoRecord;
import pro.xstore.api.message.records.StepRecord;
import pro.xstore.api.message.records.SymbolRecord;
import pro.xstore.api.message.records.TradeRecord;
import pro.xstore.api.message.response.APIErrorResponse;
import pro.xstore.api.message.response.ChartResponse;
import pro.xstore.api.message.response.MarginLevelResponse;
import pro.xstore.api.message.response.SymbolResponse;
import pro.xstore.api.message.response.TradeTransactionResponse;
import pro.xstore.api.message.response.TradingHoursResponse;
import pro.xstore.api.sync.SyncAPIConnector;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_EVEN;
import static me.ugeno.betlejem.common.utils.BetlejemUtils.cleanSymbolName;
import static me.ugeno.betlejem.common.utils.BetlejemUtils.isWeekend;
import static me.ugeno.betlejem.common.utils.BetlejemUtils.logError;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.MIN_PROFIT_FOR_SELL_STOP;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.OPPORTUNISTIC_INCREASE_TO_RATIO;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.PRICE_SCALE;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils.addBuyPrediction;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils.applyStepRule;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils.commandNameFor;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils.commandTypeFor;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils.concatSymbols;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils.extractLatestPredsD1;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils.extractLatestPredsM5;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils.extractRecentPredictionForBuy;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils.extractSymbols;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils.findSymbolForCleanSymbol;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils.getStepRule;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils.getVolumeFor;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils.waitBeforeNextCall;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils.withinTradingHoursFor;
import static org.joda.time.LocalDateTime.fromDateFields;
import static org.joda.time.LocalDateTime.now;
import static pro.xstore.api.message.codes.TRADE_OPERATION_CODE.BUY_LIMIT;
import static pro.xstore.api.message.codes.TRADE_OPERATION_CODE.SELL_LIMIT;
import static pro.xstore.api.message.codes.TRADE_OPERATION_CODE.SELL_STOP;
import static pro.xstore.api.message.codes.TRADE_TRANSACTION_TYPE.DELETE;
import static pro.xstore.api.message.codes.TRADE_TRANSACTION_TYPE.MODIFY;
import static pro.xstore.api.message.codes.TRADE_TRANSACTION_TYPE.OPEN;
import static pro.xstore.api.message.command.APICommandFactory.executeMarginLevelCommand;
import static pro.xstore.api.message.command.APICommandFactory.executeSymbolCommand;
import static pro.xstore.api.message.command.APICommandFactory.executeTradeTransactionCommand;
import static pro.xstore.api.message.command.APICommandFactory.executeTradesCommand;
import static pro.xstore.api.message.command.APICommandFactory.executeTradingHoursCommand;

/**
 * Created by alwi on 06/03/2021.
 * All rights reserved.
 */
public class TradeStrategy {
    private static final Logger LOG = LoggerFactory.getLogger(TradeStrategy.class);

    public static void applyTradingStrategy(SyncAPIConnector connector, List<SymbolRecord> tickers, final Map<Integer, List<StepRecord>> stepRules, double currencyPriceInPln, double comission, List<BuyForPredictionOrderInfo> recentBuyOrders, Deque<Map<String, Prediction>> recentPredictionsM5, Deque<Map<String, Prediction>> recentPredictionsD1) {
        try {
            List<String> currentMarketSymbols = extractSymbols(tickers);
            AccountTrades existingAccountTrades = new AccountTrades(executeTradesCommand(connector, false));

            Map<String, Optional<HoursRecord>> tradingHours = fetchTradingHours(connector, existingAccountTrades.getTradeRecordsSymbols());
            existingAccountTrades.print();

            Set<String> symbolsWithAnyPredictions = concatSymbols(recentPredictionsM5, recentPredictionsD1);
            List<String> tickerSymbolsForPredictions = tickers.stream()
                    .map(SymbolRecord::getSymbol)
                    .filter(tickerSymbol -> symbolsWithAnyPredictions.contains(cleanSymbolName(tickerSymbol)))
                    .collect(Collectors.toList());

            waitBeforeNextCall();
            Map<String, Optional<HoursRecord>> tradingHoursForPredictions = fetchTradingHours(connector, tickerSymbolsForPredictions);
            tradingHours.putAll(tradingHoursForPredictions);

            Map<String, Prediction> tickersWithPredictionsForM5 = extractLatestPredsM5(recentPredictionsM5, tradingHours);
            Map<String, Prediction> tickersWithPredictionsForD1 = extractLatestPredsD1(recentPredictionsD1, tradingHours);

            strategyPart_updateBuys(connector, stepRules, currencyPriceInPln, comission, recentBuyOrders, currentMarketSymbols, existingAccountTrades, tradingHours, tickersWithPredictionsForM5);

            waitBeforeNextCall();
            AccountTrades accountTradesUpdated = new AccountTrades(executeTradesCommand(connector, false));

            strategyPart_createNewBuys(connector, tickers, stepRules, currencyPriceInPln, comission, recentBuyOrders, tradingHours, symbolsWithAnyPredictions, tickersWithPredictionsForM5, tickersWithPredictionsForD1, accountTradesUpdated);

            waitBeforeNextCall(); // give a chance to new positions to be bought
            AccountTrades accountTradesBeforeSale = new AccountTrades(executeTradesCommand(connector, false));

            strategyPart_handleSells(connector, stepRules, recentBuyOrders, currentMarketSymbols, existingAccountTrades, tradingHours, tickersWithPredictionsForM5, tickersWithPredictionsForD1, accountTradesBeforeSale, currencyPriceInPln);

            LOG.info("----------TRADE STRATEGY DONE----------");
        } catch (Exception e) {
            logError(e);
        }
    }

    private static void strategyPart_updateBuys(SyncAPIConnector connector, Map<Integer, List<StepRecord>> stepRules, double currencyPriceInPln, double comission, List<BuyForPredictionOrderInfo> recentBuyOrders, List<String> currentMarketSymbols, AccountTrades existingAccountTrades, Map<String, Optional<HoursRecord>> tradingHours, Map<String, Prediction> tickersWithPredictionsForM5) {
        LOG.info("\n\n------- UPDATE PENDING BUY ORDERS -----------");
        for (TradeRecord pendingOrder : existingAccountTrades.getPendingOrders()) {
            String symbol = pendingOrder.getSymbol();
            if (currentMarketSymbols.contains(symbol)) {
                Optional<HoursRecord> symbolTradeHours = tradingHours.get(symbol);
                if (symbolTradeHours.isPresent()) {
                    HoursRecord tradeHours = symbolTradeHours.get();
                    if (withinTradingHoursFor(tradeHours)) {
                        SymbolResponse symbolRecordResponse;
                        try {
                            symbolRecordResponse = executeSymbolCommand(connector, symbol);
                            SymbolRecord symbolRecord = symbolRecordResponse.getSymbol();
                            int stepRuleId = symbolRecord.getStepRuleId();

                            // we can send transactions
                            if (existingAccountTrades.getOpenPositionsSymbols().contains(symbol)) {
                                LOG.debug(String.format("Position already opened for symbol: %s. Skipping BUY.", symbol));
                            } else {
                                if (tickersWithPredictionsForM5.keySet().contains(cleanSymbolName(symbol))) {
                                    // new prediction M5 occurs...
                                    LOG.debug(String.format("Prediction M5 found for pending transaction on new position for symbol: %s", symbol));
                                    Prediction recentPredM5 = tickersWithPredictionsForM5.get(cleanSymbolName(symbol));

                                    // update pending transaction for new positions with most recent prediction price - when BUY LIMIT price could be decreased
                                    try {
                                        LOG.debug(String.format("Transaction update for BUY new position on predicted increase change for symbol: %s", symbol));
                                        Long expiration = now().plusMinutes(15).toDateTime().getMillis();

                                        double volume = pendingOrder.getVolume();

                                        double oldBuyPrice = pendingOrder.getOpen_price();
                                        double oldTotalVal = volume * oldBuyPrice;
                                        oldTotalVal *= currencyPriceInPln;

                                        double newBuyPrice = recentPredM5.getBuy().doubleValue();
                                        double newTotalVal = volume * applyStepRule(stepRules, stepRuleId, newBuyPrice);
                                        newTotalVal *= currencyPriceInPln;
                                        newTotalVal *= (1 + comission);

                                        double valDelta = newTotalVal - oldTotalVal;

                                        if (valDelta < 0) {
                                            LOG.debug(String.format("BUY update to new price: %.4f=>%.4f", oldBuyPrice, newBuyPrice));
                                            LOG.debug(String.format("BUY update tx total val change by: %.4f", valDelta));

                                            MarginLevelResponse userMarginLevel = executeMarginLevelCommand(connector);
                                            BigDecimal freeMargin = new BigDecimal(userMarginLevel.getMargin_free()).setScale(PRICE_SCALE, HALF_EVEN);
                                            LOG.debug(String.format("User free margin: %s", freeMargin));

                                            // NOTE: No need to check current price and decrease BUY LIMIT - as if happened it would have been bought already
                                            if (volume > 0 && valDelta < freeMargin.doubleValue()) {
                                                TradeTransactionResponse response = txUpdateBuyOfNewPositions(connector, pendingOrder, symbol, stepRuleId, expiration, newBuyPrice, stepRules, tradeHours);
                                                addBuyPrediction(recentBuyOrders, recentPredM5, response);
                                            } else {
                                                LOG.debug(String.format("Not enough free margin to modify BUY of: %s - with price %.4f", symbol, newBuyPrice));
                                            }
                                        } else {
                                            LOG.info("Allowing only decrease when buying. Currently price increased for consecutive M5 pred. - skipping...");
                                        }
                                    } catch (Exception e) {
                                        logError(e);
                                    }
                                } else {
                                    LOG.debug(String.format("Prediction M5 not found for pending transaction on non existing position position on symbol: %s", symbol));
                                    try {
                                        if (fromDateFields(new Date(pendingOrder.getOpen_time())).isBefore(now().minusHours(1))) {
                                            // useful if somebody creates buy tx manually without expiration time
                                            LOG.info("... dangling for too long already - might miss the peek already - deleting...");
                                            LOG.debug(String.format("Transaction deletion for not fulfilled prediction of symbol: %s", symbol));
                                            txDeletePendingBuyNewPosition(connector, pendingOrder, symbol, symbolRecord, tradeHours);
                                        }
                                    } catch (Exception e) {
                                        logError(e);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            logError(e);
                        }
                    } else {
                        LOG.debug(String.format("Outside of market hours for symbol: %s", symbol));
                    }
                } else {
                    LOG.warn("Missing trading hours for symbol: {}", symbol);
                }
            } else {
                LOG.debug(String.format("Pending order symbol is outside of considered market: %s", symbol));
            }
            LOG.info("---");
        }
    }

    @SuppressWarnings("Duplicates")
    private static void strategyPart_createNewBuys(SyncAPIConnector connector, List<SymbolRecord> tickers, Map<Integer, List<StepRecord>> stepRules, double currencyPriceInPln, double commission, List<BuyForPredictionOrderInfo> recentBuyOrders, Map<String, Optional<HoursRecord>> tradingHours, Set<String> symbolsWithAnyPredictions, Map<String, Prediction> tickersWithPredictionsForM5, Map<String, Prediction> tickersWithPredictionsForD1, AccountTrades accountTradesUpdated) throws APICommandConstructionException, APIReplyParseException, APIErrorResponse, APICommunicationException {
        LOG.info("\n\n-------- CREATE BUY ORDERS FOR NEW POSITIONS -----------");
        processingSymbols:
        for (String cleanSymbol : symbolsWithAnyPredictions) {
            String symbol = findSymbolForCleanSymbol(tickers, cleanSymbol);

            if (symbol == null) {
                throw new RuntimeException(String.format("Couldn't find ticker symbol for clean symbol = %s", cleanSymbol));
            }

            boolean noOpenPositions = accountTradesUpdated.getOpenPositionsSymbols().stream()
                    .noneMatch(s -> s.equals(symbol));
            boolean noPendingOrders = accountTradesUpdated.getPendingOrdersSymbols().stream()
                    .noneMatch(s -> s.equals(symbol));

            waitBeforeNextCall(); // to avoid request too frequent error

            // handle initial BUY operations:
            Optional<HoursRecord> hoursRecord = tradingHours.get(symbol);
            if (hoursRecord != null && hoursRecord.isPresent()) {
                HoursRecord tradeHours = hoursRecord.get();
                if (withinTradingHoursFor(tradeHours)) {
                    if (noOpenPositions && noPendingOrders) {
                        LOG.debug(String.format("We have a prediction for some new symbol: %s", symbol));
                        SymbolResponse symbolRecordResponse = executeSymbolCommand(connector, symbol);
                        SymbolRecord symbolRecord = symbolRecordResponse.getSymbol();

                        Prediction recentPredM5 = tickersWithPredictionsForM5.get(cleanSymbol);
                        Prediction recentPredD1 = tickersWithPredictionsForD1.get(cleanSymbol);

                        Prediction predictionUsed;
                        if (recentPredM5 != null) {
                            predictionUsed = recentPredM5;
                            LOG.debug(String.format("New BUY from M5 prediction: %.4f", predictionUsed.getBuy()));
                        } else if (recentPredD1 != null) {
                            boolean isMaxOneHourSinceOpen = DateTime.now().isBefore(LocalTime.fromMillisOfDay(tradeHours.getFromT()).plusHours(1).toDateTimeToday());
                            if (isMaxOneHourSinceOpen) {
                                predictionUsed = recentPredD1;
                                LOG.debug(String.format("New BUY from D1 prediction: %.4f", predictionUsed.getBuy()));
                            } else {
                                LOG.debug(String.format("It's too late to use D1 prediction for BUY already for: %s", symbol));
                                continue;
                            }
                        } else {
                            // set BUY order only if there is prediction for some symbol - this should not be the case as we iterate through merged lists of symbols predictions
                            LOG.warn("There is no prediction for symbol (while traversing symbols with symbolsWithAnyPredictions collection): %s", symbol);
                            continue;
                        }

                        //check current balance
                        MarginLevelResponse userMarginLevel = executeMarginLevelCommand(connector);
                        BigDecimal freeMargin = new BigDecimal(userMarginLevel.getMargin_free()).setScale(PRICE_SCALE, HALF_EVEN);
                        LOG.debug(String.format("User free margin: %s", freeMargin));

                        Long expiration = now().plusMinutes(20).toDateTime().getMillis();
                        double buyPrice = predictionUsed.getBuy().doubleValue();

                        int stepRuleId = symbolRecord.getStepRuleId();
                        buyPrice = applyStepRule(stepRules, stepRuleId, buyPrice);
                        double volume = getVolumeFor(symbolRecord.getCurrency(), buyPrice);
                        double totalVal = volume * buyPrice;
                        totalVal *= currencyPriceInPln;
                        totalVal *= (1 + commission);
                        LOG.info(String.format("Tx total value=%.4f", totalVal));

                        if (volume > 0 && totalVal < freeMargin.doubleValue()) {
                            try {
                                // if there are money for it create pending orders for BUY_LIMIT of a new symbols with lowest price from recent predictions
                                LOG.debug(String.format("Transaction creation for open BUY new symbol: %s for price: %.4f", symbol, buyPrice));

                                ChartResponse chartResponse = APICommandFactory.executeChartLastCommand(connector, symbol, PERIOD_CODE.PERIOD_M5, now().minusDays(1).toDateTime().getMillis());
                                Optional<RateInfoRecord> latestRateInfo = chartResponse.getRateInfos().stream().max(Comparator.comparing(RateInfoRecord::getCtm));

                                if (latestRateInfo.isPresent()) {
                                    BigDecimal currentOpenBd = new BigDecimal(latestRateInfo.get().getOpen()).divide(new BigDecimal(10).pow(chartResponse.getDigits()), 4, HALF_EVEN);
                                    LOG.debug(String.format("Current open price for: %s = %.4f", symbol, currentOpenBd));

                                    BigDecimal buyPriceBd = new BigDecimal(buyPrice).setScale(4, HALF_EVEN);
                                    while (buyPriceBd.compareTo(currentOpenBd) > 0) {
                                        Optional<Double> step = getStepRule(stepRules, stepRuleId, buyPrice);
                                        if (step.isPresent()) {
                                            BigDecimal stepBd = new BigDecimal(step.get()).setScale(4, HALF_EVEN);
                                            buyPriceBd = buyPriceBd.subtract(stepBd);
                                            buyPrice = buyPriceBd.doubleValue();
                                            LOG.debug(String.format("Decreasing BUY LIMIT to: %s", buyPrice));
                                        } else {
                                            LOG.error("Cannot get current price step for: %s", symbol);
                                            continue processingSymbols; // TODO: change to break
                                        }
                                    }
                                    volume = getVolumeFor(symbolRecord.getCurrency(), buyPrice);
                                } else {
                                    LOG.warn("WARN: Could not get current RATE info for ticker: %s", symbol);
                                }

                                TradeTransactionResponse response = txBuyNewPosition(connector, symbol, expiration, volume, buyPrice, tradeHours);
                                addBuyPrediction(recentBuyOrders, predictionUsed, response);

                                waitBeforeNextCall();
                            } catch (Exception e) {
                                logError(e);
                            }
                        } else {
                            LOG.debug(String.format("Not enough free margin to buy: %s", symbol));
                            break;
                        }
                    } else {
                        LOG.debug(String.format("Some transactions exist for symbol: %s", symbol));
                    }
                } else {
                    LOG.debug(String.format("Outside of market hours for symbol: %s", symbol));
                }
            } else {
                LOG.warn("Missing trading hours for symbol: {}", symbol);
            }
            LOG.info("---");
        }
    }

    @SuppressWarnings("Duplicates")
    private static void strategyPart_handleSells(SyncAPIConnector connector, Map<Integer, List<StepRecord>> stepRules, List<BuyForPredictionOrderInfo> recentBuyOrders, List<String> currentMarketSymbols, AccountTrades existingAccountTrades, Map<String, Optional<HoursRecord>> tradingHours, Map<String, Prediction> tickersWithPredictionsForM5, Map<String, Prediction> tickersWithPredictionsForD1, AccountTrades accountTradesBeforeSale, double currencyPriceInPln) {
        LOG.info("\n\n-------- HANDLE SELL ORDERS (create & update) FOR EXISTING POSITIONS -----------");
        for (TradeRecord openPosition : accountTradesBeforeSale.getOpenPositions()) {
            String symbol = openPosition.getSymbol();

            int digitsDivider = new BigDecimal(10).pow(openPosition.getDigits()).intValue();
            LOG.debug(String.format("Digits divider: %s", digitsDivider));

            double openPositionOpenPrice = openPosition.getOpen_price();
            LOG.debug(String.format("Position open price: %.4f", openPositionOpenPrice));

            if (currentMarketSymbols.contains(symbol)) {
                if (tradingHours.get(symbol).isPresent()) {
                    HoursRecord tradeHours = tradingHours.get(symbol).get();
                    if (withinTradingHoursFor(tradeHours)) {
                        // handle SELL orders:
                        try {
                            SymbolResponse symbolRecordResponse = executeSymbolCommand(connector, symbol);
                            SymbolRecord symbolRecord = symbolRecordResponse.getSymbol();
                            int stepRuleId = symbolRecord.getStepRuleId();

                            // can send transactions
                            try {
                                Optional<TradeRecord> pendingOrderForOpenPosition = existingAccountTrades.findPendingOrder(symbol);
                                Optional<Prediction> originalBuyPrediction = extractRecentPredictionForBuy(openPosition.getOrder2(), recentBuyOrders);

                                // update SELLs
                                if (pendingOrderForOpenPosition.isPresent()) {
                                    LOG.debug(String.format("Pending SELL order found for: %s", symbol));
                                    TradeRecord pendingOrder = pendingOrderForOpenPosition.get();

                                    // try prediction from time of order - if not possible then get this new prediction sell price or some opportunistic increase
                                    double sellPrice = pendingOrder.getClose_price();
                                    LOG.debug(String.format("Current sell price for symbol: %s=%.4f", symbol, sellPrice));

                                    if (calculateCurrentProfit(currencyPriceInPln, openPosition, openPositionOpenPrice).compareTo(new BigDecimal(MIN_PROFIT_FOR_SELL_STOP)) > 0) {
                                        LOG.debug("Yet here is some profit already... Try to keep it...");

                                        // attempt to take profit
                                        ChartResponse chartResponse = APICommandFactory.executeChartLastCommand(connector, symbol, PERIOD_CODE.PERIOD_M5, now().minusDays(1).toDateTime().getMillis());
                                        Optional<RateInfoRecord> rateInfoRecord = chartResponse.getRateInfos().stream().max(Comparator.comparing(RateInfoRecord::getCtm));

                                        if (rateInfoRecord.isPresent()) {
                                            boolean sellLimitDeleteSent = false;
                                            if ((long) pendingOrder.getCmd() == SELL_LIMIT.getCode()) {
                                                LOG.info("SELL LIMIT pending - removing...");
                                                txDeletePendingSellOfOpenPosition(connector, pendingOrder, symbol, tradeHours, SELL_LIMIT);
                                                sellLimitDeleteSent = true;
                                            }

                                            long expiration = now().plusDays(1).toDateTime().getMillis();
                                            sellPrice = adjustSellPriceToSellStop(stepRules, symbol, digitsDivider, openPositionOpenPrice, stepRuleId, rateInfoRecord.get());

                                            if (sellLimitDeleteSent) {
                                                LOG.debug(String.format("Transaction new SELL STOP for open position for symbol: %s", symbol));
                                                txNewSellOpenPosition(connector, openPosition, symbol, expiration, stepRuleId, sellPrice, stepRules, tradeHours, SELL_STOP);
                                            } else {
                                                LOG.debug(String.format("Transaction update for SELL STOP on open position for symbol: %s", symbol));
                                                txUpdateSellOfOpenPositions(connector, pendingOrder, symbol, stepRuleId, expiration, sellPrice, stepRules, tradeHours, SELL_STOP);
                                            }
                                        } else {
                                            LOG.debug(String.format("Can't retrieve latest rate for symbol: %s", symbol));
                                        }
                                    } else {
                                        LOG.debug(String.format("No new prediction D1 nor M5 for open position on symbol: %s && no profit so far - leave it as is... ", symbol));
                                    }
                                } else { // create NEW SELLs (no pending orders yet or already expired for position that is open)
                                    LOG.debug(String.format("Transaction creation for SELL open position without predictions for symbol: %s", symbol));

                                    BigDecimal initSellPrice = new BigDecimal(symbolRecord.getBid());
                                    LOG.debug(String.format("> Init sell price: %.4f", initSellPrice.doubleValue()));

                                    ChartResponse chartResponse = APICommandFactory.executeChartLastCommand(connector, symbol, PERIOD_CODE.PERIOD_M5, now().minusDays(1).toDateTime().getMillis());
                                    List<RateInfoRecord> rateInfoRecords = chartResponse.getRateInfos().stream().sorted(Comparator.comparing(RateInfoRecord::getCtm)).collect(Collectors.toList());
                                    int movingPeriod = 16;
                                    if (rateInfoRecords.size() > movingPeriod) {
                                        initSellPrice = calculateKama(rateInfoRecords, digitsDivider, movingPeriod).orElse(initSellPrice);
                                        LOG.debug(String.format("Kama for price[%d]: %.4f", rateInfoRecords.size(), initSellPrice.doubleValue()));
                                    }

                                    double sellPrice = initSellPrice.doubleValue() * OPPORTUNISTIC_INCREASE_TO_RATIO;
                                    long expiration = now().plusHours(BetlejemXtbConstants.OPPORTUNISTIC_INCREASE_HOURS).toDateTime().getMillis();

                                    if (calculateCurrentProfit(currencyPriceInPln, openPosition, openPositionOpenPrice).compareTo(new BigDecimal(MIN_PROFIT_FOR_SELL_STOP)) > 0) {
                                        LOG.debug(String.format("There is some profit already symbol - attempt to set SELL STOP: %s", symbol));

                                        Optional<RateInfoRecord> rateInfoRecord = rateInfoRecords.stream().max(Comparator.comparing(RateInfoRecord::getCtm));
                                        if (rateInfoRecord.isPresent()) {
                                            expiration = now().plusDays(1).toDateTime().getMillis();
                                            sellPrice = adjustSellPriceToSellStop(stepRules, symbol, digitsDivider, openPositionOpenPrice, stepRuleId, rateInfoRecord.get());

                                            txNewSellOpenPosition(connector, openPosition, symbol, expiration, stepRuleId, sellPrice, stepRules, tradeHours, SELL_STOP);
                                        } else {
                                            LOG.debug(String.format("Can't retrieve latest rate for symbol: %s. Omitting...", symbol));
                                        }
                                    } else {
                                        LOG.debug(String.format("There is no profit yet for symbol: %s", symbol));

                                        if (originalBuyPrediction.isPresent()) {
                                            LOG.debug(String.format("Found original prediction for past BUY of symbol: %s", symbol));
                                            // if following predictions are decreasing then adjust to it (as this might suggest bad prediction), otherwise we might stuck with this position for longer...
                                            expiration = now().plusHours(4).toDateTime().getMillis();
                                            sellPrice = originalBuyPrediction.get().getSell().doubleValue();
                                        } else {
                                            LOG.debug(String.format("Original prediction not found for past BUY of symbol: %s - applying opportunistic increase... ", symbol));
                                        }

                                        txNewSellOpenPosition(connector, openPosition, symbol, expiration, stepRuleId, sellPrice, stepRules, tradeHours, SELL_LIMIT);
                                    }
                                }
                            } catch (Exception e) {
                                logError(e);
                            }
                        } catch (Exception e) {
                            logError(e);
                        }
                    } else {
                        LOG.debug(String.format("Outside of market hours for symbol: %s", symbol));
                    }
                } else {
                    LOG.warn("Missing trading hours for symbol: {}", symbol);
                    break; // we can assume that it applies for whole market and we process here symbols in context of one market
                }
            } else {
                LOG.debug(String.format("Open position symbol is outside of considered market: %s", symbol));
            }
            LOG.info("---");
        }
    }

    @SuppressWarnings("Duplicates")
    private static Optional<BigDecimal> calculateKama(List<RateInfoRecord> rateInfoRecords, int digitsDivider, int movingPeriod) {
        double[] data = rateInfoRecords.stream()
                .map(RateInfoRecord::getOpen)
                .mapToDouble(d -> d / digitsDivider)
                .toArray();

        double[] output = new double[data.length];
        MInteger begin = new MInteger();
        MInteger length = new MInteger();
        RetCode retCode = new Core().movingAverage(0, data.length - 1, data, movingPeriod, MAType.Kama, begin, length, output);

        BigDecimal lastValue = null;
        if (retCode == RetCode.Success) {
            for (int i = 0; i < data.length; i++) {
                if (i >= begin.value) {
                    lastValue = new BigDecimal(output[i - begin.value]).setScale(4, HALF_EVEN);
                }
            }
        } else {
            throw new RuntimeException(String.format("Could not enhance training data: %s", retCode.toString()));
        }

        return Optional.ofNullable(lastValue);
    }

    private static double adjustSellPriceToSellStop(Map<Integer, List<StepRecord>> stepRules, String symbol, int digitsDivider, double openPositionOpenPrice, int stepRuleId, RateInfoRecord rateInfoRecord1) {
        BigDecimal currentOpenBd = new BigDecimal(rateInfoRecord1.getOpen() / digitsDivider).setScale(4, HALF_EVEN);
        LOG.debug(String.format("Recent open price for: %s = %.4f", symbol, currentOpenBd));

        double sellPrice = applyStepRule(stepRules, stepRuleId, openPositionOpenPrice * (1. + MIN_PROFIT_FOR_SELL_STOP));
        LOG.debug(String.format("Initial sell price for: %s = %.4f (min profit level)", symbol, sellPrice));

        BigDecimal sellPriceBd = new BigDecimal(sellPrice).setScale(4, HALF_EVEN);
        Optional<Double> step = getStepRule(stepRules, stepRuleId, currentOpenBd.doubleValue());
        int xSteps = 3;
        BigDecimal currentOpenDecreasedBd = currentOpenBd.subtract(step.map(BigDecimal::new).orElse(ZERO).multiply(new BigDecimal(xSteps)));

        while (sellPriceBd.compareTo(currentOpenDecreasedBd) < 0) { // while more than X steps below current... => increase until exactly X steps below...
            step = getStepRule(stepRules, stepRuleId, sellPrice);
            if (step.isPresent()) {
                BigDecimal stepBd = new BigDecimal(step.get()).setScale(4, HALF_EVEN);
                sellPriceBd = sellPriceBd.add(stepBd);
                sellPrice = sellPriceBd.doubleValue();
                LOG.debug(String.format("Increasing SELL LIMIT to: %.4f", sellPrice));
            } else {
                LOG.debug(String.format("Could not get step for symbol: %s", symbol));
                break;
            }
        }

        return sellPrice;
    }

    private static BigDecimal calculateCurrentProfit(double currencyPriceInPln, TradeRecord openPosition, double openPositionOpenPrice) {
        double currentProfitNominal = openPosition.getProfit() / currencyPriceInPln;
        LOG.debug(String.format("Current profit nominal: %.2f", currentProfitNominal));
        BigDecimal originalTxValue = new BigDecimal(openPositionOpenPrice).multiply(new BigDecimal(openPosition.getVolume())).setScale(2, HALF_EVEN);
        LOG.debug(String.format("Open tx value: %s", originalTxValue));
        BigDecimal profitPercentage = new BigDecimal(currentProfitNominal).divide(originalTxValue, 5, HALF_EVEN).setScale(3, HALF_EVEN);
        LOG.debug(String.format("Current profit: %s ", profitPercentage));
        return profitPercentage;
    }

    private static TradeTransactionResponse txBuyNewPosition(SyncAPIConnector connector, String symbol, Long expiration, double volume, Double buyPriceByTheRules, HoursRecord tradeHours) throws APICommandConstructionException, APIReplyParseException, APIErrorResponse, APICommunicationException {
        return executeTradeCommand(connector, symbol, expiration, tradeHours, volume, BUY_LIMIT, OPEN, "txBuyNewPosition", 0L, buyPriceByTheRules);
    }

    private static void txDeletePendingBuyNewPosition(SyncAPIConnector connector, TradeRecord pendingOrder, String symbol, SymbolRecord symbolRecord, HoursRecord tradeHours) throws APICommandConstructionException, APIReplyParseException, APIErrorResponse, APICommunicationException {
        executeTradeCommand(connector, symbol, 0L, tradeHours, pendingOrder.getVolume(), BUY_LIMIT, DELETE, "txDeletePendingBuyNewPosition", pendingOrder.getOrder(), symbolRecord.getAsk());
    }

    private static TradeTransactionResponse txUpdateBuyOfNewPositions(SyncAPIConnector connector, TradeRecord existingOrder, String symbol, int stepRuleId, Long expiration, double price, Map<Integer, List<StepRecord>> stepRules, HoursRecord tradeHours) throws APICommandConstructionException, APIReplyParseException, APIErrorResponse, APICommunicationException {
        Double adjustedPrice = applyStepRule(stepRules, stepRuleId, price);
        if (adjustedPrice.equals(existingOrder.getOpen_price())) {
            LOG.info("Updated price equals previous. Skipping update...");
            return null;
        }

        return executeTradeCommand(connector, symbol, expiration, tradeHours, existingOrder.getVolume(), BUY_LIMIT, MODIFY, "txUpdateBuyOfNewPositions", existingOrder.getOrder(), adjustedPrice);
    }

    private static void txNewSellOpenPosition(SyncAPIConnector connector, TradeRecord openPosition, String symbol, Long expiration, int stepRuleId, Double price, Map<Integer, List<StepRecord>> stepRules, HoursRecord tradeHours, TRADE_OPERATION_CODE operationCode) throws APICommandConstructionException, APIReplyParseException, APIErrorResponse, APICommunicationException {
        executeTradeCommand(connector, symbol, expiration, tradeHours, openPosition.getVolume(), operationCode, OPEN, "txNewSellOpenPosition", 0L, applyStepRule(stepRules, stepRuleId, price));
    }

    private static void txDeletePendingSellOfOpenPosition(SyncAPIConnector connector, TradeRecord pendingOrder, String symbol, HoursRecord tradeHours, TRADE_OPERATION_CODE operationCode) throws APICommandConstructionException, APIReplyParseException, APIErrorResponse, APICommunicationException {
        executeTradeCommand(connector, symbol, 0L, tradeHours, pendingOrder.getVolume(), operationCode, DELETE, "txDeletePendingSellOfOpenPosition", pendingOrder.getOrder(), pendingOrder.getOpen_price());
    }

    private static void txUpdateSellOfOpenPositions(SyncAPIConnector connector, TradeRecord existingOrder, String symbol, int stepRuleId, Long expiration, double price, Map<Integer, List<StepRecord>> stepRules, HoursRecord tradeHours, TRADE_OPERATION_CODE operationCode) throws APICommandConstructionException, APIReplyParseException, APIErrorResponse, APICommunicationException {
        Double adjustedPrice = applyStepRule(stepRules, stepRuleId, price);
        if (adjustedPrice.equals(existingOrder.getOpen_price())) {
            LOG.info("Updated price equals previous. Skipping update...");
            return;
        }

        executeTradeCommand(connector, symbol, expiration, tradeHours, existingOrder.getVolume(), operationCode, MODIFY, "txUpdateSellOfOpenPositions", existingOrder.getOrder(), adjustedPrice);
    }

    private static TradeTransactionResponse executeTradeCommand(SyncAPIConnector connector, String symbol, Long expiration, HoursRecord tradeHours, double volume, TRADE_OPERATION_CODE command, TRADE_TRANSACTION_TYPE commandType, String name, long relatedOrderId, Double price) throws APICommandConstructionException, APIReplyParseException, APIErrorResponse, APICommunicationException {
        LOG.debug(String.format("Executing transaction %s for symbol: %s expiration: %s volume: %.0f command: %s type: %s, price: %.04f, relatedOrder: %d", name, symbol, new LocalDateTime(expiration), volume, commandNameFor(command), commandTypeFor(commandType), price, relatedOrderId));
        TradeTransactionResponse response = executeTradeTransactionCommand(connector, command, commandType, price, 0., 0., symbol, volume, relatedOrderId, name, adjustExpirationDate(expiration, tradeHours));
        LOG.debug(String.format("Transaction response: %s", response));
        waitBeforeNextCall(); // to avoid too frequent command error
        return response;
    }

    private static Long adjustExpirationDate(Long expiration, HoursRecord tradeHours) {
        if (tradeHours == null || expiration == 0) {
            return expiration;
        } else {
            LocalDateTime expirationLocalDateTime = fromDateFields(Date.from(Instant.ofEpochMilli(expiration)));
            LocalTime openFromLocalTime = LocalTime.fromMillisOfDay(tradeHours.getFromT());
            LocalTime openToLocalTime = LocalTime.fromMillisOfDay(tradeHours.getToT());

            DateTime expirationDateTime = expirationLocalDateTime.toDateTime();
            if (expirationDateTime.isAfter(openToLocalTime.toDateTimeToday()) && expirationDateTime.isBefore(openFromLocalTime.toDateTimeToday().plusDays(1))) {
                if (isWeekend(expirationLocalDateTime)) {
                    expirationLocalDateTime = expirationLocalDateTime.plusDays(2); // ff weekend
                }

                expirationDateTime = expirationLocalDateTime.plusHours(24 - expirationLocalDateTime.getHourOfDay() + openFromLocalTime.getHourOfDay()).toDateTime();
                return expirationDateTime.toDateTime().getMillis();
            }
            return expiration;
        }
    }

    private static Map<String, Optional<HoursRecord>> fetchTradingHours(SyncAPIConnector connector, List<String> tradeRecordsSymbols) throws APICommandConstructionException, APICommunicationException, APIReplyParseException, APIErrorResponse {
        // Get trading hours for all transactions
        TradingHoursResponse tradingHoursResponse = executeTradingHoursCommand(connector, tradeRecordsSymbols);
        Map<String, Optional<HoursRecord>> tradingHours = new HashMap<>();
        for (String tradeRecordSymbol : tradeRecordsSymbols) {
            int index = tradingHoursResponse.getSymbols().indexOf(tradeRecordSymbol);
            Optional<HoursRecord> todayHours = tradingHoursResponse.getTrading().get(index)
                    .stream()
                    .filter(h -> (int) h.getDay() == now().getDayOfWeek())
                    .findFirst();
            tradingHours.put(tradeRecordSymbol, todayHours);
        }
        return tradingHours;
    }
}
