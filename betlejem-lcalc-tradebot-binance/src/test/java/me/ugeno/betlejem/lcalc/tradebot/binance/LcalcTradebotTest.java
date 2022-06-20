package me.ugeno.betlejem.lcalc.tradebot.binance;

import me.ugeno.betlejem.binance.data.LcalcPrediction;
import me.ugeno.betlejem.binance.prices.BinanceConnector;
import me.ugeno.betlejem.binance.prices.KafkaConnector;
import me.ugeno.betlejem.binance.training.DataSet;
import me.ugeno.betlejem.common.crypto.kafka.DataProviderKafka;
import me.ugeno.betlejem.common.crypto.kafka.EvalDeserializer;
import me.ugeno.betlejem.lcalc.Lcalc;
import me.ugeno.betlejem.lcalc.LcalcTrainerApplication;
import me.ugeno.betlejem.lcalc.PricesDeserializer;
import me.ugeno.betlejem.lcalc.evaluation.LcalcEvaluationWorker;
import org.joda.time.LocalDateTime;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openapitools.client.ApiException;
import org.openapitools.client.api.TradeApi;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static me.ugeno.betlejem.lcalc.LcalcTrainerApplication.LOSS_PERCENTAGE;
import static me.ugeno.betlejem.lcalc.LcalcTrainerApplication.PAST_INTERVALS;
import static me.ugeno.betlejem.lcalc.tradebot.binance.LcalcTradebot.CERTAINTY_LVL_DIRECT;
import static me.ugeno.betlejem.lcalc.tradebot.binance.LcalcTradebot.CERTAINTY_LVL_REVERSE;
import static me.ugeno.betlejem.lcalc.tradebot.binance.LcalcTradebot.MIN_GAIN_DIRECT;
import static me.ugeno.betlejem.lcalc.tradebot.binance.LcalcTradebot.MIN_GAIN_REVERSE;
import static me.ugeno.betlejem.lcalc.tradebot.binance.LcalcTradebot.SIMULATED_INIT_BALANCE_PRI;
import static me.ugeno.betlejem.lcalc.tradebot.binance.LcalcTradebot.SIMULATED_INIT_BALANCE_SEC;
import static me.ugeno.betlejem.lcalc.tradebot.binance.TradebotConfigAutoTune.extractLatestPrice;

/**
 * Created by alwi on 10/12/2021.
 * All rights reserved.
 */
@SuppressWarnings("Duplicates")
@Disabled
        // These are not really unit tests.. just some semi-auto testing scripts
class LcalcTradebotTest {
    @SuppressWarnings("FieldCanBeLocal")
    private final String topicPrices = DataSet.ETHUSDT_1m.TOPIC_PRICES;
    private final String topicEval = DataSet.ETHUSDT_1m.TOPIC_EVAL;
    private PairConfig pairConfig = new PairConfig(
            DataSet.ETHUSDT_1m.SYMBOL_PRI,
            DataSet.ETHUSDT_1m.SYMBOL_SEC,
            DataSet.ETHUSDT_1m.SCALE_PRI,
            DataSet.ETHUSDT_1m.SCALE_SEC,
            DataSet.ETHUSDT_1m.MIN_TX_VALUE_IN_SEC
    );

    @Test
    void runSimulation() {
        Lcalc lcalc = new Lcalc(true);
        lcalc.runSimulation("e:\\ml\\data\\lcalc\\v12_ETHUSDT_1m.forward003i_limit00010_past256_loss05_dt_000.csv", "tmp_out.csv", 0, 75, PAST_INTERVALS, LOSS_PERCENTAGE);
    }

    @Test
    void checkConfigurationAutoTune() {
        int minDataRequired = 360;
        int simRecentMinutes = 5760;
        TradebotConfigAutoTune calc = new TradebotConfigAutoTune(pairConfig);
        BigDecimal simulatedInitUsd = BigDecimal.valueOf(Double.parseDouble("800"));
        BigDecimal simulatedInitEth = BigDecimal.valueOf(Double.parseDouble("0"));
        System.out.println("RESULT: " + calc.tuneConfig(simulatedInitEth, simulatedInitUsd, topicEval, simRecentMinutes, minDataRequired));
    }

