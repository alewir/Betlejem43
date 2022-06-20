package me.ugeno.betlejem.binance.training;

import com.google.common.base.Strings;
import com.tictactec.ta.lib.Core;
import com.tictactec.ta.lib.MAType;
import com.tictactec.ta.lib.MInteger;
import com.tictactec.ta.lib.RetCode;
import me.ugeno.betlejem.binance.prices.KafkaConnector;
import me.ugeno.betlejem.common.crypto.kafka.KafkaDeserializer;
import me.ugeno.betlejem.common.utils.BetlejemUtils;
import me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.javatuples.Quartet;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.Integer.MAX_VALUE;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_EVEN;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.BASE_DATA_PATH;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.F_DATA_RETRO_DAYS_192;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.NEAR_ZERO;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.ONE_HUNDRED_PROC;

/**
 * Created by alwi on 10/02/2021.
 * All rights reserved.
 */
@SuppressWarnings({"Duplicates", "WeakerAccess", "SameParameterValue"})
public class BinanceTrainingDataGenerator implements KafkaConnector, KafkaDeserializer {
    private static final Logger LOG = LoggerFactory.getLogger(BinanceTrainingDataGenerator.class);

    public static final String INIT_SCRAP_DATE = "2018-01-01";

    private static final double MAX_PERCENTAGE_ZERO_DELTAS_IN_FEATURES = 0.75;

    public static final String KEY_TIMESTAMP = "DATE";
    public static final String KEY_PRICE = "PRICE";
    public static final String KEY_DELTA = "DELTA";

    private static final String KEY_ENH_BB_UP = "BB_UP";
    private static final String KEY_ENH_BB_MID = "BB_MID";
    private static final String KEY_ENH_BB_LOW = "BB_LOW";
    private static final String KEY_ENH_EMA_128 = "EMA_128";
    private static final String KEY_ENH_EMA_256 = "EMA_256";
    private static final String KEY_ENH_EMA_1024 = "EMA_1024";

    public static final BigDecimal[] BOUNDARIES = new BigDecimal[]{
            new BigDecimal("-21.91"),
            new BigDecimal("-11.40"),
            new BigDecimal("-5.92"),
            new BigDecimal("-4.27"),
            new BigDecimal("-3.70"),
            new BigDecimal("-3.25"),
            new BigDecimal("-2.92"),
            new BigDecimal("-2.64"),
            new BigDecimal("-2.40"),
            new BigDecimal("-2.23"),
            new BigDecimal("-2.07"),
            new BigDecimal("-1.93"),
            new BigDecimal("-1.80"),
            new BigDecimal("-1.68"),
            new BigDecimal("-1.56"),
            new BigDecimal("-1.47"),
            new BigDecimal("-1.38"),
            new BigDecimal("-1.29"),
            new BigDecimal("-1.21"),
            new BigDecimal("-1.14"),
            new BigDecimal("-1.06"),
            new BigDecimal("-0.97"),
            new BigDecimal("-0.91"),
            new BigDecimal("-0.86"),
            new BigDecimal("-0.81"),
            new BigDecimal("-0.76"),
            new BigDecimal("-0.71"),
            new BigDecimal("-0.66"),
            new BigDecimal("-0.62"),
            new BigDecimal("-0.57"),
            new BigDecimal("-0.53"),
            new BigDecimal("-0.49"),
            new BigDecimal("-0.45"),
            new BigDecimal("-0.42"),
            new BigDecimal("-0.39"),
            new BigDecimal("-0.36"),
            new BigDecimal("-0.33"),
            new BigDecimal("-0.30"),
            new BigDecimal("-0.27"),
            new BigDecimal("-0.23"),
            new BigDecimal("-0.20"),
            new BigDecimal("-0.17"),
            new BigDecimal("-0.13"),
            new BigDecimal("-0.10"),
            new BigDecimal("-0.07"),
            new BigDecimal("-0.04"),
            ZERO,
            new BigDecimal("0.05"),
            new BigDecimal("0.09"),
            new BigDecimal("0.14"),
            new BigDecimal("0.18"),
            new BigDecimal("0.23"),
            new BigDecimal("0.28"),
            new BigDecimal("0.32"),
            new BigDecimal("0.36"),
            new BigDecimal("0.42"),
            new BigDecimal("0.47"),
            new BigDecimal("0.52"),
            new BigDecimal("0.57"),
            new BigDecimal("0.61"),
            new BigDecimal("0.67"),
            new BigDecimal("0.72"),
            new BigDecimal("0.78"),
            new BigDecimal("0.84"),
            new BigDecimal("0.90"),
            new BigDecimal("0.97"),
            new BigDecimal("1.04"),
            new BigDecimal("1.11"),
            new BigDecimal("1.20"),
            new BigDecimal("1.28"),
            new BigDecimal("1.37"),
            new BigDecimal("1.49"),
            new BigDecimal("1.58"),
            new BigDecimal("1.70"),
            new BigDecimal("1.83"),
            new BigDecimal("1.98"),
            new BigDecimal("2.12"),
            new BigDecimal("2.29"),
            new BigDecimal("2.49"),
            new BigDecimal("2.67"),
            new BigDecimal("2.94"),
            new BigDecimal("3.20"),
            new BigDecimal("3.60"),
            new BigDecimal("3.98"),
            new BigDecimal("4.87"),
            new BigDecimal("6.77"),
            new BigDecimal("16.27"),
            BigDecimal.valueOf(Long.MAX_VALUE)
    };

