package me.ugeno.betlejem.lcalc.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.ugeno.betlejem.binance.data.LcalcPrediction;
import me.ugeno.betlejem.binance.prices.KafkaConnector;
import me.ugeno.betlejem.binance.training.DataSet;
import me.ugeno.betlejem.common.WindowsCommandExecutor;
import me.ugeno.betlejem.common.crypto.kafka.DataProviderKafka;
import me.ugeno.betlejem.common.utils.BetlejemUtils;
import me.ugeno.betlejem.lcalc.Lcalc;
import me.ugeno.betlejem.lcalc.LcalcTrainerApplication;
import me.ugeno.betlejem.lcalc.PricesDeserializer;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@SuppressWarnings("Duplicates")
@Component
public class LcalcEvaluationWorker {
    public static final String COMPONENT_NAME = "binance-prices-evaluator";

    private static final Logger LOG = LoggerFactory.getLogger(LcalcEvaluationWorker.class);
    private static final int MAX_MINUTES_IN_PAST_ALLOWED = 2;
    private static final String TOPIC_READY_FOR_EVAL = "ready_for_eval";
    public static final String CMD_PYTHON_BIN = "python";

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final LcalcEvaluationCache evaluationCache;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public LcalcEvaluationWorker(KafkaTemplate<String, String> kafkaTemplate, LcalcEvaluationCache evaluationCache) {
        this.kafkaTemplate = kafkaTemplate;
        this.evaluationCache = evaluationCache;
    }

    // ETHUSDT

    @KafkaListener(topics = DataSet.ETHUSDT_1m.TOPIC_PRICES, groupId = COMPONENT_NAME)
    public void receivePrepareDataForEvalETHUSDT(ConsumerRecord<String, String> record) {
        prepareDataForEval(record, record.topic(), TOPIC_READY_FOR_EVAL, LcalcTrainerApplication.PAST_INTERVALS);
    }

    @KafkaListener(topics = DataSet.ETHUSDT_1m.TOPIC_EVAL_FINISHED, groupId = COMPONENT_NAME)
    public void receiveEvalFinishedForETHUSDT(ConsumerRecord<String, String> record) {
        uploadEvalResults(record, DataSet.ETHUSDT_1m.TOPIC_EVAL, DataSet.ETHUSDT_1m.TOPIC_PRICES);
    }

    @SuppressWarnings("SameParameterValue")
    private void prepareDataForEval(ConsumerRecord<String, String> record, String pricesTopic, String evalPendingTopic, int pastIntervals) {
        String key = record.key();
        String message = record.value();
        LOG.info(pricesTopic + " >>> EVAL data preparation - new message: " + message + ", record key: " + key);

        DateTimeFormatter dateFormatter = new DateTimeFormatterBuilder().appendPattern(KafkaConnector.KEY_DATE_PATTERN).toFormatter();
        DateTime recordKey = DateTime.parse(key, dateFormatter);
        LocalDateTime timestampMs = LocalDateTime.now();
        if (timestampMs.minusMinutes(MAX_MINUTES_IN_PAST_ALLOWED).isAfter(recordKey.toLocalDateTime())) {
            LOG.info(pricesTopic + " >>> Skipping message with obsolete key.");
            return;
        }

        Lcalc lcalc = new Lcalc(false);
        DataProviderKafka dataProviderKafka = new DataProviderKafka();
        List<String[]> inputDataset = dataProviderKafka.fetchRecentFrom(pricesTopic, 2048, new PricesDeserializer());

        int size = inputDataset.size();
        LOG.debug(pricesTopic + " >>> DATASET length: " + size);
        if (!inputDataset.isEmpty()) {
            String[] lastElement = inputDataset.get(size - 1);
            LOG.debug(pricesTopic + " >>> Latest retrieved element: " + Arrays.asList(lastElement));
            String timestamp = lastElement[0];
            String price = lastElement[1];
            if (!timestamp.equals(key) || !message.contains(price)) {
                LOG.warn("Obsolete record received. Skipping...");
                return;
            }
        }

        String timestampFile = timestampMs.toString("YYYY-MM-dd");
        Optional<String> fileMaybe = lcalc.prepareDataForEvaluation(LcalcTrainerApplication.BASE_PATH, inputDataset, pastIntervals, pricesTopic, timestampFile);
        fileMaybe.ifPresent(s -> {
            String timestampNotification = timestampMs.toString(KafkaConnector.KEY_DATE_PATTERN);
            uploadReadyForEval(s, timestampNotification, evalPendingTopic);
        });

        LOG.debug(pricesTopic + " >>> Ready for EVAL notification sent to '{}'...", evalPendingTopic);
    }

