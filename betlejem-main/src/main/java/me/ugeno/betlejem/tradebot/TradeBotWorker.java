package me.ugeno.betlejem.tradebot;

import me.ugeno.betlejem.common.data.BuyForPredictionOrderInfo;
import me.ugeno.betlejem.common.data.MarketPredictions;
import me.ugeno.betlejem.common.data.Prediction;
import me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants;
import me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils;
import me.ugeno.betlejem.tradebot.expose.PredictionsRepository;
import me.ugeno.betlejem.tradebot.trainer.TrainingDataGenerator;
import me.ugeno.betlejem.tradebot.xtb.TradeStrategy;
import me.ugeno.betlejem.xtb.XtbClient;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pro.xstore.api.message.error.APICommandConstructionException;
import pro.xstore.api.message.error.APICommunicationException;
import pro.xstore.api.message.error.APIReplyParseException;
import pro.xstore.api.message.records.StepRecord;
import pro.xstore.api.message.records.SymbolRecord;
import pro.xstore.api.message.response.APIErrorResponse;
import pro.xstore.api.message.response.AllSymbolsResponse;
import pro.xstore.api.message.response.StepRulesResponse;
import pro.xstore.api.message.response.SymbolResponse;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.CURRENCY_PLN;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.CURRENCY_USD;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.DATA_PATH_GPW_D1;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.DATA_PATH_GPW_M5;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.DATA_PATH_US_D1;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.DATA_PATH_US_M5;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.TRAINING_DATA_DATE_FORMAT;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.TRAINING_DATA_DATE_FORMAT_MIN;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.VALOR_NAME_ALL;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils.TYPE_GPW_STC_DEMO;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils.TYPE_GPW_STC_REAL;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils.TYPE_US_STC_DEMO;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils.TYPE_US_STC_REAL;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils.extractValuable;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils.inGpwMarketOpenHours;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils.inUsMarketOpenHours;
import static me.ugeno.betlejem.tradebot.trainer.TrainingDataGenerator.fetchActiveValorsGpwXtb;
import static org.joda.time.LocalDateTime.now;
import static pro.xstore.api.message.command.APICommandFactory.executeAllSymbolsCommand;
import static pro.xstore.api.message.command.APICommandFactory.executeStepRulesCommand;
import static pro.xstore.api.message.command.APICommandFactory.executeSymbolCommand;
import static pro.xstore.api.sync.ServerData.ServerEnum.DEMO;

@SuppressWarnings({"Duplicates"})
@Component
public class TradeBotWorker extends XtbClient {
    private static final Logger LOG = LoggerFactory.getLogger(TradeBotWorker.class);
    private static final int F_DATA_RETRO_DAYS = 32;

    private List<SymbolRecord> tickersGpw;
    private List<SymbolRecord> tickersUs;

    private LocalDate lastDailyPredictionsDateUs;
    private LocalDate lastDailyPredictionsDateGpw;

    private Deque<Map<String, Prediction>> recentUsPredD1 = new ArrayDeque<>(3);
    private Deque<Map<String, Prediction>> recentUsPredM5 = new ArrayDeque<>(3);
    private Deque<Map<String, Prediction>> recentGpwPredD1 = new ArrayDeque<>(3);
    private Deque<Map<String, Prediction>> recentGpwPredM5 = new ArrayDeque<>(3);

    private List<BuyForPredictionOrderInfo> recentBuyOrders = new LinkedList<>();

    private boolean doUs;
    private Map<Integer, List<StepRecord>> stepRules;

    @Autowired
    public TradeBotWorker() {
    }

    @PostConstruct
    public void init() {
        connect();
    }

