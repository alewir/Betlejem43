package me.ugeno.betlejem.binance.prices;

import me.ugeno.betlejem.binance.training.DataSet;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.TopicPartition;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.openapitools.client.ApiException;
import org.openapitools.client.api.MarketApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.joda.time.LocalDateTime.now;

@SuppressWarnings("Duplicates")
@Component
public class ScrapperWorker implements KafkaConnector, BinanceConnector {
    private static final Logger LOG = LoggerFactory.getLogger(ScrapperWorker.class);
    private static final String APP_NAME = "betlejem-binance-price-scrapper-app";

    private boolean connected = Boolean.FALSE;

    private Consumer<String, String> consumer;
    private Producer<String, String> producer;
    private MarketApi marketApi;

    private boolean initialized = false;

    @Scheduled(cron = "3,8,13,18 * * * * ?")
    public void mainLoop() throws IOException {
        LocalDateTime begin = now();
        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("\n\nEntering %s...", APP_NAME));
        }

        if (!initialized) {
            connect();

            initialized = true;
        }

        try {
            if (connected) {
                LOG.debug(String.format("%s connection is active.", APP_NAME));

                DateTime fromDate = DateTime.now().minusYears(1);
                uploadDataForPairs(DataSet.ETHUSDT_1m.SYMBOL_PRI, DataSet.ETHUSDT_1m.SYMBOL_SEC, "1m", fromDate, DataSet.ETHUSDT_1m.SCALE_SEC);
                // to scrap other just specify it here, e.g. uploadDataForPairs(DataSet.BTCUSDT_1m.SYMBOL_PRI, DataSet.BTCUSDT_1m.SYMBOL_SEC, "1m", fromDate, DataSet.BTCUSDT_1m.SCALE_SEC);
            } else {
                throw new Exception(String.format("%s seems not to be connected...", APP_NAME));
            }
        } catch (Exception e) {
            System.err.printf(String.format("%s error: %%s - %%s%%n", APP_NAME), e.getClass().getSimpleName(), e.getMessage());
            disconnect();

            LOG.debug(String.format("%s attempting to (re-)connect...", APP_NAME));
            sleepForSec(15);

            connect();
        }

        LocalDateTime end = now();
        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("Exiting %s... Took: %.2f min", APP_NAME, new Interval(begin.toDateTime(), end.toDateTime()).toDurationMillis() / 60000f));
        }
    }

    private void connect() throws IOException {
        producer = prepareProducer();
        consumer = prepareConsumer(APP_NAME);
        marketApi = prepareBinanceClientForMarketApi();

        connected = true;
    }

    private void disconnect() {
        if (connected) {
            consumer.close();
            producer.close();
        }
        connected = false;
    }

    @SuppressWarnings("SameParameterValue")
    private void uploadDataForPairs(String currency1, String currency2, String interval, DateTime fromDate, int scaleSec) throws ApiException {
        String symbol = currency1 + currency2;
        String topic = assembleTopicName(interval, symbol);
        System.out.printf("-> Processing %s%n", topic);

        List<TopicPartition> topicPartitions = Collections.singletonList(new TopicPartition(topic, 0));
        consumer.assign(topicPartitions);

        Optional<String> result = fetchLastEntryKey(consumer, topicPartitions);
        LocalDateTime latestEntryDate = result.map(r -> LocalDateTime.parse(r, DateTimeFormat.forPattern(KEY_DATE_PATTERN))).orElse(fromDate.toLocalDateTime());

        List<List<String>> recordsList = fetchRecords(interval, symbol, latestEntryDate);

        List<String> lastLine = List.of();
        if (recordsList.size() > 0) {
            lastLine = recordsList.get(recordsList.size() - 1);
        }

        System.out.printf("   Uploading %d record - %s%n", recordsList.size(), lastLine.isEmpty() ? "" : new BigDecimal(lastLine.get(IDX_SCRAP_IN_OPEN)).setScale(scaleSec, RoundingMode.HALF_EVEN));
        this.uploadPrices(recordsList, topic, producer, KEY_DATE_PATTERN, scaleSec);
    }

    private List<List<String>> fetchRecords(String interval, String symbol, LocalDateTime latestEntryDate) throws ApiException {
        DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern(KEY_DATE_PATTERN);
        System.out.printf("  Latest stored date: %s%n", dateTimeFormatter.print(latestEntryDate));
        return marketApi.apiV3KlinesGet(symbol, interval, latestEntryDate.plusSeconds(1).toDateTime().getMillis(), DateTime.now().getMillis(), 1000);
    }

    @NotNull
    private static String assembleTopicName(String interval, String symbol) {
        return String.format("%s_%s", symbol, interval);
    }

    private static void sleepForSec(int sec) {
        try {
            Thread.sleep(sec * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}