    private String specificDataset;
    private int daysInPast; // how many days in past to consider as input (training/prediction)?
    private int assumedPercentageIncrease = 5; // how much % increase to expect?
    private int daysInFuture = 3; // within how many days to calculate future increase?
    private boolean calculateOnlyForSpecificDay = true; // expect given increase only in specific future day or within all days up to it?

    public BinanceTrainingDataGenerator(String specificDataset, int daysInPast) {
        this.specificDataset = specificDataset;
        this.daysInPast = daysInPast;
    }

    private static boolean calculateLblConditionOnSpecDay(int assumedPercentageIncrease, BigDecimal lastFeatureClose, List<Map<String, BigDecimal>> lData, int futureDays) {
        // price increase > expectedPercentageIncrease during on specific day
        BigDecimal closeAtNthDay = lData.stream()
                .skip(futureDays - 1)
                .map(l -> l.get(KEY_PRICE))
                .findFirst()
                .orElse(BigDecimal.valueOf(-1));

        boolean noDecrease = increasing(lastFeatureClose, lData, futureDays);

        return noDecrease && checkPriceDiff(assumedPercentageIncrease, lastFeatureClose, closeAtNthDay);
    }

    private static boolean calculateLblConditionInAnyDay(int assumedPercentageIncrease, BigDecimal lastFeatureClose, List<Map<String, BigDecimal>> lData, int futureDays) {
        // price increase > expectedPercentageIncrease during any of next lData days
        BigDecimal maxCloseNextDays = lData.stream()
                .limit(futureDays)
                .map(l -> l.get(KEY_PRICE))
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.valueOf(-1));

        boolean noDecrease = increasing(lastFeatureClose, lData, futureDays);