    @Test
    void prepareDataForEvaluation() throws IOException, InterruptedException {
        Lcalc lcalc = new Lcalc(false);
        DataProviderKafka dataProviderKafka = new DataProviderKafka();
        List<String[]> inputDataset = dataProviderKafka.fetchRecentFrom(topicPrices, 2048, new PricesDeserializer());
        System.out.println("DATASET length: " + inputDataset.size());

        lcalc.prepareDataForEvaluation(LcalcTrainerApplication.BASE_PATH, inputDataset, LcalcTrainerApplication.PAST_INTERVALS, topicPrices, LocalDateTime.now().toString(KafkaConnector.KEY_DATE_PATTERN));
        LcalcEvaluationWorker.runPythonScript(LcalcEvaluationWorker.BETLEJEM_NEURAL_NETWORKS_SCRIPTS_PATH, "predict_rnn_lcalc.py");
        List<LcalcPrediction> predictions = LcalcEvaluationWorker.loadRecentEvaluations(LcalcTrainerApplication.BASE_PATH, topicPrices);
        System.out.println(predictions);
    }

    @Test
    void checkBotOnHistoricalPredictions() {
        DataProviderKafka dataProviderKafka = new DataProviderKafka();
        int recent = 5760;
        List<String[]> predictions = dataProviderKafka.fetchRecentFrom(topicEval, recent, new EvalDeserializer());
        System.out.println("DATASET length: " + predictions.size());

        BigDecimal minGainDirect = new BigDecimal(0.025);
        BigDecimal minGainReverse = new BigDecimal(0.025);
        BigDecimal certaintyLvl = new BigDecimal(0.35);

        BigDecimal simulatedInitUsd = BigDecimal.valueOf(Double.parseDouble("1121"));
        BigDecimal simulatedInitEth = BigDecimal.valueOf(Double.parseDouble("2.3"));
        LcalcTradebot lcalcBot = new LcalcTradebot(pairConfig, true, certaintyLvl, certaintyLvl, minGainDirect, minGainReverse, simulatedInitEth, simulatedInitUsd, extractLatestPrice(predictions));
        for (String[] prediction : predictions) {
            recalculate(lcalcBot, prediction);
        }

        System.out.printf("RESULT: %7.2f USD (%+5d%%) %5.2f PRI + %5.2f SEC%n", lcalcBot.getWealth().doubleValue(), lcalcBot.getWealthChange().intValue(), lcalcBot.getBalancePri(), lcalcBot.getBalanceSec());
    }

    private void recalculate(LcalcTradebot lcalcBot, String[] prediction) {
        String date = prediction[0];
        double price = Double.parseDouble(prediction[1]);
        double sell = Double.parseDouble(prediction[2]);
        double pass = Double.parseDouble(prediction[3]);
        double buy = Double.parseDouble(prediction[4]);
        lcalcBot.receive(date, sell, pass, buy, price);
    }

    @Test
    void testConnectivity() throws IOException, ApiException {
        LcalcTradebot lcalcBot = new LcalcTradebot(pairConfig, false, CERTAINTY_LVL_DIRECT, CERTAINTY_LVL_REVERSE, MIN_GAIN_DIRECT, MIN_GAIN_REVERSE, SIMULATED_INIT_BALANCE_PRI, SIMULATED_INIT_BALANCE_SEC, BigDecimal.ZERO);
        lcalcBot.setOnline(true);
        TradeApi tradeApi = new BinanceConnector() {
        }.prepareBinanceClientForTradeApi();
        lcalcBot.updateAccountBalance(tradeApi);
        lcalcBot.postOrderQty("ETHUSDC", tradeApi, "BUY", new BigDecimal("0.005"), pairConfig.getScalePri());
    }
}
