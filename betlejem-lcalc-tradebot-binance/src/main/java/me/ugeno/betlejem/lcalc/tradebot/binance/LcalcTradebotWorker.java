package me.ugeno.betlejem.lcalc.tradebot.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.ugeno.betlejem.binance.prices.KafkaConnector;
import me.ugeno.betlejem.binance.training.DataSet;
import me.ugeno.betlejem.common.utils.BetlejemUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Optional;


@SuppressWarnings("Duplicates")
@Component
public class LcalcTradebotWorker {
    private static final Logger LOG = LoggerFactory.getLogger(LcalcTradebotWorker.class);

    static final String COMPONENT_NAME = "betlejem-prices-tradebot-binance";

    private static final int CONFIG_AUTO_TUNE_STEP = 720;
    private static final int CONFIG_AUTO_TUNE_INIT_OPTIMUM = 10320; // ~7 days

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private final KafkaTemplate<String, String> kafkaTemplate;
    private static final int MAX_MINUTES_IN_PAST_ALLOWED = 2;

    @Autowired
    public LcalcTradebotWorker(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    // =========================================ETHUSDT==================

    private static final PairConfig PAIR_CONFIG_ETHUSDT = new PairConfig(
            DataSet.ETHUSDT_1m.SYMBOL_PRI,
            DataSet.ETHUSDT_1m.SYMBOL_SEC,
            DataSet.ETHUSDT_1m.SCALE_PRI,
            DataSet.ETHUSDT_1m.SCALE_SEC,
            DataSet.ETHUSDT_1m.MIN_TX_VALUE_IN_SEC
    );
    private final LcalcTradebot lcalcBotETHUSDT = new LcalcTradebot(PAIR_CONFIG_ETHUSDT);
    private final TradebotConfigAutoTune tradebotConfigAutoTune_ETHUSDT = new TradebotConfigAutoTune(PAIR_CONFIG_ETHUSDT);

    @Scheduled(fixedDelay = 1000, initialDelay = 10000)
    public void recalculateConfigurationETHUSDT() {
        String startTime = LocalDateTime.now().toString(KafkaConnector.KEY_DATE_PATTERN);
        int simMinutes = CONFIG_AUTO_TUNE_INIT_OPTIMUM;
        Optional<TradebotConfig> config = tradebotConfigAutoTune_ETHUSDT.tuneConfig(lcalcBotETHUSDT.getBalancePri(), lcalcBotETHUSDT.getBalanceSec(), DataSet.ETHUSDT_1m.TOPIC_EVAL, simMinutes, CONFIG_AUTO_TUNE_STEP);
        String endTime = LocalDateTime.now().toString(KafkaConnector.KEY_DATE_PATTERN);
        config.ifPresent(c -> storeConfig(c, startTime, endTime, "config_diary.txt", simMinutes));
        config.ifPresent(lcalcBotETHUSDT::updateConfig);
    }

    @KafkaListener(topics = DataSet.ETHUSDT_1m.TOPIC_EVAL, groupId = COMPONENT_NAME)
    public void notificationNewEvalETHUSDT(String message) {
        processNewEval(message, this.lcalcBotETHUSDT);
    }

    private void processNewEval(String message, LcalcTradebot lcalcBot) {
        try {
            JsonNode result = new ObjectMapper().readTree(message);
            String date = result.findValue("date").asText();
            double sell = result.findValue("sell").asDouble();
            double pass = result.findValue("pass").asDouble();
            double buy = result.findValue("buy").asDouble();
            double price = result.findValue("price").asDouble();

            DateTimeFormatter dateFormatter = new DateTimeFormatterBuilder().appendPattern(KafkaConnector.KEY_DATE_PATTERN).toFormatter();
            DateTime entryTimestamp = DateTime.parse(date, dateFormatter);
            if (LocalDateTime.now().minusMinutes(MAX_MINUTES_IN_PAST_ALLOWED).isAfter(entryTimestamp.toLocalDateTime())) {
                LOG.warn("Received entry is older than {} minutes, skipping...", MAX_MINUTES_IN_PAST_ALLOWED);
            } else {
                lcalcBot.receive(date, sell, pass, buy, price);
            }
        } catch (Exception e) {
            BetlejemUtils.logError(e);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void storeConfig(TradebotConfig config, String startTimeStr, String endTimeStr, String configDiaryFilename, int simMinutes) {
        try (FileWriter fileWriter = new FileWriter(configDiaryFilename, true)) {
            try (BufferedWriter bufferedWriter = new BufferedWriter(fileWriter)) {
                String line = String.format("genStart: %s genEnd: %s simMinutes: %10d txAmount: %3d minGainDir: %5.3f minGainRev: %5.3f certLvlDir: %5.2f certLvlRev: %5.2f override: N/A wealthChange: %5.3f%n",
                        startTimeStr, endTimeStr, simMinutes,
                        config.getTxAmount(),
                        config.getMinGainDirect(),
                        config.getMinGainReverse(),
                        config.getCertaintyLvlDirect(),
                        config.getCertaintyLvlReverse(),
                        config.getWealthChange().doubleValue()
                );
                bufferedWriter.write(line);
            }
        } catch (IOException e) {
            LOG.error("Issue with writing to file " + configDiaryFilename + ", tradebot config: " + config);
            BetlejemUtils.logError(e);
        }
    }
}