        return noDecrease && checkPriceDiff(assumedPercentageIncrease, lastFeatureClose, maxCloseNextDays);
    }

    private static boolean increasing(BigDecimal lastFeatureClose, List<Map<String, BigDecimal>> lData, int futureDays) {
        return lData.stream()
                .limit(futureDays)
                .map(l -> l.get(KEY_PRICE).subtract(lastFeatureClose))
                .allMatch(l -> l.compareTo(ZERO) >= 0);
    }

    private static boolean checkPriceDiff(int assumedPercentageIncrease, BigDecimal lastFeatureClose, BigDecimal closeAtNthDay) {
        BigDecimal priceDiffNominal = closeAtNthDay.subtract(lastFeatureClose);
        BigDecimal priceDiffPercentage;
        if (lastFeatureClose.compareTo(ZERO) == 0) {
            priceDiffPercentage = ZERO;
        } else {
            priceDiffPercentage = priceDiffNominal.divide(lastFeatureClose, 3, HALF_EVEN).multiply(BigDecimal.valueOf(100));
        }
        return priceDiffPercentage.compareTo(BigDecimal.valueOf(assumedPercentageIncrease)) > 0;
    }

    private String getTrainingDataSequencesFilename(int assumedPercentageIncrease) {
        return String.format("train_%s_%din%d_%s.csv", LocalDate.now().toString(), assumedPercentageIncrease, daysInFuture, specificDataset);
    }

    /**
     * Analyze all test data to establish deltas distribution for training data normalization.
     * It is prediction model agnostic it only prints out statistical information for historical data.
     * NOTE:
     * This should be rare or even once-per-lifetime action.
     * This one does not scale data - only applies ranks to get balanced deltas distribution.
     * WARNING:
     * Assumes manual update of data normalization function afterwards which invalidates already trained models.
     *
     * @param normalize  disable it for the first run when trying to establish deltas distribution,
     *                   then enable to validate effects (printout) of applied normalization.
     * @param boundaries @Deprecated was used for normalization
     */
    void analyzeData(List<Map<String, BigDecimal>> dataset, boolean normalize, BigDecimal[] boundaries) {
        Map<BigDecimal, Integer> stats = new HashMap<>();
        dataset.forEach(record -> {
            BigDecimal deltaBD = record.get(KEY_DELTA);
            deltaBD = deltaBD.setScale(2, HALF_EVEN);
            if (normalize) {
                deltaBD = normalize(deltaBD, boundaries);
            }
            stats.merge(deltaBD, 1, (a, b) -> a + b);

        });
        List<Map.Entry<BigDecimal, Integer>> sorted = stats.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .peek(e -> LOG.info(e.getKey() + ": " + e.getValue()))
                .collect(Collectors.toList());

        int size = sorted.size();
        LOG.info("Unique deltas: " + size);
        LOG.info("All deltas: " + sorted.stream().map(Map.Entry::getValue).reduce(0, Integer::sum));

        if (!normalize) {
            int groupAmnt = 50;
            LOG.info("Unique deltas distribution: ");

            List<Map.Entry<BigDecimal, Integer>> negatives = sorted.stream()
                    .filter(e -> e.getKey().signum() < 0)
                    .sorted(Comparator.comparing(Map.Entry::getKey, Comparator.reverseOrder()))
                    .collect(Collectors.toList());
            calculateGroupBoundaries(negatives, false, groupAmnt);

            List<Map.Entry<BigDecimal, Integer>> positives = sorted.stream()
                    .filter(e -> e.getKey().signum() > 0)
                    .collect(Collectors.toList());
            calculateGroupBoundaries(positives, true, groupAmnt);
        }
    }

    public static void main(String[] args) {
        String datasetName = DataSet.ETHUSDC_1m.TOPIC_PRICES;
        String dataPath = String.format("%s\\%s", datasetName, BASE_DATA_PATH);
        int fDataRetroDays = BetlejemXtbConstants.F_DATA_RETRO_DAYS;

        BinanceTrainingDataGenerator trainer = new BinanceTrainingDataGenerator(datasetName, fDataRetroDays);
        int minIncrease = 1;
        int maxIncrease = 1;
        int minIntervals = 60;
        int maxIntervals = 60;
        boolean calculateOnlyForSpecificMoment = true;

        trainer.generateMultipleDatasets(minIncrease, maxIncrease, minIntervals, maxIntervals, calculateOnlyForSpecificMoment, datasetName, dataPath, KEY_DATE_PATTERN);
    }

    @SuppressWarnings("SameParameterValue")
    void generateMultipleDatasets(int minIncrease, int maxIncrease, int minDays, int maxDays, boolean calculateOnlyForSpecificMoment, String dataset, String dataPath, String inputTimestampFormat) {
        for (int days = minDays; days <= maxDays; days++) {
            for (int inc = minIncrease; inc <= maxIncrease; inc++) {
                prepareSingleDataset(inc, days, calculateOnlyForSpecificMoment, dataset, dataPath, inputTimestampFormat);
                LOG.info("=====================================================");
            }
        }
    }

    @SuppressWarnings({"SameParameterValue"})
    void prepareSingleDataset(int expectedIncrease, int withinDays, boolean calculateOnlyForSpecificMoment, String dataset, String dataPath, String inputTimestampFormat) {
        this.assumedPercentageIncrease = expectedIncrease;
        this.daysInFuture = withinDays; // within how many days to calculate future increase?
        this.calculateOnlyForSpecificDay = calculateOnlyForSpecificMoment;

        List<Map<String, BigDecimal>> data = prepareTrainingData(dataset, dataPath, inputTimestampFormat);
        trainingDataPostProcessing(dataPath);
        trainingDataAnalysis(dataPath);
        analyzeData(data, true, BOUNDARIES);
    }

    private String getTrainingDataSequencesFilenamePp(int assumedPercentageIncrease) {
        return String.format("train_%s_pp_x%d_%din%d_%s.csv", LocalDate.now().toString(), this.daysInPast, assumedPercentageIncrease, daysInFuture, specificDataset);
    }

    @Override
    public String[] rewrite(ConsumerRecord<String, String> element) {
        return new String[0];
    }

    /**
     * This one:
     * - applies normalization data
     * - scales normalized data
     * - prepares features sequences
     * - calculates conditions for labels
     * - prepares labels sequences
     * - reduces sequences with too many zero-deltas
     *
     * @param dataPath        where to find historical data
     * @param timestampFormat format of date in training pp files
     */
    private List<Map<String, BigDecimal>> prepareTrainingData(String dataset, String dataPath, String timestampFormat) {
        List<Map<String, BigDecimal>> datasetScrappedData = new ArrayList<>();

        DateTimeFormatter formatter = new DateTimeFormatterBuilder().appendPattern(BetlejemXtbConstants.TRAINING_DATA_DATE_FORMAT).toFormatter();
        LocalDate sinceDate = LocalDate.parse(INIT_SCRAP_DATE, formatter);

        String outputPath = String.format("%s%s%s", pathDataTraining(dataPath), File.separator, getTrainingDataSequencesFilename(assumedPercentageIncrease));
        LOG.info(String.format("Generating: %s%n", outputPath));
        try {
            FileWriter outputFileWriter = new FileWriter(outputPath, false);
            try (CSVPrinter outputPrinter = new CSVPrinter(outputFileWriter, CSVFormat.DEFAULT)) {
                boolean printStats = false; // stats are for frequency of predicted increase

                int statsTotal = 0;
                int increaseTotal = 0;
                int noIncreaseTotal = 0;
                int statsSkipped = 0;

                Map<String, BigDecimal> statsForIncrease = new LinkedHashMap<>();

                Consumer<String, String> consumer = prepareConsumer("betlejem-training-data-generator");
                List<TopicPartition> topicPartitions = Collections.singletonList(new TopicPartition(dataset, 0));
                consumer.assign(topicPartitions);

                TopicPartition partition = topicPartitions.stream().findFirst().orElse(null);
                consumer.seek(partition, 0);

                int readAmount;
                do {
                    long offset = consumer.position(partition);
                    consumer.seek(partition, offset);
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(RECORDS_PULL_TIMEOUT));
                    readAmount = records.count();
                    System.out.printf("Loaded records: %d from offset %d%n", readAmount, offset);
                    records.records(partition).forEach(element -> {
                        Map<String, String> record = extractRecord(element);
                        String dateStr = element.key();

                            /*
                              SAMPLE ELEMENT
                              {"open":"4296.70000000","high":"4462.38000000","low":"4282.24000000","close":"4445.45000000","volume":"14590.13660000","tx":"26764"}
                             */

                        String openStr = record.get("open");
                        String closeStr = record.get("close");
                        BigDecimal openBD = new BigDecimal(openStr);
                        openBD = openBD.subtract(ZERO).compareTo(NEAR_ZERO) <= 0 ? NEAR_ZERO : openBD; // nasty hack but we use data with max 0.0000 precision anyway
                        BigDecimal deltaPercentage = (new BigDecimal(closeStr).subtract(openBD)).divide(openBD, 4, HALF_EVEN).multiply(ONE_HUNDRED_PROC);

                        LocalDateTime recordDate = LocalDateTime.parse(dateStr, new DateTimeFormatterBuilder().appendPattern(timestampFormat).toFormatter());
                        LocalDateTime sinceDateTime = sinceDate.toDateTimeAtStartOfDay().toLocalDateTime();

                        System.out.printf("%s,\t%s,\t%s%n", recordDate, closeStr, deltaPercentage);

                        if (recordDate.isEqual(sinceDateTime) || recordDate.isAfter(sinceDateTime)) {
                            BigDecimal timestamp = new BigDecimal(recordDate.toDate().getTime());
                            BigDecimal close = new BigDecimal(closeStr);
                            BigDecimal delta = new BigDecimal(deltaPercentage.toString());

                            Map<String, BigDecimal> trainingBase = new HashMap<>();
                            trainingBase.put(KEY_TIMESTAMP, timestamp);
                            trainingBase.put(KEY_PRICE, close);
                            trainingBase.put(KEY_DELTA, delta);
                            datasetScrappedData.add(trainingBase);
                        }
                    });
                } while (readAmount > 0);
                consumer.close();

                enhanceSequenceElement(datasetScrappedData);

                int totalScrappedDays = datasetScrappedData.size();

                // Prepare output data
                for (int i = 0; i < totalScrappedDays; i++) {
                    int lookForwardDays = daysInPast + daysInFuture;
                    if (i < totalScrappedDays - lookForwardDays + 1) {
                        List<Map<String, BigDecimal>> valorSequence = new ArrayList<>();
                        for (int k = 0; k < lookForwardDays; k++) {
                            Map<String, BigDecimal> valorData = datasetScrappedData.get(i + k);
                            valorSequence.add(valorData);
                        }

                        // skip training vector with periods containing too few changes
                        if (hasTooFewSignificantChanges(valorSequence)) {
                            statsSkipped++;
                            continue;
                        }
                        // TODO: consider reducing vectors containing deltas beyond reasonable range

                        List<String> outputSequence = new ArrayList<>();

                        Map<String, BigDecimal> lastSequenceData = valorSequence.get(daysInPast - 1);
                        BigDecimal timestampMs = lastSequenceData.get(KEY_TIMESTAMP);
                        String lastSequenceTimestamp = timestampMs.toString();

                        outputSequence.add(lastSequenceTimestamp); //0
                        outputSequence.add(dataset); //1

                        // ----------------------- add features to sequence -----------------------
                        Map<String, BigDecimal> pastDay = null;
                        for (int x = 0; x < daysInPast; x++) { //2..retroDays
                            pastDay = valorSequence.get(x);
                            enhanceOutputSequence(outputSequence, pastDay);
                        }
                        @SuppressWarnings("ConstantConditions")
                        BigDecimal sequenceLastFeatureClose = pastDay.get(KEY_PRICE);

                        // ----------------------- add labels to sequence --------------------------
                        List<Map<String, BigDecimal>> futureDays = new ArrayList<>(); //days in future needed for labels calculation
                        for (int y = daysInPast; y < daysInPast + this.daysInFuture; y++) {
                            Map<String, BigDecimal> futureDay = valorSequence.get(y);
                            futureDays.add(futureDay);
//                                LOG.info("data[" + y + "]=future: " + futureData);
                        }

                        boolean gotExpectedIncrease = calculateOnlyForSpecificDay ?
                                calculateLblConditionOnSpecDay(assumedPercentageIncrease, sequenceLastFeatureClose, futureDays, this.daysInFuture) :
                                calculateLblConditionInAnyDay(assumedPercentageIncrease, sequenceLastFeatureClose, futureDays, this.daysInFuture);
                        if (gotExpectedIncrease) {
                            outputSequence.add("1");
                            outputSequence.add("0");
                            increaseTotal++;
                        } else {
                            // this category class is redundant but kept for convenience of later enumerations
                            outputSequence.add("0");
                            outputSequence.add("1");
                            noIncreaseTotal++;
                        }

                        try {
                            outputPrinter.printRecord(outputSequence);
                        } catch (IOException e) {
                            LOG.error("IOException - issue with writing to CSV: " + e.getMessage());
                        }

                        //noinspection ConstantConditions
                        if (printStats) {
                            calculateIncreaseFreq(statsForIncrease, sequenceLastFeatureClose, futureDays);
                        }
                        statsTotal++;
                    } else {
                        break; // not enough data left to continue - pick up next valor
                    }
                }

                // Print out some statistics
                LOG.info("Amount of records with increase achieved: " + increaseTotal);
                LOG.info("Amount of records with too little increase: " + noIncreaseTotal);
                LOG.info("Total training data records (remained): " + statsTotal);
                LOG.info("Skipped (insignificant) data records: " + statsSkipped);
                for (Map.Entry<String, BigDecimal> entry : statsForIncrease.entrySet()) {
                    String key = entry.getKey();
                    BigDecimal value = entry.getValue();
                    LOG.info(String.format("%s, %s, %s %n", key, value, value.doubleValue() / statsTotal));
                }
            }

            outputFileWriter.close();
        } catch (Exception e) {
            BetlejemUtils.logError(e);
        }

        return datasetScrappedData;
    }

    /**
     * Balances already prepared training data files to get similar amount of samples for each class in labels.
     * Rewrites data to a separate *_pp.csv file.
     *
     * @param basePath base path for data folders
     */
    private void trainingDataPostProcessing(String basePath) {
        List<Long> selectedRecordsOrderedByTimestamp = new LinkedList<>();

        String trainingDataPath = String.format("%s%s%s", pathDataTraining(basePath), File.separator, getTrainingDataSequencesFilename(assumedPercentageIncrease));
        try (Reader trainingDataFileReader = new FileReader(trainingDataPath)) {
            CSVParser recordsIterator = CSVFormat.DEFAULT.parse(trainingDataFileReader);

            List<Quartet<Long, String, Long, String>> trainingDataRecords = new LinkedList<>();
            recordsIterator.forEach(record -> {
                Long timestamp = Long.parseLong(record.get(0));
                String name = record.get(1);

                Long csvRecordNumber = record.getRecordNumber();
                String category = record.get(record.size() - 2) + ":" + record.get(record.size() - 1);
                trainingDataRecords.add(Quartet.with(timestamp, name, csvRecordNumber, category));
            });

            Map<String, List<Quartet<Long, String, Long, String>>> recordsInClass = new LinkedHashMap<>();
            trainingDataRecords.forEach(record -> {
                String category = record.getValue3();
                List<Quartet<Long, String, Long, String>> ric = recordsInClass.computeIfAbsent(category, k -> new ArrayList<>());
                ric.add(record);
            });

            List<Quartet<Long, String, Long, String>> interesting = recordsInClass.get("1:0");
            List<Quartet<Long, String, Long, String>> notInteresting = recordsInClass.get("0:1");

            if (interesting == null) {
                LOG.error("No valuable training data found in: " + trainingDataPath);
                return;
            }
            trainingDataRecords.clear();
            recordsInClass.clear();

            int interestingAmount = interesting.size();
            int notInterestingAmount = notInteresting.size();
            LOG.info("Interesting amount: " + interestingAmount);
            LOG.info("Not interesting amount: " + notInterestingAmount);

            if (interestingAmount < notInterestingAmount) {
                // pick first random notInteresting up to limit, drop the rest
                Collections.shuffle(notInteresting);
                notInteresting = notInteresting.stream()
                        .limit(interestingAmount)
                        .collect(Collectors.toList());
            } else {
                //  pick first random interesting up to limit, drop the rest
                Collections.shuffle(interesting);
                interesting = interesting.stream()
                        .limit(notInterestingAmount)
                        .collect(Collectors.toList());
            }

            LinkedList<Quartet<Long, String, Long, String>> selectedRecords = new LinkedList<>();
            selectedRecords.addAll(interesting);
            selectedRecords.addAll(notInteresting);
            LOG.info("Selected amount: " + selectedRecords.size());
            interesting.clear();
            notInteresting.clear();

            LOG.info("Applying order by timestamp, then name to selected records...");
            selectedRecordsOrderedByTimestamp = selectedRecords.stream()
                    .sorted(Comparator.<Quartet<Long, String, Long, String>>
                            comparingLong(Quartet::getValue0)
                            .thenComparing(Quartet::getValue1))
                    .map(Quartet::getValue2)
                    .collect(Collectors.toList());
            LOG.info(">>> Retained amount: " + selectedRecordsOrderedByTimestamp.size());

            recordsIterator.close();
        } catch (Exception e) {
            BetlejemUtils.logError(e);
        }

        rewriteTrainingData(basePath, selectedRecordsOrderedByTimestamp, trainingDataPath);
    }

    private void rewriteTrainingData(String basePath, List<Long> selectedRecordsInFinalOrder, String trainingDataPath) {
        // Reopen input and rewrite only selected data
        try {
            ArrayList<Long> sortedRecordNumbers = new ArrayList<>(selectedRecordsInFinalOrder);
            sortedRecordNumbers.sort(Comparator.naturalOrder());

            String outputPath = String.format("%s%s%s", pathDataTraining(basePath), File.separator, getTrainingDataSequencesFilenamePp(assumedPercentageIncrease));
            LOG.info(String.format("Generating: %s%n", outputPath));
            FileWriter outputFileWriter = new FileWriter(outputPath, false);
            try (PrintWriter outputPrinter = new PrintWriter(outputFileWriter)) {
                // re-load file content as string lines
                LOG.info("Reloading training data.");
                Map<Long, String> trainingDataFullContent = new LinkedHashMap<>();
                try (Reader trainingDataFileReader = new FileReader(trainingDataPath)) {
                    try (BufferedReader bufferedReader = new BufferedReader(trainingDataFileReader)) {
                        AtomicReference<Long> number = new AtomicReference<>(1L);
                        bufferedReader.lines().forEach(line -> {
                            Long recordNumber = number.get();
                            int index = sortedRecordNumbers.indexOf(recordNumber);
                            if (index > -1) {
                                trainingDataFullContent.put(recordNumber, line);
                                sortedRecordNumbers.remove(index);
                            }
                            number.getAndSet(recordNumber + 1);
                        });
                    }
                } catch (Exception e) {
                    BetlejemUtils.logError(e);
                }

                // apply data generalization, normalization and scaling here
                LOG.info("Process values and rewrite to separate output file.");
                for (Long recordNumber : selectedRecordsInFinalOrder) {
                    String recordString = trainingDataFullContent.get(recordNumber);
                    outputPrinter.println(recordString);
                }
            }
        } catch (Exception e) {
            BetlejemUtils.logError(e);
        }
    }

    private void enhanceSequenceElement(List<Map<String, BigDecimal>> valorScrappedData) {
        double[] data = valorScrappedData.stream()
                .mapToDouble(d -> d.get(KEY_DELTA).doubleValue())
                .toArray();

        if (daysInPast == F_DATA_RETRO_DAYS_192) { // TODO: just temporary solution for backward compatibility
            addBBands(valorScrappedData, 512, data);
            addKama(valorScrappedData, 1024, data, KEY_ENH_EMA_1024);
        } else {
            addBBands(valorScrappedData, 64, data);
            addKama(valorScrappedData, 128, data, KEY_ENH_EMA_128);
            addKama(valorScrappedData, 256, data, KEY_ENH_EMA_256);
        }
    }

    private void enhanceOutputSequence(List<String> outputSequence, Map<String, BigDecimal> pastData) {
        outputSequence.add(pastData.get(KEY_DELTA).toString());

        outputSequence.add(Optional.ofNullable(pastData.get(KEY_ENH_BB_UP)).orElse(ZERO).toString());
        outputSequence.add(Optional.ofNullable(pastData.get(KEY_ENH_BB_MID)).orElse(ZERO).toString());
        outputSequence.add(Optional.ofNullable(pastData.get(KEY_ENH_BB_LOW)).orElse(ZERO).toString());
        outputSequence.add(Optional.ofNullable(pastData.get(KEY_ENH_EMA_128)).orElse(ZERO).toString());
        outputSequence.add(Optional.ofNullable(pastData.get(KEY_ENH_EMA_256)).orElse(ZERO).toString());
    }

    /**
     * Checks distribution of labels in particular training data slice.
     * NOTE: requires manual adjustments - assumes 3 types of trainings regarding data used for accuracy validation:
     * <p>
     * examples of possible approaches:
     * - 20% samples from beginning
     * - 10% samples from beginning + 10% samples from ending
     * - 20% samples from ending
     *
     * @param dataPath path to base dir structure
     */
    private void trainingDataAnalysis(String dataPath) {
        String filename = String.format("%s%s%s", pathDataTraining(dataPath), File.separator, getTrainingDataSequencesFilenamePp(assumedPercentageIncrease));
        try (Reader finalTrainingDataFileReader = new FileReader(filename)) {
            Iterable<CSVRecord> trainingDataRecords = CSVFormat.DEFAULT.parse(finalTrainingDataFileReader);
            Map<Long, String> recordsRegistry = new LinkedHashMap<>();
            Function<CSVRecord, String> keyGen = record -> record.get(record.size() - 2) + ":" + record.get(record.size() - 1);
            for (CSVRecord record : trainingDataRecords) {
                String classStr = keyGen.apply(record);
                recordsRegistry.put(record.getRecordNumber(), classStr);
            }

            int totalAmount = recordsRegistry.size();
            Map<String, List<Long>> trainingRecordsInClass = new LinkedHashMap<>();
            int i = 0;
            int t = 0;
            double testDataSplit = 0.2;
            double lowBoundary = (testDataSplit / 2) * totalAmount;
            double highBoundary = (1. - (testDataSplit / 2)) * totalAmount;
            for (Long recordNumber : recordsRegistry.keySet()) {
                if (i > lowBoundary && i < highBoundary) {
                    List<Long> ric = trainingRecordsInClass.computeIfAbsent(recordsRegistry.get(recordNumber), k -> new ArrayList<>());
                    ric.add(recordNumber);
                    t++;
                }

                i++;
            }

            LOG.info("Total data amount: " + i);
            LOG.info(">>> Total amount for training: " + t);
            List<Long> class1 = trainingRecordsInClass.get("1:0");
            List<Long> class2 = trainingRecordsInClass.get("0:1");

            if (class1 == null || class2 == null) {
                return;
            }
            LOG.info("1:0 in training data - " + class1.size() + " = " + (BigDecimal.valueOf(class1.size()).divide(BigDecimal.valueOf(t), 3, HALF_EVEN).toString()));
            LOG.info("0:1 in training data - " + class2.size() + " = " + (BigDecimal.valueOf(class2.size()).divide(BigDecimal.valueOf(t), 3, HALF_EVEN).toString()));
        } catch (Exception e) {
            BetlejemUtils.logError(e);
        }
    }

    static BigDecimal normalize(BigDecimal deltaBD, BigDecimal[] boundaries) {
        BigDecimal zero = ZERO;
        for (int i = 0; i < boundaries.length; i++) {
            if (deltaBD.compareTo(zero) < 0 && deltaBD.compareTo(boundaries[i]) < 0) {
                return new BigDecimal(i + 1);
            } else if (deltaBD.compareTo(zero) == 0 && deltaBD.compareTo(boundaries[i]) == 0) {
                return new BigDecimal(i + 2);
            } else if (deltaBD.compareTo(zero) > 0 && deltaBD.compareTo(boundaries[i]) < 0) {
                return new BigDecimal(i + 2);
            }
        }
        throw new RuntimeException("Normalization error for: " + deltaBD);
    }

    private void calculateGroupBoundaries(List<Map.Entry<BigDecimal, Integer>> range, boolean positive, int groupAmnt) {
        int total = range.stream().map(Map.Entry::getValue).reduce(0, Integer::sum);
        int groupSize = total / groupAmnt;
        int subAmnt = 0;
        BigDecimal latest = null;
        Map<BigDecimal, Integer> rangePrintouts = new LinkedHashMap<>();
        for (Map.Entry<BigDecimal, Integer> entry : range) {
            BigDecimal delta = entry.getKey();
            Integer deltaAmnt = entry.getValue();
            if (positive) {
                if (subAmnt > groupSize + deltaAmnt) {
                    rangePrintouts.put(delta, subAmnt);
                    subAmnt = 0;
                }
                subAmnt += deltaAmnt;
            } else {
                subAmnt += deltaAmnt;
                if (subAmnt > groupSize) {
                    rangePrintouts.put(delta, subAmnt);
                    subAmnt = 0;
                }
            }
            latest = delta;
        }

        if (latest != null) {
            rangePrintouts.put(latest, subAmnt);
        }

        if (positive) { // closure for range group
            rangePrintouts.put(BigDecimal.valueOf(MAX_VALUE), -1);
        } else {
            rangePrintouts.put(ZERO, -1);
        }

        rangePrintouts.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(e -> {
                    String deltaAmnt = String.valueOf(e.getValue());
                    String rangePrintout = String.format("new BigDecimal(\"%s\"),", e.getKey());
                    System.out.print(String.format("%s : %s%n", Strings.padStart(rangePrintout, 34, ' '), deltaAmnt));
                });
    }

    private void calculateIncreaseFreq(Map<String, BigDecimal> statsForIncrease, BigDecimal lastFeatureOpen, List<Map<String, BigDecimal>> lData) {
        int maxExpectedPercentageIncrease = 10;
        for (int nextDays = 0; nextDays < lData.size(); nextDays++) {
            for (int hypotheticalIncrease = 1; hypotheticalIncrease <= maxExpectedPercentageIncrease; hypotheticalIncrease++) {
                BigDecimal maxCloseNextDays = lData.stream()
                        .limit(nextDays)
                        .map(l -> l.get(KEY_PRICE))
                        .max(Comparator.naturalOrder())
                        .orElse(BigDecimal.valueOf(-1));

                String key = String.format("%d, %d", nextDays + 1, hypotheticalIncrease);
                if (maxCloseNextDays.subtract(lastFeatureOpen).compareTo(BigDecimal.valueOf(hypotheticalIncrease)) > 0) { // delta > expectedPercentageIncrease during next_days
                    statsForIncrease.merge(key, ONE, BigDecimal::add);
                }
            }
        }
    }

    private boolean hasTooFewSignificantChanges(List<Map<String, BigDecimal>> valorSequence) {
        // remove too-many-zeros-vectors
        int numOfZeroDelta = valorSequence.stream()
                .filter(valor -> ZERO.compareTo(valor.get(KEY_DELTA)) == 0)
                .collect(Collectors.toList())
                .size();
        return numOfZeroDelta > MAX_PERCENTAGE_ZERO_DELTAS_IN_FEATURES * daysInPast;
    }

    @SuppressWarnings("SameParameterValue")
    public static void addKama(List<Map<String, BigDecimal>> valorScrappedData, int movingPeriod, double[] data, String emaKey) {
        double[] output = new double[data.length];
        MInteger begin = new MInteger();
        MInteger length = new MInteger();
        RetCode retCode = new Core().movingAverage(0, data.length - 1, data, movingPeriod, MAType.Kama, begin, length, output);

        if (retCode == RetCode.Success) {
            for (int i = 0; i < data.length; i++) {
                if (i >= begin.value) {
                    BigDecimal emaValue = new BigDecimal(output[i - begin.value]).setScale(4, HALF_EVEN);
                    valorScrappedData.get(i).put(emaKey, emaValue);
                }
            }
        } else {
            throw new RuntimeException("Could not enhance training data: " + retCode.toString());
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static void addBBands(List<Map<String, BigDecimal>> valorScrappedData, int movingPeriod, double[] data) {
        double[] up = new double[data.length];
        double[] mid = new double[data.length];
        double[] low = new double[data.length];

        MInteger begin = new MInteger();
        MInteger outNBElem = new MInteger();
        RetCode retCode = new Core().bbands(0, data.length - 1, data, movingPeriod, 1., 1., MAType.Ema, begin, outNBElem, up, mid, low);

        if (retCode == RetCode.Success) {
            for (int i = 0; i < data.length; i++) {
                if (i >= begin.value) {
                    BigDecimal upBand = new BigDecimal(up[i - begin.value]).setScale(4, HALF_EVEN);
                    BigDecimal midBand = new BigDecimal(mid[i - begin.value]).setScale(4, HALF_EVEN);
                    BigDecimal lowBand = new BigDecimal(low[i - begin.value]).setScale(4, HALF_EVEN);

                    valorScrappedData.get(i).put(KEY_ENH_BB_UP, upBand);
                    valorScrappedData.get(i).put(KEY_ENH_BB_MID, midBand);
                    valorScrappedData.get(i).put(KEY_ENH_BB_LOW, lowBand);
                }
            }
        } else {
            throw new RuntimeException("Could not enhance training data: " + retCode.toString());
        }
    }

    public static String pathDataTraining(String basePath) {
        return String.format("%s%straining", basePath, File.separator);
    }

}