    @SuppressWarnings("Duplicates")
    @Scheduled(fixedDelay = 10000)
    public void mainLoop() {
        DateTime now = DateTime.now();
        if (now.getHourOfDay() < 8 || now.getHourOfDay() > 23) {
            LOG.debug("\n\nSleeping...");
            return;
        }

        LocalDateTime begin = now();
        if (LOG.isDebugEnabled()) {
            LOG.debug("\n\nEntering XTB prices check...");
        }

        try {
            if (connected) {
                LOG.debug("Connection is active.");

                initStepRulesIfNecessary();

                if (tickersUs == null) { // TODO: this is probably needed once a day
                    tickersUs = fetchAllSymbolsUs();
                }

                if (tickersGpw == null) { // TODO: this is probably needed once a day
                    tickersGpw = fetchAllSymbolsGpw();
                }

                double commission = 0.002;
                if (doUs) {
                    MarketPredictions latestPredictions = updateUsMarket();
                    handleUsPredictions(latestPredictions, commission);
//                    doUs = false; // TODO: Uncomment / testing USD account
                } else {
                    MarketPredictions lastPredictions = updateGpw();
                    handleGpwPredictions(lastPredictions, commission);
                    doUs = true;
                }
            } else {
                throw new Exception("Connection seems to be inactive...");
            }
        } catch (Exception e) {
            System.err.printf("Error: %s - %s%n", e.getClass().getSimpleName(), e.getMessage());
            LOG.debug("Attempting to (re-)connect...");

            try {
                Thread.sleep(60000);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
            connect();
        }

        LocalDateTime end = now();
        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("Exiting XTB prices check... Took: %.2f min", new Interval(begin.toDateTime(), end.toDateTime()).toDurationMillis() / 60000f));
        }
    }

    private void initStepRulesIfNecessary() throws APICommandConstructionException, APICommunicationException, APIReplyParseException, APIErrorResponse {
        if (stepRules == null || stepRules.isEmpty()) {
            LOG.debug("Initializing step rules...");

            stepRules = new HashMap<>();
            StepRulesResponse stepRulesResponse = executeStepRulesCommand(connector);
            List<Integer> stepRuleIds = stepRulesResponse.getIds();
            for (int i = 0; i < stepRuleIds.size(); i++) {
                Integer rulesId = stepRuleIds.get(i);
                List<StepRecord> rules = stepRulesResponse.getStepRecords().get(i);
                stepRules.put(rulesId, rules);
            }
        }
    }

    private void handleUsPredictions(MarketPredictions latestPredictions, double commission) {
        LOG.debug("\n\n======================");
        LOG.debug("Predictions for US-d1:");
        Optional.ofNullable(latestPredictions.getPredictions_d1()).ifPresent(p -> p.keySet().stream()
                .sorted(Comparator.comparing(key -> p.get(key).getAvg()).reversed())
                .forEach(key -> LOG.debug(String.valueOf(p.get(key)))));

        LOG.debug("Predictions for US-m5:");
        Map<String, Prediction> recentPredictionsM5 = latestPredictions.getPredictions_m5();
        Optional.ofNullable(recentPredictionsM5).ifPresent(p -> p.keySet().stream()
                .sorted(Comparator.comparing(key -> p.get(key).getAvg()).reversed())
                .forEach(key -> LOG.debug(String.valueOf(p.get(key)))));
        LOG.debug("======================\n\n");

        try {
            if (false) {
                SymbolResponse symbolRecordResponse = executeSymbolCommand(connector, "USDPLN");
                SymbolRecord symbolRecord = symbolRecordResponse.getSymbol();

                double currencyPrice = symbolRecord.getAsk(); // TODO: testing USD account
            }
            double currencyPrice = 1.;
            LOG.info(String.format("USD/PLN=%.4f", currencyPrice));

            TradeStrategy.applyTradingStrategy(connector, tickersUs, stepRules, currencyPrice, commission, recentBuyOrders, recentUsPredM5, recentUsPredD1);
        } catch (Exception e) {
            LOG.error("Could not retrieve USD/PLN price");
            e.printStackTrace();
        }
    }

    private void handleGpwPredictions(MarketPredictions lastPredictions, double comission) {
        LOG.debug("\n\n=======================");
        LOG.debug("Predictions for GPW-d1:");
        Optional.ofNullable(lastPredictions.getPredictions_d1()).ifPresent(p -> p.keySet().stream()
                .sorted(Comparator.comparing(key -> p.get(key).getAvg()).reversed())
                .forEach(key -> LOG.debug(String.valueOf(p.get(key)))));

        LOG.debug("Predictions for GPW-m5:");
        Map<String, Prediction> recentPredictionsM5 = lastPredictions.getPredictions_m5();
        Optional.ofNullable(recentPredictionsM5).ifPresent(p -> p.keySet().stream()
                .sorted(Comparator.comparing(key -> p.get(key).getAvg()).reversed())
                .forEach(key -> LOG.debug(String.valueOf(p.get(key)))));
        LOG.debug("=======================\n\n");

        TradeStrategy.applyTradingStrategy(connector, tickersGpw, stepRules, 1, comission, recentBuyOrders, recentGpwPredM5, recentGpwPredD1);
    }

    private MarketPredictions updateGpw() throws IOException, InterruptedException {
        LocalDateTime now = now();
        LocalDate today = now.toLocalDate();

        Map<String, Prediction> predictions_d1 = recentGpwPredD1.peekLast();
        if (!today.equals(lastDailyPredictionsDateGpw)) {
            int hourOfDay = now.getHourOfDay();
            if (hourOfDay > 6) { // give scrapper a chance to get full list of prices
                predictions_d1 = fetchAndPredictGpwD1();
            } else {
                LOG.debug("To early for GPW daily predictions... Hour of day: " + hourOfDay);
            }

            lastDailyPredictionsDateGpw = today;
        }

        Map<String, Prediction> predictions_m5 = recentGpwPredM5.peekLast();
        if (inGpwMarketOpenHours(now)) {
            predictions_m5 = fetchAndPredictGpwM5();
        }

        return new MarketPredictions(predictions_d1, predictions_m5);
    }

    private MarketPredictions updateUsMarket() throws IOException, InterruptedException {
        LocalDateTime now = now();
        LocalDate today = now.toLocalDate();

        Map<String, Prediction> predictions_d1 = recentUsPredD1.peekLast();
        if (!today.equals(lastDailyPredictionsDateUs)) {
            int hourOfDay = now.getHourOfDay();
            if (hourOfDay > 6) { // give scrapper a chance to get full list of prices
                predictions_d1 = fetchAndPredictUsD1();
            } else {
                LOG.debug("To early for US daily predictions... Hour of day: " + hourOfDay);
            }

            lastDailyPredictionsDateUs = today;
        }

        Map<String, Prediction> predictions_m5 = recentUsPredM5.peekLast();
        if (inUsMarketOpenHours(now)) {
            predictions_m5 = fetchAndPredictUsM5();
        }

        return new MarketPredictions(predictions_d1, predictions_m5);
    }

    private Map<String, Prediction> fetchAndPredictGpwM5() throws IOException, InterruptedException {
        Map<String, Prediction> predictions_m5 = predict_gpw_m5();
        recentGpwPredM5.addLast(predictions_m5);
        return predictions_m5;
    }

    private Map<String, Prediction> fetchAndPredictUsM5() throws IOException, InterruptedException {
        Map<String, Prediction> predictions_m5 = predict_us_m5();
        recentUsPredM5.addLast(predictions_m5);
        return predictions_m5;
    }

    private Map<String, Prediction> fetchAndPredictGpwD1() throws IOException, InterruptedException {
        Map<String, Prediction> predictions_d1 = predict_gpw_d1();
        recentGpwPredD1.addLast(predictions_d1);
        return predictions_d1;
    }

    private Map<String, Prediction> fetchAndPredictUsD1() throws IOException, InterruptedException {
        Map<String, Prediction> predictions_d1 = predict_us_d1();
        recentUsPredD1.addLast(predictions_d1);
        return predictions_d1;
    }

    private Map<String, Prediction> predict_us_d1() throws IOException, InterruptedException {
        String predictionScriptName = "predict_rnn_us_d1.py";

        TrainingDataGenerator trainer = new TrainingDataGenerator(VALOR_NAME_ALL, BetlejemXtbConstants.F_DATA_RETRO_DAYS);
        List<String> activeValorsToConsider = trainer.fetchActiveTickersUs();
        trainer.prepareDataForEvaluation(DATA_PATH_US_D1, activeValorsToConsider, TRAINING_DATA_DATE_FORMAT, "");
        trainer.runTfDataEvaluationUs(predictionScriptName);

        List<Prediction> predictionResults = PredictionsRepository.load_recent_evaluations(DATA_PATH_US_D1, TRAINING_DATA_DATE_FORMAT, "04in01", "04in02", "04in03", "sell_04");
        return extractValuable(predictionResults);
    }

    private Map<String, Prediction> predict_gpw_d1() throws IOException, InterruptedException {
        String predictionScriptName = "predict_rnn_gpw_d1.py";

        TrainingDataGenerator trainer = new TrainingDataGenerator(VALOR_NAME_ALL, F_DATA_RETRO_DAYS);
        List<String> activeValorsToConsider = fetchActiveValorsGpwXtb();
        trainer.prepareDataForEvaluation(DATA_PATH_GPW_D1, activeValorsToConsider, TRAINING_DATA_DATE_FORMAT, "");
        trainer.runTfDataEvaluationUs(predictionScriptName);

        List<Prediction> predictionResults = PredictionsRepository.load_recent_evaluations(DATA_PATH_GPW_D1, TRAINING_DATA_DATE_FORMAT, "04in01", "04in02", "04in03", "sell_04");
        return extractValuable(predictionResults);
    }

    private Map<String, Prediction> predict_us_m5() throws IOException, InterruptedException {
        String predictionScriptName = "predict_rnn_us_m5.py";

        TrainingDataGenerator trainer = new TrainingDataGenerator(VALOR_NAME_ALL, BetlejemXtbConstants.F_DATA_RETRO_DAYS_192);
        List<String> activeValorsToConsider = trainer.fetchActiveTickersUs();
        trainer.prepareDataForEvaluation(DATA_PATH_US_M5, activeValorsToConsider, TRAINING_DATA_DATE_FORMAT_MIN, "");
        trainer.runTfDataEvaluationUs(predictionScriptName);

        List<Prediction> predictionResults = PredictionsRepository.load_recent_evaluations(DATA_PATH_US_M5, TRAINING_DATA_DATE_FORMAT_MIN, "03in12", "03in13", "03in14", "sell_03");
        return extractValuable(predictionResults);
    }

    private Map<String, Prediction> predict_gpw_m5() throws IOException, InterruptedException {
        String predictionScriptName = "predict_rnn_gpw_m5.py";

        TrainingDataGenerator trainer = new TrainingDataGenerator(VALOR_NAME_ALL, BetlejemXtbConstants.F_DATA_RETRO_DAYS);
        List<String> activeValorsToConsider = fetchActiveValorsGpwXtb();
        trainer.prepareDataForEvaluation(DATA_PATH_GPW_M5, activeValorsToConsider, TRAINING_DATA_DATE_FORMAT_MIN, "");
        trainer.runTfDataEvaluationUs(predictionScriptName);

        List<Prediction> predictionResults = PredictionsRepository.load_recent_evaluations(DATA_PATH_GPW_M5, TRAINING_DATA_DATE_FORMAT_MIN, "03in12", "03in13", "03in14", "sell_03");
        return extractValuable(predictionResults);
    }

    private List<SymbolRecord> fetchAllSymbolsUs() throws APICommandConstructionException, APIReplyParseException, APICommunicationException, APIErrorResponse {
        AllSymbolsResponse all = executeAllSymbolsCommand(connector);
        return fetchAllSymbols(all, TYPE_US_STC_DEMO, TYPE_US_STC_REAL, CURRENCY_USD);
    }

    private List<SymbolRecord> fetchAllSymbolsGpw() throws APICommandConstructionException, APIReplyParseException, APICommunicationException, APIErrorResponse {
        AllSymbolsResponse all = executeAllSymbolsCommand(connector);
        return fetchAllSymbols(all, TYPE_GPW_STC_DEMO, TYPE_GPW_STC_REAL, CURRENCY_PLN);
    }

    private List<SymbolRecord> fetchAllSymbols(AllSymbolsResponse all, int typeStcDemo, int typeStcReal, String currency) {
        return all.getSymbolRecords()
                .stream()
                .filter(r -> r.getType() == (BetlejemXtbUtils.serverType == DEMO ? typeStcDemo : typeStcReal))
                .filter(r -> r.getCurrency().equals(currency))
                .filter(r -> r.getCategoryName().equals(BetlejemXtbUtils.CATEGORY))
                .collect(Collectors.toList());
    }
}