    @SuppressWarnings("SameParameterValue")
    private void uploadEvalResults(ConsumerRecord<String, String> record, String topicEval, String pricesTopic) {
        String evalProcessingTimestamp = record.value();
        String recordTimestamp = new DateTime(record.timestamp()).toLocalDateTime().toString(KafkaConnector.KEY_DATE_PATTERN);
        LOG.debug(pricesTopic + " >>> EVAL result processing starting - new message: " + evalProcessingTimestamp + ", record timestamp: " + recordTimestamp);
        List<LcalcPrediction> predictions = loadRecentEvaluations(LcalcTrainerApplication.BASE_PATH, pricesTopic);

        LcalcPrediction lastPrediction = null;
        for (LcalcPrediction prediction : predictions) {
            if (evalProcessingTimestamp.contains(prediction.getDate())) { // TODO: should be checked against now() too
                LOG.info("Prediction: {}", prediction);
                lastPrediction = prediction;
            } else {
                LOG.warn("Obsolete prediction received {}", prediction);
            }
        }

        if (lastPrediction != null) {
            List<LcalcPrediction> singlePredictionList = List.of(lastPrediction);
            evaluationCache.store(pricesTopic, singlePredictionList);
            uploadEval(singlePredictionList, topicEval);
        }

        LOG.debug(pricesTopic + " >>> EVAL stored...");
    }

    private void uploadReadyForEval(String filename, String key, String topicName) {
        try {
            kafkaTemplate.send(new ProducerRecord<>(topicName, key, objectMapper.writeValueAsString(filename)));
        } catch (JsonProcessingException e) {
            BetlejemUtils.logError(e);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void uploadEval(List<LcalcPrediction> recordsList, String topicName) {
        for (LcalcPrediction record : recordsList) {
            try {
                kafkaTemplate.send(new ProducerRecord<>(topicName, record.getDate(), objectMapper.writeValueAsString(record)));
            } catch (JsonProcessingException e) {
                BetlejemUtils.logError(e);
            }
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public static String runPythonScript(String scriptDir, final String scriptFilename) throws IOException, InterruptedException {
        WindowsCommandExecutor wce = new WindowsCommandExecutor();
        return wce.executeInWindowsCmd(String.format("%s %s", CMD_PYTHON_BIN, scriptFilename), new File(scriptDir));
    }

    private static String pathDataEvaluation(String basePath) {
        return String.format("%s%sevaluation", basePath, File.separator);
    }

    public static final String BETLEJEM_NEURAL_NETWORKS_SCRIPTS_PATH = ".\\betlejem-neural-networks\\src\\lcalc";

    private static final String EVAL_FILE_PATTERN = "%s/results_%s%s.csv";

    public static List<LcalcPrediction> loadRecentEvaluations(String basePath, String datasetName) {
        LocalDate today = LocalDate.now().toDateTimeAtStartOfDay().toLocalDateTime().toLocalDate();
        String datasetNameStr = datasetName.isEmpty() ? "" : datasetName + "_";
        String evaluationResultsFilename = String.format(EVAL_FILE_PATTERN, pathDataEvaluation(basePath), datasetNameStr, today); //e.g. "results_ETHUSDT_1m_2022-01-16.csv"

        List<LcalcPrediction> predictions = new ArrayList<>();
        try {
            LOG.debug("Loading evaluation results from: {}", evaluationResultsFilename);

            if (!new File(evaluationResultsFilename).exists()) {
                LOG.error(String.format("File not found: %s%n", evaluationResultsFilename));
                return List.of(); // only return if file for Today was generated
            }

            try (Reader evalResultsFileReader = new FileReader(evaluationResultsFilename)) {
                CSVParser evalResults = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(evalResultsFileReader);
                for (CSVRecord resultRecord : evalResults) {
                    String dateStr = resultRecord.get("date");
                    String name = resultRecord.get("name");
                    String price = resultRecord.get("price");
                    String sellStr = resultRecord.get("sell");
                    String passStr = resultRecord.get("pass");
                    String buyStr = resultRecord.get("buy");

                    LcalcPrediction pred = new LcalcPrediction();
                    pred.setDate(dateStr);
                    pred.setName(name);
                    pred.setPrice(new BigDecimal(price));
                    pred.setSell(new BigDecimal(sellStr));
                    pred.setPass(new BigDecimal(passStr));
                    pred.setBuy(new BigDecimal(buyStr));

                    predictions.add(pred);
                }

                evalResults.close();
            } catch (Exception e) {
                BetlejemUtils.logError(e);
            }
        } catch (Exception e) {
            BetlejemUtils.logError(e);
        }

        LOG.debug(String.format("%nLoaded %d positions.%n", predictions.size()));
        return predictions;
    }
}