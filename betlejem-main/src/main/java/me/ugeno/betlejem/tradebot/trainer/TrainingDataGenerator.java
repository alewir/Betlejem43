package me.ugeno.betlejem.tradebot.trainer;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.tictactec.ta.lib.Core;
import com.tictactec.ta.lib.MAType;
import com.tictactec.ta.lib.MInteger;
import com.tictactec.ta.lib.RetCode;
import me.ugeno.betlejem.common.WindowsCommandExecutor;
import me.ugeno.betlejem.common.utils.BetlejemUtils;
import me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.javatuples.Quartet;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.xstore.api.message.codes.PERIOD_CODE;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Integer.MAX_VALUE;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_EVEN;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.CSV_EXTENSION;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.GPW_URL;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.NEAR_ZERO;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.ONE_HUNDRED_PROC;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.TXT_EXTENSION;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.gpwMapping;

/**
 * Created by alwi on 10/02/2021.
 * All rights reserved.
 */
@SuppressWarnings({"Duplicates", "WeakerAccess", "SameParameterValue"})
public
class TrainingDataGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(TrainingDataGenerator.class);

    public static final String INIT_SCRAP_DATE = "2018-01-01";

    public static final String BETLEJEM_NEURAL_NETWORKS_SCRIPTS_PATH = "betlejem-neural-networks/src/revived/";

    private static final double MAX_PERCENTAGE_ZERO_DELTAS_IN_FEATURES = 0.75;

    private static final int IDX_GPW_DATE = 0;
    private static final int IDX_GPW_CLOSE = 3;
    private static final int IDX_GPW_DELTA = 4;

    private static final int IDX_STOOQ_DATE = 2;
    private static final int IDX_STOOQ_TIME = 3;
    private static final int IDX_STOOQ_OPEN = 4;
    private static final int IDX_STOOQ_CLOSE = 7;

    private static final String KEY_TIMESTAMP = "DATE";
    private static final String KEY_CLOSE = "CLOSE";
    private static final String KEY_DELTA = "DELTA";

    private static final String KEY_ENH_BB_UP = "BB_UP";
    private static final String KEY_ENH_BB_MID = "BB_MID";
    private static final String KEY_ENH_BB_LOW = "BB_LOW";
    private static final String KEY_ENH_EMA_128 = "EMA_128";
    private static final String KEY_ENH_EMA_256 = "EMA_256";
    private static final String KEY_ENH_EMA_1024 = "EMA_1024";

    private static final DateTimeFormatter GPW_PAGE_HISTORY_INPUT_FORMAT = DateTimeFormat.forPattern("dd-MM-yyyy");
    private static final String STOOQ_DATE_FORMAT = "yyyyMMdd";
    private static final String STOOQ_TIME_FORMAT = "HHmmss";

    @SuppressWarnings("unused")
    private static WebDriver DRV;

    static {
        System.setProperty("webdriver.chrome.driver", "webdriver\\chromedriver.exe");
    }

    private String specificValorName;
    private int pastIntervals; // how many days in past to consider as input (training/prediction)?
    private int assumedPercentageIncrease = 5; // how much % increase to expect?
    private int futureIntervals = 3; // within how many days to calculate future increase?
    private boolean calculateOnlyForSpecificDay = true; // expect given increase only in specific future day or within all days up to it?

    public TrainingDataGenerator(String specificValorName, int pastIntervals) {
        this.specificValorName = specificValorName;
        this.pastIntervals = pastIntervals;
    }

    private static boolean calculateLblConditionOnSpecDay(int assumedPercentageIncrease, BigDecimal lastFeatureClose, List<Map<String, BigDecimal>> lData, int futureDays) {
        // price increase > expectedPercentageIncrease during on specific day
        BigDecimal closeAtNthDay = lData.stream()
                .skip(futureDays - 1)
                .map(l -> l.get(KEY_CLOSE))
                .findFirst()
                .orElse(BigDecimal.valueOf(-1));

        boolean noDecrease = increasing(lastFeatureClose, lData, futureDays);

        return noDecrease && checkPriceDiff(assumedPercentageIncrease, lastFeatureClose, closeAtNthDay);
    }

    private static boolean calculateLblConditionInAnyDay(int assumedPercentageIncrease, BigDecimal lastFeatureClose, List<Map<String, BigDecimal>> lData, int futureDays) {
        // price increase > expectedPercentageIncrease during any of next lData days
        BigDecimal maxCloseNextDays = lData.stream()
                .limit(futureDays)
                .map(l -> l.get(KEY_CLOSE))
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.valueOf(-1));

        boolean noDecrease = increasing(lastFeatureClose, lData, futureDays);

        return noDecrease && checkPriceDiff(assumedPercentageIncrease, lastFeatureClose, maxCloseNextDays);
    }

    private static boolean increasing(BigDecimal lastFeatureClose, List<Map<String, BigDecimal>> lData, int futureDays) {
        return lData.stream()
                .limit(futureDays)
                .map(l -> l.get(KEY_CLOSE).subtract(lastFeatureClose))
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
        return String.format("train_%s_%din%d_%s.csv", LocalDate.now().toString(), assumedPercentageIncrease, futureIntervals, specificValorName);
    }

    public void runTfDataEvaluationGpw() throws IOException, InterruptedException {
        WindowsCommandExecutor wce = new WindowsCommandExecutor();
        File workingDir = new File(BETLEJEM_NEURAL_NETWORKS_SCRIPTS_PATH);
        wce.executeInWindowsCmd("python predict_rnn_gpw_d1.py", workingDir);
    }

    public String runTfDataEvaluationUs(final String predictionScriptName) throws IOException, InterruptedException {
        WindowsCommandExecutor wce = new WindowsCommandExecutor();
        File workingDir = new File(BETLEJEM_NEURAL_NETWORKS_SCRIPTS_PATH);
        return wce.executeInWindowsCmd("python " + predictionScriptName, workingDir);
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
     * @param dataPath   directory with training data
     * @param normalize  disable it for the first run when trying to establish deltas distribution,
     *                   then enable to validate effects (printout) of applied normalization.
     * @param boundaries @Deprecated was used for normalization
     */
    void analyzeData(String dataPath, boolean normalize, BigDecimal[] boundaries) {
        String scrappedValors = String.format("%s%shistorical", dataPath, File.separator);
        List<String> activeValors = fetchAllScrappedValors(scrappedValors, String.format(".%s", CSV_EXTENSION));
        Map<BigDecimal, Integer> stats = new HashMap<>();
        activeValors.forEach(v -> {
            try (Reader in = new FileReader(String.format("%s/%s.%s", scrappedValors, v, CSV_EXTENSION))) {
                Iterable<CSVRecord> records = CSVFormat.DEFAULT.parse(in);
                for (CSVRecord record : records) {
                    String delta = record.get(IDX_GPW_DELTA);

                    BigDecimal deltaBD = new BigDecimal(delta);
                    deltaBD = deltaBD.setScale(2, HALF_EVEN);
                    if (normalize) {
                        deltaBD = normalize(deltaBD, boundaries);
                    }
                    stats.merge(deltaBD, 1, (a, b) -> a + b);
                }

            } catch (Exception e) {
                BetlejemUtils.logError(e);
            }
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

    @SuppressWarnings("SameParameterValue")
    void generateMultipleDatasets(int minIncrease, int maxIncrease, int minDays, int maxDays, boolean calculateOnlyForSpecificDay, ArrayList<String> specificValors, List<String> activeValorsToConsider, String dataPath, String inputTimestampFormat, String valorSuffix) {
        for (String valorName : specificValors) {
            for (int days = minDays; days <= maxDays; days++) {
                for (int inc = minIncrease; inc <= maxIncrease; inc++) {
                    prepareSingleDataset(inc, days, calculateOnlyForSpecificDay, valorName, activeValorsToConsider, dataPath, inputTimestampFormat, valorSuffix);
                    LOG.info("=====================================================");
                }
            }
        }
    }

    @SuppressWarnings({"SameParameterValue"})
    void prepareSingleDataset(int expectedIncrease, int withinDays, boolean calculateOnlyForSpecificDay, String specificValorName, List<String> activeValorsToConsider, String dataPath, String inputTimestampFormat, String valorSuffix) {
        this.assumedPercentageIncrease = expectedIncrease;
        this.futureIntervals = withinDays; // within how many days to calculate future increase?
        this.calculateOnlyForSpecificDay = calculateOnlyForSpecificDay;
        this.specificValorName = specificValorName;

        prepareTrainingData(activeValorsToConsider, dataPath, inputTimestampFormat, valorSuffix);
        trainingDataPostProcessing(dataPath);
        trainingDataAnalysis(dataPath);
    }

    private String getTrainingDataSequencesFilenamePp(int assumedPercentageIncrease) {
        return String.format("train_%s_pp_x%d_%din%d_%s.csv", LocalDate.now().toString(), this.pastIntervals, assumedPercentageIncrease, futureIntervals, specificValorName);
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
     * @param activeValors    all that should be used for training (
     * @param dataPath        where to find historical data
     * @param timestampFormat format of date in training pp files
     * @param valorSuffix
     */
    private void prepareTrainingData(List<String> activeValors, String dataPath, String timestampFormat, String valorSuffix) {
        DateTimeFormatter formatter = new DateTimeFormatterBuilder().appendPattern(BetlejemXtbConstants.TRAINING_DATA_DATE_FORMAT).toFormatter();
        LocalDate sinceDate = LocalDate.parse(INIT_SCRAP_DATE, formatter);

        if (!this.specificValorName.equals(BetlejemXtbConstants.VALOR_NAME_ALL)) {
            activeValors = activeValors.stream()
                    .filter(v -> v.equals(this.specificValorName))
                    .collect(Collectors.toList());
        }

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

                for (String valorName : activeValors) {
                    List<Map<String, BigDecimal>> dataset = new ArrayList<>();

                    valorName += valorSuffix;

                    String valorScrappedDataFilename = String.format("%s/%s.csv", BetlejemUtils.pathDataHistorical(dataPath), valorName);
                    File valorFile = new File(valorScrappedDataFilename);
                    if (!valorFile.exists()) {
                        LOG.error("File with ticker data not found at: " + valorFile.getAbsolutePath());
                        continue;
                    }

                    try (Reader valorFileReader = new FileReader(valorFile)) {
                        CSVParser valorRecords = CSVFormat.DEFAULT.parse(valorFileReader);
                        for (CSVRecord record : valorRecords) {
                            String dateStr = record.get(IDX_GPW_DATE);
                            String closeStr = record.get(IDX_GPW_CLOSE);
                            String deltaStr = record.get(IDX_GPW_DELTA);

                            LocalDateTime recordDate = LocalDateTime.parse(dateStr, new DateTimeFormatterBuilder().appendPattern(timestampFormat).toFormatter());
                            LocalDateTime sinceDateTime = sinceDate.toDateTimeAtStartOfDay().toLocalDateTime();
                            if (recordDate.isEqual(sinceDateTime) || recordDate.isAfter(sinceDateTime)) {
                                BigDecimal timestamp = new BigDecimal(recordDate.toDate().getTime());
                                BigDecimal close = new BigDecimal(closeStr);
                                BigDecimal delta = new BigDecimal(deltaStr);

                                Map<String, BigDecimal> trainingBase = new HashMap<>();
                                trainingBase.put(KEY_TIMESTAMP, timestamp);
                                trainingBase.put(KEY_CLOSE, close);
                                trainingBase.put(KEY_DELTA, delta);
                                dataset.add(trainingBase);
                            }
                        }

                        valorRecords.close();
                    } catch (Exception e) {
                        BetlejemUtils.logError(e);
                    }

                    if (dataset.size() == 0) { // skip active valors for which there was no historical data
                        continue;
                    }

                    enhanceDataset(dataset);

                    int totalScrappedIntervals = dataset.size();
//                    LOG.info(String.format("%s has data for : %d days.%n", valorName, valorDaysTotal);

                    // Prepare output data
                    for (int i = 0; i < totalScrappedIntervals; i++) {
                        int lookForwardIntervals = pastIntervals + futureIntervals;
                        if (i < totalScrappedIntervals - lookForwardIntervals + 1) {
                            List<Map<String, BigDecimal>> valorSequence = new ArrayList<>();
                            for (int k = 0; k < lookForwardIntervals; k++) {
                                Map<String, BigDecimal> valorData = dataset.get(i + k);
                                valorSequence.add(valorData);
                            }

                            // skip training vector with periods containing too few changes
                            if (hasTooFewSignificantChanges(valorSequence)) {
                                statsSkipped++;
                                continue;
                            }
                            // TODO: consider reducing vectors containing deltas beyond reasonable range

                            List<String> outputSequence = new ArrayList<>();

                            Map<String, BigDecimal> lastSequenceData = valorSequence.get(pastIntervals - 1);
                            BigDecimal timestampMs = lastSequenceData.get(KEY_TIMESTAMP);
                            String lastSequenceTimestamp = timestampMs.toString();

                            outputSequence.add(lastSequenceTimestamp); //0
                            outputSequence.add(valorName); //1

                            // ----------------------- add features to sequence -----------------------
                            Map<String, BigDecimal> pastDay = null;
                            for (int x = 0; x < pastIntervals; x++) { //2..retroDays
                                pastDay = valorSequence.get(x);
                                appendEnhancedSequenceEntry(outputSequence, pastDay);
                            }
                            @SuppressWarnings("ConstantConditions")
                            BigDecimal sequenceLastFeatureClose = pastDay.get(KEY_CLOSE);

                            // ----------------------- add labels to sequence --------------------------
                            List<Map<String, BigDecimal>> futureDays = new ArrayList<>(); //days in future needed for labels calculation
                            for (int y = pastIntervals; y < pastIntervals + this.futureIntervals; y++) {
                                Map<String, BigDecimal> futureDay = valorSequence.get(y);
                                futureDays.add(futureDay);
//                                LOG.info("data[" + y + "]=future: " + futureData);
                            }

                            boolean gotExpectedIncrease = calculateOnlyForSpecificDay ?
                                    calculateLblConditionOnSpecDay(assumedPercentageIncrease, sequenceLastFeatureClose, futureDays, this.futureIntervals) :
                                    calculateLblConditionInAnyDay(assumedPercentageIncrease, sequenceLastFeatureClose, futureDays, this.futureIntervals);
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
        } catch (Exception e) {
            BetlejemUtils.logError(e);
        }
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

    public void prepareDataForEvaluation(String dataPath, List<String> activeValorsToConsider, String dateFormat, String valorNameSuffix) {
        LOG.info("Entering prepareDataForEvaluation....");

        int n = 0;
        String outputPath = String.format("%s%s%s", pathDataEvaluation(dataPath), File.separator, String.format("eval_%s.%s", LocalDate.now().toString(), CSV_EXTENSION));
        try {
            FileWriter outputFileWriter = new FileWriter(outputPath, false);
            try (CSVPrinter outputPrinter = new CSVPrinter(outputFileWriter, CSVFormat.DEFAULT)) {
                for (String valorName : activeValorsToConsider) {
                    LocalDate sinceDate = LocalDate.now().minusDays(600); // we need to calculate data enhancements which currently count in 512 past days
                    List<Map<String, BigDecimal>> valorScrappedData = new ArrayList<>();

                    String valorScrappedDataFilename = String.format("%s/%s%s.csv", BetlejemUtils.pathDataHistorical(dataPath), valorName, valorNameSuffix);
                    File valorFile = new File(valorScrappedDataFilename);
                    if (!valorFile.exists()) {
                        System.err.print(String.format("%s not found ", valorFile));
                        continue;
                    }

                    try (Reader valorFileReader = new FileReader(valorFile)) {
                        CSVParser valorRecords = CSVFormat.DEFAULT.parse(valorFileReader);
                        LocalDateTime latestRecordDate = null;
                        for (CSVRecord record : valorRecords) {
                            String dateStr = record.get(IDX_GPW_DATE);
                            String closeStr = record.get(IDX_GPW_CLOSE);
                            String deltaStr = record.get(IDX_GPW_DELTA);

                            DateTimeFormatter formatter = new DateTimeFormatterBuilder().appendPattern(dateFormat).toFormatter();
                            LocalDateTime recordDate = LocalDateTime.parse(dateStr, formatter);
                            LocalDateTime sinceDateTime = sinceDate.toDateTimeAtStartOfDay().toLocalDateTime();
                            if (recordDate.isEqual(sinceDateTime) || recordDate.isAfter(sinceDateTime)) {
                                BigDecimal timestamp = new BigDecimal(recordDate.toDate().getTime());
                                BigDecimal close = new BigDecimal(closeStr);
                                BigDecimal delta = new BigDecimal(deltaStr);

                                Map<String, BigDecimal> trainingBase = new HashMap<>();
                                trainingBase.put(KEY_TIMESTAMP, timestamp);
                                trainingBase.put(KEY_CLOSE, close);
                                trainingBase.put(KEY_DELTA, delta);
                                valorScrappedData.add(trainingBase);

                                latestRecordDate = recordDate;
                            }
                        }
                        valorRecords.close();

                        if (latestRecordDate != null && latestRecordDate.toLocalDate().isBefore(LocalDate.now().minusDays(7))) { // skip also anything that has stale latest records
                            continue;
                        }
                    } catch (Exception e) {
                        BetlejemUtils.logError(e);
                    }

                    if (valorScrappedData.size() == 0) { // skip active valors for which there was no historical data
                        continue;
                    }

                    enhanceDataset(valorScrappedData);

                    int valorDaysTotal = valorScrappedData.size();
                    LOG.info("{}-loaded-{}", valorName, valorDaysTotal);

                    // Prepare output data
                    for (int i = 0; i < valorDaysTotal; i++) {
                        int dataForwardAmnt = pastIntervals;
                        if (i < valorDaysTotal - dataForwardAmnt) {
                            if (i == valorDaysTotal - dataForwardAmnt - 1) {
                                List<Map<String, BigDecimal>> valorSequence = new ArrayList<>();
                                Map<String, BigDecimal> sequenceValor = null;
                                for (int k = 1; k <= dataForwardAmnt; k++) {
                                    sequenceValor = valorScrappedData.get(i + k);
                                    valorSequence.add(sequenceValor);
                                }
                                assert sequenceValor != null;
                                BigDecimal sequenceLastTimestampMs = sequenceValor.get(KEY_TIMESTAMP);
                                BigDecimal sequenceLastClose = sequenceValor.get(KEY_CLOSE);

                                // skip evaluation vector with periods containing too few changes
                                if (hasTooFewSignificantChanges(valorSequence)) {
                                    System.out.print("Too many zeros.");
                                    continue;
                                }

                                n++;

                                List<String> outputSequence = new ArrayList<>();

                                // add features to sequence
                                Map<String, BigDecimal> valorData;
                                for (int x = 0; x < pastIntervals; x++) {
                                    valorData = valorSequence.get(x);
                                    if (x == 0) { // first columns with some basic information
                                        outputSequence.add(LocalDateTime.fromDateFields(Date.from(Instant.ofEpochMilli(sequenceLastTimestampMs.longValue()))).toString());
                                        outputSequence.add(valorName);
                                        outputSequence.add(sequenceLastClose.toString());
                                    }

                                    // ... then sequence of data
                                    appendEnhancedSequenceEntry(outputSequence, valorData);
                                }

//                                LOG.info(outputSequence);
                                try {
                                    outputPrinter.printRecord(outputSequence);
                                } catch (Exception e) {
                                    BetlejemUtils.logError(e);
                                }
                            }
                        } else {
                            break; // not enough data left to continue - pick up next valor
                        }
                    }
                }
            }
        } catch (IOException e) {
            BetlejemUtils.logError(e);
        }

        System.out.printf("%nTotal valors with reasonable dataset: %d - %s%n%n", n, outputPath);
    }

    public static void enhanceDataset(List<Map<String, BigDecimal>> valorScrappedData) {
        double[] data = valorScrappedData.stream()
                .mapToDouble(d -> d.get(KEY_DELTA).doubleValue())
                .toArray();

        addBBands(valorScrappedData, 512, data);
        addKama(valorScrappedData, 1024, data, KEY_ENH_EMA_1024);
    }

    public static void appendEnhancedSequenceEntry(List<String> outputSequence, Map<String, BigDecimal> entry) {
        outputSequence.add(entry.get(KEY_DELTA).toString());

        outputSequence.add(Optional.ofNullable(entry.get(KEY_ENH_BB_UP)).orElse(ZERO).toString());
        outputSequence.add(Optional.ofNullable(entry.get(KEY_ENH_BB_MID)).orElse(ZERO).toString());
        outputSequence.add(Optional.ofNullable(entry.get(KEY_ENH_EMA_1024)).orElse(ZERO).toString());
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
                    LOG.info(String.format("%s : %s%n", Strings.padStart(rangePrintout, 34, ' '), deltaAmnt));
                });
    }

    private void calculateIncreaseFreq(Map<String, BigDecimal> statsForIncrease, BigDecimal lastFeatureOpen, List<Map<String, BigDecimal>> lData) {
        int maxExpectedPercentageIncrease = 10;
        for (int nextDays = 0; nextDays < lData.size(); nextDays++) {
            for (int hypotheticalIncrease = 1; hypotheticalIncrease <= maxExpectedPercentageIncrease; hypotheticalIncrease++) {
                BigDecimal maxCloseNextDays = lData.stream()
                        .limit(nextDays)
                        .map(l -> l.get(KEY_CLOSE))
                        .max(Comparator.naturalOrder())
                        .orElse(BigDecimal.valueOf(-1));

                String key = String.format("%d, %d", nextDays + 1, hypotheticalIncrease);
                if (maxCloseNextDays.subtract(lastFeatureOpen).compareTo(BigDecimal.valueOf(hypotheticalIncrease)) > 0) { // delta > expectedPercentageIncrease during next_days
                    statsForIncrease.merge(key, ONE, BigDecimal::add);
                }
            }
        }
    }

    public List<String> fetchActiveValorsGpwStooqMappings() {
        return Lists.newArrayList("01C,06N,08N,11B,1AT,2CP,3RG,4FM,4MB,4MS,7FT,7LV,AAS,AAT,ABE,ABK,ABS,ACA,ACG,ACK,ACP,ACR,ACT,ADV,ADX,AER,AFC,AFH,AGL,AGO,AGP,AGT,AIN,AIT,ALD,ALE,ALG,ALI,ALL,ALR,ALS,ALU,AMB,AMC,AML,ANR,ANRA,AOL,APA,APC,APE,APL,APN,APR,APS,APT,AQA,AQT,AQU,ARE,ARG,ARH,ARI,ARR,ART,ARX,ASA,ASB,ASE,ASM,ASP,ASR,AST,ATA,ATC,ATD,ATG,ATJ,ATL,ATO,ATP,ATR,ATS,ATT,AUG,AVE,AVT,AWB,AWM,AZC,B24,BAH,BBA,BBD,BBT,BCI,BCM,BDG,BDX,BDZ,BEP,BER,BFC,BFT,BGD,BHW,BHX,BIK,BIO,BIP,BKM,BLO,BLR,BLT,BLU,BMC,BML,BMX,BNP,BOS,BOW,BPC,BPN,BPX,BRA,BRG,BRH,BRO,BRS,BRU,BSA,BST,BTC,BTG,BTK,BTX,BVT,CAI,CAM,CAR,CBD,CCC,CCE,CCS,CDA,CDL,CDR,CDT,CEZ,CFG,CFI,CFS,CHP,CIE,CIG,CLC,CLD,CLE,CLN,CMC,CMI,CMP,CMR,CNG,CNT,COG,COR,CPA,CPD,CPG,CPL,CPS,CRB,CRC,CRJ,CRM,CRP,CRS,CSR,CTE,CTF,CTS,CWP,CZT,DADA,DAM,DAT,DBC,DBE,DCD,DCR,DDI,DEG,DEK,DEL,DEV,DGA,DIN,DKR,DLK,DNP,DNS,DOK,DOM,DPL,DRE,DRF,DRG,DTR,DTX,DUA,DVL,EAH,EAT,EBX,EC2,ECA,ECC,ECH,ECK,ECL,ECO,ECR,EDI,EDN,EEX,EFE,EFK,EGH,EHG,EKP,EKS,ELB,ELM,ELQ,ELT,ELZ,EMA,EMC,EMM,EMT,EMU,ENA,ENE,ENG,ENI,ENP,ENT,EON,EPR,ERB,ERG,ERH,ERS,ESK,EST,ETL,ETX,EUC,EUR,EXA,EXC,EXM,F51,FEE,FER,FFI,FGT,FHD,FIG,FIN,FIV,FKD,FLD,FLG,FMF,FMG,FON,FOR,FPO,FRO,FSG,FTE,FTH,FTN,FVE,GAL,GBK,GCN,GEM,GEN,GIF,GKI,GKS,GLC,GLG,GME,GNB,GNG,GNR,GOB,GOL,GOP,GOV,GPW,GRC,GRE,GRM,GRN,GTC,GTF,GTK,GTN,GTP,GTS,GTY,GX1,H4F,HBG,HDR,HEF,HEL,HLD,HMI,HMP,HOR,HPS,HRC,HRL,HRP,HRS,HRT,HUGE,I2D,IAG,IBC,IBS,ICA,ICD,ICE,IDG,IDH,IDM,IFA,IFC,IFI,IFM,IFR,IGN,IGS,IGT,IIA,IMC,IMG,IMP,IMS,INC,INF,ING,INK,INL,INM,INP,INS,INT,INV,INW,IOD,IPE,IPF,IPL,IPO,IRL,ISG,IST,ITB,ITL,ITM,IUS,IVE,IVO,IZB,IZO,IZS,JJB,JJO,JRH,JSW,JWA,JWC,JWW,K2H,K2P,KAN,KBD,KBJ,KBT,KCH,KCI,KDM,KER,KGH,KGL,KGN,KIN,KKH,KLN,KME,KMP,KOM,KOR,KPC,KPD,KPI,KPL,KRC,KRI,KRK,KRU,KSG,KSW,KTY,KVT,LAB,LAN,LBD,LBT,LBW,LCN,LEN,LET,LGT,LKD,LKS,LPP,LPS,LRK,LRQ,LSH,LSI,LTG,LTS,LTX,LUG,LUK,LUX,LVC,LWB,M4B,MAB,MAD,MAK,MAX,MBF,MBK,MBR,MBW,MCE,MCI,MCP,MCR,MDA,MDB,MDG,MDI,MDN,MDP,MEG,MEI,MER,MEX,MFD,MFO,MGA,MGC,MGS,MGT,MIL,MIR,MLB,MLG,MLK,MLP,MLS,MLT,MMA,MMC,MMD,MNC,MND,MNS,MNX,MOE,MOJ,MOL,MON,MOV,MPH,MPY,MRA,MRB,MRC,MRD,MRG,MRH,MRK,MRS,MSM,MSP,MSW,MSZ,MTN,MTR,MTS,MVP,MWT,MXC,MXP,MZA,NET,NEU,NFP,NGG,NNG,NOV,NRS,NST,NTS,NTT,NTU,NTW,NVA,NVG,NVT,NVV,NWA,NWG,NXB,NXG,OAT,OBL,ODL,OEX,OML,ONC,OPF,OPG,OPL,OPM,OPN,OPT,ORG,ORL,OTM,OTS,OUT,OVI,OVO,OXY,OZE,P24,P2B,P2C,PAS,PAT,PBB,PBF,PBG,PBT,PBX,PCE,PCF,PCFA,PCG,PCR,PCX,PDG,PDZ,PEM,PEN,PEO,PEP,PEX,PFD,PFG,PFM,PGE,PGM,PGN,PGO,PGV,PHN,PHR,PIG,PIT,PIW,PJP,PKN,PKO,PKP,PLE,PLG,PLI,PLM,PLW,PLX,PLY,PLZ,PMA,PMF,PMP,PND,PNT,PNW,POZ,PPS,PRD,PRF,PRI,PRL,PRM,PRO,PRS,PRT,PSH,PSM,PST,PSW,PTE,PTH,PTN,PTW,PUE,PUN,PUR,PURA,PWX,PXM,PYL,PZU,QNT,QON,QRS,QRT,QUB,R22,RAF,RBS,RBW,RCA,RCW,RDG,RDL,RDN,RDS,REG,REM,RES,REV,RFK,RHD,RLP,RMK,RNC,RNK,RON,ROV,RPC,RSP,RVU,RWL,S4E,SAN,SBE,SCP,SCS,SDG,SEK,SEL,SEN,SES,SEV,SFD,SFG,SFK,SFN,SFS,SGN,SGR,SHD,SHG,SIM,SIN,SKA,SKH,SKL,SKN,SKT,SLV,SLZ,SME,SMS,SMT,SNG,SNK,SNT,SNW,SNX,SOK,SOL,SON,SP1,SPH,SPK,SPL,SPR,SSK,STA,STD,STF,STP,STX,SUL,SUN,SUW,SVRS,SWD,SWG,SWK,SWT,SYM,SZR,T2P,TAR,TBL,TEN,THD,TIG,TIM,TLG,TLO,TLS,TLT,TLV,TLX,TME,TMP,TMR,TNX,TOA,TOR,TOS,TOW,TPE,TRI,TRK,TRN,TRR,TSG,TXF,TXM,TXN,TYP,U2K,UCG,UFC,ULG,ULM,UNI,UNL,UNT,URS,UTD,VAB,VAR,VCP,VDS,VEE,VEL,VER,VGO,VIA,VIN,VIV,VKT,VOT,VOX,VRB,VRG,VTI,VTL,VVD,WAS,WBY,WHH,WIK,WIS,WLT,WOD,WOJ,WPL,WRE,WRL,WSE,WTF,WTN,WWL,WXF,XPL,XTB,XTP,YAN,YOL,ZAP,ZEP,ZMT,ZRE,ZUE,ZUK,ZWC".split(","));
    }

    public static List<String> fetchActiveValorsGpwXtb() {
        return Lists.newArrayList("11B.PL,ABE.PL,ACG.PL,ACT.PL,AGO.PL,AGT.PL,ALL.PL,AWM.PL,ALC.PL,ALR.PL,ALI.PL,AML.PL,AMB.PL,AMC.PL,EAT.PL,APT.PL,APE.PL,ARH.PL,ATC.PL,ASB.PL,ABS.PL,ACP.PL,ASE.PL,AST.PL,1AT.PL,ATD.PL,ATG.PL,ATG.PL,ATR.PL,APR.PL,BAH.PL,BBD.PL,BFT.PL,BML.PL,BIO.PL,LWB.PL,BRS.PL,BOS.PL,BRA.PL,BDX.PL,SPL.PL,CPG.PL,CCC.PL,CDR.PL,CDL.PL,CIE.PL,CIG.PL,CLN.PL,COG.PL,CMR.PL,CMP.PL,CRM.PL,CPS.PL,DBC.PL,DEL.PL,DNP.PL,DOM.PL,ECH.PL,EEX.PL,ELB.PL,ELT.PL,ENA.PL,ENG.PL,ENI.PL,ASB.PL,APE.PL,EAH.PL,EUC.PL,EUR.PL,ETL.PL,FMF.PL,FMF.PL,FRO.PL,FTE.PL,GBK.PL,GNB.PL,GNB.PL,GLC.PL,GOB.PL,GPW.PL,GCN.PL,GRN.PL,ATT.PL,GTC.PL,BHW.PL,HRP.PL,HRS.PL,IMC.PL,IPL.PL,IMS.PL,INC.PL,ING.PL,INK.PL,IRL.PL,CAR.PL,INL.PL,IPE.PL,IZB.PL,JSW.PL,JWC.PL,KAN.PL,KER.PL,KTY.PL,KGH.PL,KPL.PL,KGN.PL,KST.PL,KRU.PL,KSW.PL,KVT.PL,DVL.PL,LEN.PL,LTX.PL,LBT.PL,LVC.PL,LKD.PL,LTS.PL,LPP.PL,LSI.PL,LBW.PL,MAB.PL,MAK.PL,MGT.PL,MVP.PL,MPH.PL,MXC.PL,MBK.PL,MCI.PL,MDI.PL,MDG.PL,MNC.PL,MRC.PL,MCR.PL,MEX.PL,MIL.PL,MRB.PL,MLG.PL,MBR.PL,MOL.PL,MON.PL,MSW.PL,MWT.PL,NNG.PL,NET.PL,NEU.PL,NWG.PL,NVT.PL,NTT.PL,ODL.PL,OEX.PL,OPN.PL,OPM.PL,OPL.PL,OBL.PL,PBG.PL,BKM.PL,PCX.PL,PCR.PL,PBX.PL,PEO.PL,PEP.PL,PGE.PL,PGN.PL,PSW.PL,PHN.PL,PKN.PL,PKO.PL,PKP.PL,PLW.PL,PLW.PL,PCE.PL,PXM.PL,PND.PL,PWX.PL,POZ.PL,PRF.PL,PDZ.PL,PRM.PL,PRT.PL,ZAP.PL,PZU.PL,QRS.PL,R22.PL,RFK.PL,RBW.PL,RNK.PL,RLP.PL,RPC.PL,NVV.PL,SNK.PL,SPL.PL,SNW.PL,SEK.PL,SEL.PL,SLV.PL,SEN.PL,SKH.PL,SKA.PL,STX.PL,STP.PL,STF.PL,STL.PL,SNX.PL,SGN.PL,SNT.PL,STX.PL,TAR.PL,TPE.PL,TSG.PL,ELT.PL,TOR.PL,TOW.PL,TOA.PL,TRK.PL,ULM.PL,UNI.PL,UNT.PL,URS.PL,VGO.PL,VIN.PL,VST.PL,VVD.PL,VOT.PL,VOX.PL,WWL.PL,WLT.PL,WPL.PL,WTN.PL,WSE.PL,XTB.PL,ZEP.PL,PUE.PL,ZWC.PL".split(","));
    }

    public List<String> fetchActiveValorsGpw() {
        return Lists.newArrayList("11BIT,ABPL,ACAUTOGAZ,ACTION,AGORA,AGROTON,AILLERON,AIRWAY,ALCHEMIA,ALIOR,ALTUSTFI,ALUMETAL,AMBRA,AMICA,AMREST,APATOR,APSENERGY,ARCHICOM,ARCTIC,ASBIS,ASSECOBS,ASSECOPOL,ASSECOSEE,ASTARTA,ATAL,ATENDE,ATM,ATMGRUPA,ATREM,AUTOPARTN,BAHOLDING,BBIDEV,BENEFIT,BIOMEDLUB,BIOTON,BOGDANKA,BORYSZEW,BOS,BRASTER,BUDIMEX,SANTANDER,CAPITAL,CCC,CDPROJEKT,CDRL,CIECH,CIGAMES,CLNPHARMA,COGNOR,COMARCH,COMP,CORMAY,CYFRPLSAT,DEBICA,DELKO,DINOPL,DOMDEV,ECHO,EKOEXPORT,ELBUDOWA,ELEKTROTI,ENEA,ENERGA,ENERGOINS,ENTER,ERG,ESOTIQ,EUCO,EUROCASH,EUROTEL,FAM,FAMUR,FERRO,FORTE,GETBACK,GETIN,GETINOBLE,GLCOSMED,GOBARTO,GPW,GROCLIN,GRODNO,GRUPAAZOTY,GTC,HANDLOWY,HARPER,HERKULES,IMCOMPANY,IMPEL,IMS,INC,INGBSK,INSTALKRK,INTERAOLT,INTERCARS,INTROL,IPOPEMA,IZOBLOK,JSW,JWCONSTR,KANIA,KERNEL,KETY,KGHM,KINOPOL,KOGENERA,KONSSTALI,KRUK,KRUSZWICA,KRVITAMIN,LCCORP,LENA,LENTEX,LIBET,LIVECHAT,LOKUM,LOTOS,LPP,LSISOFT,LUBAWA,MABION,MAKARONPL,MANGATA,MARVIPOL,MASTERPHA,MAXCOM,MBANK,MCI,MDIENERGIA,MEDICALG,MENNICA,MERCATOR,MERCOR,MEXPOLSKA,MILLENNIUM,MIRBUD,MLPGROUP,MOBRUK,MOL,MONNARI,MOSTALWAR,MWTRADE,NANOGROUP,NETIA,NEUCA,NEWAG,NOVITA,NTTSYSTEM,ODLEWNIE,OEX,OPONEO,OPTEAM,ORANGEPL,ORZBIALY,PBG,PBKM,PCCEXOL,PCCROKITA,PEKABEX,PEKAO,PEP,PGE,PGNIG,PGSSOFT,PHN,PKNORLEN,PKOBP,PKPCARGO,PLAYWAY,PLAYWAY,POLICE,POLIMEXMS,POLNORD,POLWAX,POZBUD,PRAGMAFA,PRAIRIE,PROCHEM,PROTEKTOR,PULAWY,PZU,QUERCUS,R22,RAFAKO,RAINBOW,RANKPROGR,RELPOL,ROPCZYCE,RUBICON,SANOK,SANTANDER,SANWIL,SEKO,SELENAFM,SELVITA,SERINUS,SKARBIEC,SNIEZKA,STALEXP,STALPROD,STALPROFI,STELMET,SUNEX,SYGNITY,SYNEKTIK,STALEXP,TARCZYNSKI,TAURONPE,TESGAS,TIM,TORPOL,TOWERINVT,TOYA,TRAKCJA,ULMA,UNIBEP,UNIMOT,URSUS,VIGOSYS,VINDEXUS,VISTULA,VIVID,VOTUM,VOXEL,WAWEL,WIELTON,WIRTUALNA,WITTCHEN,WORKSERV,XTB,ZEPAK,ZPUE,ZYWIEC".split(","));
    }

    public List<String> fetchActiveTickersUs() {
        return Lists.newArrayList("A.US,AA.US,AAL.US,AAN.US,AAP.US,AAPL.US,ABBV.US,ABC.US,ABEO.US,ABEV.US,ABMD.US,ABNB.US,ABT.US,ABUS.US,ABX.US,ACB.US,ACC.US,ACCD.US,ACCO.US,ACI.US,ACM.US,ACN.US,ADBE.US,ADI.US,ADIL.US,ADM.US,ADNT.US,ADP.US,ADS.US,ADSK.US,AEE.US,AEM.US,AEO.US,AEP.US,AER.US,AES.US,AFG.US,AFIB.US,AFL.US,AG.US,AGCO.US,AGEN.US,AGFS.US,AGIO.US,AGNC.US,AGRO.US,AIG.US,AIMC.US,AIV.US,AIZ.US,AJG.US,AKAM.US,ALB.US,ALGN.US,ALK.US,ALKS.US,ALL.US,ALLE.US,ALNY.US,ALT.US,ALXN.US,AMAT.US,AMBA.US,AMC.US,AMCX.US,AMD.US,AME.US,AMG.US,AMGN.US,AMP.US,AMRN.US,AMT.US,AMX.US,AMZN.US,AN.US,ANET.US,ANF.US,ANSS.US,ANTM.US,AON.US,AOS.US,APA.US,APD.US,APH.US,APHA.US,API.US,APPF.US,APPN.US,APPS.US,APRN.US,APT.US,APTV.US,ARCC.US,ARCO.US,ARE.US,ARNC1.US,ARR.US,ARVN.US,ARW.US,ASH.US,ASM.US,ASMB.US,ATHM.US,ATNX.US,ATO.US,ATR.US,ATVI.US,AU.US,AUY.US,AVB.US,AVGO.US,AVLR.US,AVT.US,AVY.US,AWK.US,AXP.US,AYI.US,AYRO.US,AYX.US,AZN.US,AZO.US,BA.US,BABA.US,BAC.US,BAM.US,BAND.US,BAP.US,BAX.US,BB.US,BBBY.US,BBDC.US,BBY.US,BC.US,BCRX.US,BDX.US,BE.US,BEKE.US,BEN.US,BFB.US,BG.US,BGNE.US,BGS.US,BHC.US,BHF.US,BHP.US,BIDU.US,BIG.US,BIGC.US,BIIB.US,BK.US,BKCC.US,BKH.US,BKNG.US,BKR.US,BL.US,BLDP.US,BLK.US,BLL.US,BLNK.US,BLUE.US,BMA.US,BMO.US,BMRN.US,BMY.US,BNTX.US,BOX.US,BP.US,BPOP.US,BR.US,BRFS.US,BRG.US,BRKA.US,BRKB.US,BRKS.US,BSX.US,BTG.US,BUD.US,BURL.US,BWA.US,BX.US,BXMT.US,BXP.US,BXS.US,BYND.US,BZUN.US,C.US,CACC.US,CAG.US,CAH.US,CAKE.US,CAMP.US,CAR.US,CARA.US,CASY.US,CAT.US,CB.US,CBAT.US,CBD.US,CBOE.US,CBRE.US,CBRL.US,CBSH.US,CC.US,CCI.US,CCJ.US,CCK.US,CCL.US,CCU.US,CDE.US,CDK.US,CDLX.US,CDNS.US,CDW.US,CE.US,CERN.US,CETX.US,CF.US,CFG.US,CGNX.US,CHD.US,CHE.US,CHGG.US,CHKP.US,CHL.US,CHNG.US,CHRS.US,CHRW.US,CHS.US,CHTR.US,CI.US,CIB.US,CIEN.US,CINF.US,CIT.US,CL.US,CLDR.US,CLF.US,CLI.US,CLR.US,CLVS.US,CLX.US,CM.US,CMA.US,CMC.US,CMCSA.US,CME.US,CMG.US,CMI.US,CMS.US,CNC.US,CNI.US,CNK.US,CNO.US,CNP.US,CNX.US,CODX.US,COF.US,COG.US,COHR.US,COMM.US,CONE.US,COO.US,COOP.US,COP.US,COR.US,COST.US,COTY.US,COUP.US,CPA.US,CPB.US,CPE.US,CPRI.US,CPRT.US,CPS.US,CPT.US,CPTA.US,CRBP.US,CREE.US,CRI.US,CRL.US,CRM.US,CRSP.US,CRUS.US,CRWD.US,CSCO.US,CSGP.US,CSIQ.US,CSL.US,CSLT.US,CSX.US,CTAS.US,CTB.US,CTL.US,CTLT.US,CTSH.US,CTVA.US,CTXS.US,CVAC.US,CVLT.US,CVNA.US,CVS.US,CVX.US,CWH.US,CXO.US,CXW.US,CYBR.US,CYH.US,CZR.US,CZZ.US,D.US,DADA.US,DAL.US,DAN.US,DAO.US,DAVA.US,DBD.US,DBX.US,DD.US,DDD.US,DDOG.US,DDS.US,DE.US,DECK.US,DEI.US,DELL.US,DF.US,DFS.US,DG.US,DGX.US,DHC.US,DHI.US,DHR.US,DIN.US,DIS.US,DISCA.US,DISCK.US,DISH.US,DK.US,DKNG.US,DKS.US,DLR.US,DLTR.US,DNKN.US,DNOW.US,DO.US,DOC.US,DOCU.US,DOMO.US,DOV.US,DOW.US,DOX.US,DOYU.US,DPHC.US,DPZ.US,DQ.US,DRE.US,DRI.US,DS.US,DT.US,DTE.US,DUK.US,DVA.US,DVAX.US,DVN.US,DXC.US,DXCM.US,DY.US,EA.US,EAF.US,EAT.US,EBAY.US,ECL.US,ECOL.US,ED.US,EDIT.US,EDU.US,EFX.US,EGLE.US,EGO.US,EHTH.US,EIDX.US,EIX.US,EKSO.US,EL.US,ELF.US,ELY.US,EMN.US,EMR.US,ENB.US,ENDP.US,ENIA.US,ENPH.US,ENTA.US,EOG.US,EPAM.US,EPC.US,EPR.US,EQH.US,EQIX.US,EQR.US,EQT.US,ERIC.US,ERJ.US,ES.US,ESPR.US,ESS.US,ESTC.US,ESV.US,ETN.US,ETR.US,ETSY.US,EV.US,EVBG.US,EVOP.US,EVRG.US,EW.US,EWBC.US,EXAS.US,EXC.US,EXEL.US,EXK.US,EXP.US,EXPD.US,EXPE.US,EXPO.US,EXR.US,F.US,FAF.US,FANG.US,FAST.US,FATE.US,FB.US,FBHS.US,FBP.US,FCEL.US,FCX.US,FDS.US,FDX.US,FE.US,FEYE.US,FFIV.US,FHN.US,FIS.US,FISV.US,FIT.US,FITB.US,FIVE.US,FIVN.US,FIXX.US,FIZZ.US,FL.US,FLEX.US,FLGT.US,FLIR.US,FLO.US,FLR.US,FLS.US,FLT.US,FMC.US,FMX.US,FNV.US,FOLD.US,FOSL.US,FOUR.US,FOX.US,FOXA.US,FRC.US,FROG.US,FRT.US,FSLR.US,FSLY.US,FSM.US,FTCH.US,FTI.US,FTNT.US,FTV.US,FUV.US,FVRR.US,GBT.US,GD.US,GDDY.US,GDEN.US,GE.US,GEO.US,GES.US,GH.US,GIB.US,GIII.US,GILD.US,GIS.US,GL.US,GLNG.US,GLOB.US,GLW.US,GM.US,GME.US,GMED.US,GNTX.US,GNUS.US,GOL.US,GOLD.US,GOOG.US,GOOGC.US,GOOGL.US,GPC.US,GPN.US,GPOR.US,GPRO.US,GPS.US,GRA.US,GRMN.US,GRPN.US,GRUB.US,GS.US,GSKY.US,GSM.US,GT.US,GTES.US,GTHX.US,GTLS.US,GTN.US,GTS.US,GWPH.US,GWRE.US,GWW.US,H.US,HA.US,HAE.US,HAIN.US,HAL.US,HALO.US,HAS.US,HBAN.US,HBI.US,HCA.US,HCAC.US,HCAT.US,HCC.US,HD.US,HDB.US,HEAR.US,HELE.US,HES.US,HEXO.US,HFC.US,HHC.US,HIG.US,HII.US,HIIQ.US,HIW.US,HL.US,HLF.US,HLT.US,HMC.US,HOG.US,HOLX.US,HOME.US,HON.US,HP.US,HPE.US,HPQ.US,HPT.US,HQY.US,HRB.US,HRL.US,HSIC.US,HST.US,HSY.US,HTZ.US,HUBB.US,HUBS.US,HUM.US,HUN.US,HUYA.US,HVT.US,HWM.US,HYLN.US,HZNP.US,I.US,IAC.US,IBKR.US,IBM.US,ICE.US,ICPT.US,ICUI.US,IDA.US,IDCC.US,IDXX.US,IEX.US,IFF.US,IGT.US,IIPR.US,ILMN.US,IMGN.US,INCY.US,INFO.US,INGR.US,INO.US,INSG.US,INSP.US,INSY.US,INTC.US,INTU.US,IONS.US,IOVA.US,IP.US,IPG.US,IPGP.US,IPOB.US,IQ.US,IQV.US,IR.US,IRBT.US,IRM.US,ISRG.US,IT.US,ITCI.US,ITUB.US,ITW.US,IVR.US,IVZ.US,J.US,JACK.US,JAMF.US,JAZZ.US,JBHT.US,JBL.US,JBLU.US,JCI.US,JCOM.US,JD.US,JEF.US,JKHY.US,JKS.US,JLL.US,JMIA.US,JNJ.US,JNPR.US,JPM.US,JWN.US,K.US,KBH.US,KBR.US,KC.US,KDP.US,KEX.US,KEY.US,KEYS.US,KGC.US,KHC.US,KIM.US,KKR.US,KL.US,KLAC.US,KMB.US,KMI.US,KMT.US,KMX.US,KNDI.US,KNSL.US,KNX.US,KO.US,KODK.US,KORS.US,KOS.US,KR.US,KSS.US,KSU.US,L.US,LAC.US,LADR.US,LAMR.US,LB.US,LC.US,LCA.US,LDOS.US,LEA.US,LECO.US,LEG.US,LEN.US,LEVI.US,LGND.US,LH.US,LHX.US,LI.US,LII.US,LIN.US,LITE.US,LIVN.US,LK.US,LKQ.US,LLY.US,LM.US,LMND.US,LMT.US,LNC.US,LNG.US,LNT.US,LOGI.US,LOGM.US,LOPE.US,LOW.US,LPX.US,LRCX.US,LSCC.US,LSTR.US,LTC.US,LTHM.US,LULU.US,LUMN.US,LUNA.US,LUV.US,LVS.US,LW.US,LYB.US,LYFT.US,LYV.US,M.US,MA.US,MAA.US,MAC.US,MAIN.US,MAN.US,MAR.US,MARK.US,MAS.US,MASI.US,MAT.US,MAXR.US,MBIO.US,MBRX.US,MCD.US,MCF.US,MCHP.US,MCK.US,MCO.US,MD.US,MDB.US,MDC.US,MDLA.US,MDLZ.US,MDP.US,MDR.US,MDRX.US,MDT.US,MDU.US,MED.US,MEDP.US,MEET.US,MELI.US,MET.US,MGA.US,MGI.US,MGM.US,MGNX.US,MHK.US,MIDD.US,MIME.US,MKC.US,MKL.US,MKSI.US,MKTX.US,MLCO.US,MLM.US,MMC.US,MMM.US,MMS.US,MMYT.US,MNK.US,MNST.US,MNTA.US,MO.US,MOH.US,MOMO.US,MOS.US,MPC.US,MPWR.US,MRK.US,MRNA.US,MRO.US,MRVL.US,MS.US,MSCI.US,MSFT.US,MSI.US,MSM.US,MSTR.US,MTB.US,MTCH.US,MTDR.US,MTN.US,MTZ.US,MU.US,MUR.US,MUX.US,MVIS.US,MXIM.US,MXL.US,MYL.US,MYOK.US,MYOV.US,NAT.US,NAVI.US,NBEV.US,NBIX.US,NBR.US,NCLH.US,NCNO.US,NCR.US,NDAQ.US,NDSN.US,NE.US,NEE.US,NEM.US,NEO.US,NEPT.US,NET.US,NEWR.US,NFLX.US,NGD.US,NGG.US,NGM.US,NHI.US,NI.US,NIO.US,NKE.US,NKLA.US,NKTR.US,NLOK.US,NLSN.US,NLY.US,NNDM.US,NNN.US,NNOX.US,NOC.US,NOV.US,NOW.US,NPTN.US,NRG.US,NRIX.US,NRZ.US,NSC.US,NSTG.US,NTAP.US,NTCT.US,NTES.US,NTLA.US,NTNX.US,NTR.US,NTRS.US,NUAN.US,NUE.US,NVAX.US,NVDA.US,NVO.US,NVR.US,NVST.US,NVTA.US,NWL.US,NWS.US,NWSA.US,NXPI.US,NYCB.US,NYT.US,O.US,OAS.US,OC.US,ODFL.US,OEC.US,OGI.US,OHI.US,OI.US,OII.US,OIS.US,OKE.US,OKTA.US,OLED.US,OLLI.US,OLN.US,OMC.US,ON.US,ONEM.US,OPI.US,OPK.US,OPRA.US,ORA.US,ORCL.US,ORI.US,ORLY.US,OSB.US,OSK.US,OSTK.US,OTEX.US,OUT.US,OVV.US,OXLC.US,OXSQ.US,OXY.US,OZK.US,PAAS.US,PAC.US,PAGS.US,PAM.US,PANW.US,PAYC.US,PAYX.US,PBA.US,PBCT.US,PBF.US,PBI.US,PBR.US,PBYI.US,PCAR.US,PCG.US,PD.US,PDCO.US,PDD.US,PE.US,PEAK.US,PEG.US,PEI.US,PENN.US,PEP.US,PFE.US,PFG.US,PFPT.US,PG.US,PGR.US,PH.US,PHM.US,PHR.US,PIC.US,PII.US,PINS.US,PK.US,PKG.US,PKI.US,PKX.US,PLAN.US,PLAY.US,PLD.US,PLL.US,PLTR.US,PLUG.US,PM.US,PNC.US,PNR.US,PNW.US,POST.US,PPBI.US,PPG.US,PPL.US,PRAH.US,PRGO.US,PRLB.US,PRSP.US,PRU.US,PSA.US,PSEC.US,PSLV.US,PSN.US,PSTG.US,PSX.US,PTC.US,PTCT.US,PTEN.US,PTGX.US,PTN.US,PTON.US,PVG.US,PVH.US,PWR.US,PXD.US,PYPL.US,PZZA.US,QCOM.US,QD.US,QEP.US,QGEN.US,QRTEA.US,QRVO.US,QTT.US,QUAD.US,R.US,RACE.US,RCII.US,RCL.US,RDFN.US,RDUS.US,RE.US,REAL.US,REG.US,REGN.US,RESI.US,RF.US,RGA.US,RGEN.US,RGLD.US,RH.US,RHI.US,RHP.US,RIG.US,RIO.US,RJF.US,RL.US,RLAY.US,RLGY.US,RMD.US,RNG.US,RNR.US,ROK.US,ROKU.US,ROL.US,ROP.US,ROST.US,RPM.US,RPRX.US,RRC.US,RRD.US,RS.US,RSG.US,RTX.US,RVLV.US,RY.US,SABR.US,SAGE.US,SAVE.US,SBAC.US,SBE.US,SBGI.US,SBNY.US,SBUX.US,SCCO.US,SCHW.US,SCI.US,SCPL.US,SDGR.US,SE1.US,SEDG.US,SEE.US,SEIC.US,SELB.US,SF.US,SFIX.US,SFM.US,SGEN.US,SGMO.US,SGMS.US,SGRY.US,SHAK.US,SHLD.US,SHLL.US,SHOP.US,SHW.US,SIG.US,SIMO.US,SIRI.US,SIVB.US,SIX.US,SJI.US,SJM.US,SJR.US,SKT.US,SKX.US,SLB.US,SLG.US,SM.US,SMAR.US,SMG.US,SNA.US,SNAP.US,SNE.US,SNH.US,SNOW.US,SNPS.US,SNV.US,SO.US,SOI.US,SOLO.US,SON.US,SPAQ.US,SPCE.US,SPG.US,SPGI.US,SPI.US,SPLK.US,SPN.US,SPOT.US,SPR.US,SPWR.US,SQ.US,SQM.US,SRCL.US,SRE.US,SRG.US,SRNE.US,SRPT.US,SSL.US,SSNC.US,SSRM.US,SSYS.US,STAA.US,STAG.US,STLD.US,STMP.US,STNE.US,STNG.US,STOK.US,STOR.US,STT.US,STX.US,STZ.US,SU.US,SUMO.US,SUP.US,SVC.US,SVM.US,SWK.US,SWKS.US,SWN.US,SYF.US,SYK.US,SYMC.US,SYNA.US,SYY.US,T.US,TAL.US,TAP.US,TBIO.US,TCDA.US,TCPC.US,TDC.US,TDG.US,TDOC.US,TDS.US,TEAM.US,TECD.US,TECH.US,TEL.US,TELL.US,TEN.US,TER.US,TERP.US,TEVA.US,TEX.US,TFC.US,TFX.US,TGI.US,TGNA.US,TGS.US,TGT.US,TGTX.US,THC.US,THG.US,THO.US,THS.US,TIF.US,TJX.US,TK.US,TKR.US,TLK.US,TLND.US,TLRY.US,TME.US,TMO.US,TMUS.US,TNDM.US,TOL.US,TOT.US,TPL.US,TPR.US,TPX.US,TREE.US,TREX.US,TRGP.US,TRIP.US,TRMB.US,TRN.US,TROW.US,TRST.US,TRU.US,TRV.US,TRVG.US,TRVN.US,TS.US,TSCO.US,TSE.US,TSG.US,TSLA.US,TSM.US,TSN.US,TTC.US,TTD.US,TTM.US,TTWO.US,TUFN.US,TUP.US,TV.US,TW.US,TWLO.US,TWOU.US,TWST.US,TWTR.US,TX.US,TXN.US,TXT.US,TYL.US,U.US,UA.US,UAA.US,UAL.US,UBER.US,UBX.US,UDR.US,UEC.US,UFPI.US,UFS.US,UGI.US,UHS.US,UI.US,ULTA.US,UMPQ.US,UNFI.US,UNH.US,UNIT.US,UNM.US,UNP.US,UPS.US,UPWK.US,URBN.US,URI.US,USB.US,USFD.US,UTHR.US,UTX.US,UUUU.US,UXIN.US,V.US,VAC.US,VAL.US,VALE.US,VAR.US,VC.US,VEDL.US,VEEV.US,VER.US,VERI.US,VET.US,VFC.US,VIAC.US,VIPS.US,VIRT.US,VIV.US,VIVE.US,VKTX.US,VLO.US,VMC.US,VMW.US,VNE.US,VNO.US,VNTR.US,VOYA.US,VRNS.US,VRSK.US,VRSN.US,VRT.US,VRTU.US,VRTX.US,VSH.US,VST.US,VTR.US,VXRT.US,VZ.US,W.US,WAB.US,WAT.US,WB.US,WBA.US,WCC.US,WCN.US,WD.US,WDAY.US,WDC.US,WDR.US,WEC.US,WELL.US,WEN.US,WERN.US,WETF.US,WEX.US,WFC.US,WFT.US,WHR.US,WISH.US,WIX.US,WKHS.US,WLK.US,WLL.US,WLTW.US,WM.US,WMB.US,WMG.US,WMT.US,WORK.US,WPC.US,WPG.US,WPM.US,WPX.US,WRB.US,WRI.US,WRK.US,WRTC.US,WSM.US,WSO.US,WST.US,WTR.US,WTRH.US,WU.US,WUBA.US,WVE.US,WW.US,WWD.US,WWE.US,WWR.US,WY.US,WYND.US,WYNN.US,X.US,XAN.US,XEC.US,XEL.US,XLNX.US,XOM.US,XP.US,XPEV.US,XPO.US,XRAY.US,XRX.US,XSPA.US,XYL.US,YCBD.US,YELP.US,YEXT.US,YNDX.US,YPF.US,YRD.US,YUM.US,YUMC.US,YY.US,Z.US,ZBH.US,ZBRA.US,ZEN.US,ZION.US,ZM.US,ZNGA.US,ZS.US".split(","));
    }

    private List<String> fetchAllScrappedValors(String dataFilesPath, String dataFilesExtension) {
        return Stream.of(Objects.requireNonNull(new File(dataFilesPath).listFiles()))
                .filter(File::isFile)
                .filter(f -> f.getName().endsWith(dataFilesExtension))
                .map(file -> file.getName().replaceAll(dataFilesExtension, ""))
                .collect(Collectors.toList());
    }

    private List<Ticker> scrapValorsFor(LocalDateTime date) {
        openDatePage(date);
        List<WebElement> table = readDetailedTable();
        List<Ticker> dailyTickers = extractData(table, date);
        clickBackButton();
        return dailyTickers;
    }

    @SuppressWarnings("unused")
    private List<String> scrapValorNamesFor(LocalDateTime date) {
        List<Ticker> dailyTickers = scrapValorsFor(date);
        List<String> namesOnly = dailyTickers.stream()
                .map(Ticker::getName)
                .peek(x -> LOG.info(String.format("%s,", x)))
                .collect(Collectors.toList());
        LOG.info(String.format("%nAmount of valors for %s: %d%n", date, dailyTickers.size()));
        return namesOnly;
    }

    /**
     * Scrap data from GPW and append fresh records to data/*.csv files.
     *
     * @param forDate           scrap everything after this date (exclusive), skip weekends too
     * @param tickersToConsider if there are specific tickers to focus on
     * @param basePath          base path with folder structure for training data
     * @param initScrapDate     when to start at
     */
    void doScrapDailyGpw(LocalDateTime forDate, List<String> tickersToConsider, String basePath, String initScrapDate) {
        DRV = openBrowser();
        DRV.findElement(By.id("selectType")).click();
        DRV.findElement(By.xpath("//option[contains(text(),'akcje')]")).click();

        String trainingDataFormat = BetlejemXtbConstants.TRAINING_DATA_DATE_FORMAT;
        DateTimeFormatter formatter = new DateTimeFormatterBuilder().appendPattern(trainingDataFormat).toFormatter();
        LocalDateTime date = BetlejemUtils.getLastScrapDate(basePath, trainingDataFormat, LocalDateTime.parse(initScrapDate, formatter), "");
        date = date.plusDays(1); // starting from another day after last scrap

        while (date.isBefore(forDate) || date.equals(forDate)) {
            if (BetlejemUtils.isWeekend(date)) {
                date = date.plusDays(1);
                continue; // skip weekends
            }

            openDatePage(date);
            if (DRV.findElements(By.xpath("//div[contains(text(),'Brak danych')]")).size() != 0) {
                date = date.plusDays(1);
                continue; // skip empty dates, like holidays
            }

            List<Ticker> dailyTickers = scrapValorsFor(date);
            dailyTickers.forEach(ticker -> {
                String gpwName = ticker.getName();
                try {
                    String outputFilename = gpwMapping.get(gpwName);
                    if (outputFilename != null) {
                        writeValorGpwToCsv(ticker, outputFilename);
                        BetlejemUtils.setLastScrapDate(ticker.getDate(), basePath, trainingDataFormat, "_" + outputFilename); // only store last scrap date if fresh data was found for the date...
                    } else {
                        LOG.error("Could not find mapping for GPW name: " + gpwName);
                    }
                } catch (Exception e) {
                    BetlejemUtils.logError(e);
                }
            });

            LOG.info(String.format("Daily valors scrapped for %s: %d%n", date, dailyTickers.size()));
            BetlejemUtils.setLastScrapDate(LocalDateTime.fromDateFields(date.toDate()), basePath, trainingDataFormat, ""); // only store last scrap date if fresh data was found for the date...
            date = date.plusDays(1);
        }

        prepareDataForEvaluation(BetlejemXtbConstants.DATA_PATH_GPW_D1, tickersToConsider, trainingDataFormat, "");
    }

    private List<Ticker> extractData(List<WebElement> rows, LocalDateTime date) {
        return rows.stream()
                .map(r -> extractValorFromHtml(date, r))
                .peek(System.out::println)
                .collect(Collectors.toList());
    }

    private void clickBackButton() {
        DRV.findElement(By.xpath("//a[contains(text(), 'powrót')]")).click();
    }

    private List<WebElement> readDetailedTable() {
        WebElement fullTable = DRV.findElement(By.xpath("//a[contains(@href, 'archiwum-notowan-full')]"));
        fullTable.click();
        return DRV.findElements(By.xpath("//section[contains(@class,'quotations-archive')]/table/tbody/tr"));
    }

    private void openDatePage(LocalDateTime date) {
        DRV.findElement(By.name("date")).clear();
        DRV.findElement(By.name("date")).sendKeys(date.toString(GPW_PAGE_HISTORY_INPUT_FORMAT));
        DRV.findElement(By.name("show_x")).click();
    }

    private Ticker extractValorFromHtml(LocalDateTime date, WebElement row) {
        String rowHtml = row.getAttribute("innerHTML");
        String[] columnPatterns = new String[]{
                ".*>(.*)</td>",
                ".*>.*</td>\n.*<td.*>(.*)</td>",
                ".*>.*</td>\n.*<td.*>.*</td>\n.*<td.*>(.*)</td>",
                ".*>.*</td>\n.*<td.*>.*</td>\n.*<td.*>.*</td>\n.*<td.*>\n(.*)\n.*</td>",
                ".*>.*</td>\n.*<td.*>.*</td>\n.*<td.*>.*</td>\n.*<td.*>\n.*\n.*</td>\n.*<td.*>\n(.*)\n.*</td>",
                ".*>.*</td>\n.*<td.*>.*</td>\n.*<td.*>.*</td>\n.*<td.*>\n.*\n.*</td>\n.*<td.*>\n.*\n.*</td>\n.*<td.*>\n(.*)\n.*</td>",
                ".*>.*</td>\n.*<td.*>.*</td>\n.*<td.*>.*</td>\n.*<td.*>\n.*\n.*</td>\n.*<td.*>\n.*\n.*</td>\n.*<td.*>\n.*\n.*</td>\n.*<td.*>\n(.*)\n.*</td>",
                ".*>.*</td>\n.*<td.*>.*</td>\n.*<td.*>.*</td>\n.*<td.*>\n.*\n.*</td>\n.*<td.*>\n.*\n.*</td>\n.*<td.*>\n.*\n.*</td>\n.*<td.*>\n.*\n.*</td>\n.*<td.*>\n(.*)\n.*</td>",
                ".*>.*</td>\n.*<td.*>.*</td>\n.*<td.*>.*</td>\n.*<td.*>\n.*\n.*</td>\n.*<td.*>\n.*\n.*</td>\n.*<td.*>\n.*\n.*</td>\n.*<td.*>\n.*\n.*</td>\n.*<td.*>\n.*\n.*</td>\n.*<td.*>\n(.*)\n.*</td>",
                ".*>.*</td>\n.*<td.*>.*</td>\n.*<td.*>.*</td>\n.*<td.*>\n.*\n.*</td>\n.*<td.*>\n.*\n.*</td>\n.*<td.*>\n.*\n.*</td>\n.*<td.*>\n.*\n.*</td>\n.*<td.*>\n.*\n.*</td>\n.*<td.*>\n.*\n.*</td>\n.*<td.*>\n(.*)\n.*</td>",
                ".*>.*</td>\n.*<td.*>.*</td>\n.*<td.*>.*</td>\n.*<td.*>\n.*\n.*</td>\n.*<td.*>\n.*\n.*</td>\n.*<td.*>\n.*\n.*</td>\n.*<td.*>\n.*\n.*</td>\n.*<td.*>\n.*\n.*</td>\n.*<td.*>\n.*\n.*</td>\n.*<td.*>\n.*\n.*</td>\n.*<td.*>\n(.*)\n.*</td>",
        };

        List<String> values = Arrays.stream(columnPatterns).map(pattern -> extractMatchingGroupStr(rowHtml, pattern))
                .filter(Objects::nonNull)
                .map(String::trim)
                .map(Ticker::normalizeIntStr)
                .collect(Collectors.toList());

        return new Ticker(date, values);
    }

    private String extractMatchingGroupStr(String rowHtml, String pattern) {
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(rowHtml);
        boolean matchFound = m.find();
        String mathingGroupStr = null;
        if (matchFound) {
            mathingGroupStr = m.group(1);
        }
        return mathingGroupStr;
    }

    private WebDriver openBrowser() {
        WebDriver webDriver = new ChromeDriver();
        webDriver.manage().timeouts().implicitlyWait(1, TimeUnit.SECONDS);
        webDriver.navigate().to(GPW_URL);
        return webDriver;
    }

    private boolean hasTooFewSignificantChanges(List<Map<String, BigDecimal>> valorSequence) {
        // remove too-many-zeros-vectors
        int numOfZeroDelta = valorSequence.stream()
                .filter(valor -> ZERO.compareTo(valor.get(KEY_DELTA)) == 0)
                .collect(Collectors.toList())
                .size();
        return numOfZeroDelta > MAX_PERCENTAGE_ZERO_DELTAS_IN_FEATURES * pastIntervals;
    }

    private void writeValorGpwToCsv(Ticker ticker, String outputFilename) throws IOException {
        FileWriter out = new FileWriter(String.format(BetlejemUtils.pathDataHistorical(BetlejemXtbConstants.DATA_PATH_GPW_D1) + "/%s.csv", outputFilename), true);
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT)) {
            List<Object> record = new ArrayList<>();
            record.add(ticker.getDate().toLocalDate()); //0
            record.add(ticker.getName()); //1
            record.add(ticker.getPriceOpen()); //2
            record.add(ticker.getPriceClose()); //3
            record.add(ticker.getPriceDelta()); //4
            printer.printRecord(record);
        }
    }

    void closeWebDriver() {
        if (DRV != null) {
            DRV.close();
        }
    }

    @SuppressWarnings("Duplicates")
    void transformStooqMarketData(String dateLimitAfterStr, String dateLimitBeforeStr, List<String> tickers, String basePath, PERIOD_CODE period, String trainingDataDateFormat, String tickerSuffix) {
        String pathDataUsHistoricalOutput = BetlejemUtils.pathDataHistorical(basePath);
        String pathDataUsHistoricalInput = String.format("%s%sraw%s", pathDataUsHistoricalOutput, File.separator, File.separator);

        File inputPath = new File(pathDataUsHistoricalInput);

        DateTimeFormatter dateInputFormatStooq = new DateTimeFormatterBuilder().appendPattern(STOOQ_DATE_FORMAT).toFormatter();
        DateTimeFormatter timeInputFormatStooq = new DateTimeFormatterBuilder().appendPattern(STOOQ_TIME_FORMAT).toFormatter();
        DateTimeFormatter dateInputFormatLimit = new DateTimeFormatterBuilder().appendPattern(BetlejemXtbConstants.TRAINING_DATA_DATE_FORMAT).toFormatter();

        LocalDate dateLimitAfter = LocalDate.parse(dateLimitAfterStr, dateInputFormatLimit);
        LocalDate dateLimitBefore = LocalDate.parse(dateLimitBeforeStr, dateInputFormatLimit);

        LOG.info(String.format("Input path: %s%n", inputPath.getAbsolutePath()));
        ArrayList<File> rawInputFiles = Optional.ofNullable(inputPath.listFiles()).map(Lists::newArrayList).orElse(Lists.newArrayList());
        for (File rawInput : rawInputFiles) {
            String txtExtension = String.format(".%s", TXT_EXTENSION);
            String csvExtension = String.format(".%s", CSV_EXTENSION);
            String tickerName = rawInput.getName().replace(txtExtension, tickerSuffix).toUpperCase();
            if (!tickers.contains(tickerName) && !tickers.contains(tickerName.replaceAll(tickerSuffix, ""))) {
                LOG.info(String.format("Skipping (not on active list): %s%n", tickerName));
                continue;
            }

            String tickerOutputFilename = String.format("%s%s", tickerName, csvExtension);
            String outputPath = String.format("%s%s%s", pathDataUsHistoricalOutput, File.separator, tickerOutputFilename);
            LOG.info(String.format("Generating: %s%n", outputPath));

            String scrapDateFilenameSuffix = "_" + tickerName;
            LocalDateTime lastScrapDate = BetlejemUtils.getLastScrapDate(basePath, trainingDataDateFormat, BetlejemUtils.lastScrapDateFallback(period), scrapDateFilenameSuffix);

            try (Reader valorFileReader = new FileReader(rawInput)) {
                CSVParser valorRecords = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(valorFileReader);
                FileWriter outputFileWriter = new FileWriter(outputPath, false);
                try (CSVPrinter outputPrinter = new CSVPrinter(outputFileWriter, CSVFormat.DEFAULT)) {
                    AtomicReference<LocalDateTime> lastRecordTime = new AtomicReference<>(lastScrapDate);
                    for (CSVRecord record : valorRecords) {
                        String dateStr = record.get(IDX_STOOQ_DATE);
                        LocalDate recordDate = LocalDate.parse(dateStr, dateInputFormatStooq);
                        String timeStr = record.get(IDX_STOOQ_TIME);
                        LocalTime recordTime = LocalTime.parse(timeStr, timeInputFormatStooq);

                        LocalDateTime newDate;
                        if (period == PERIOD_CODE.PERIOD_D1) {
                            newDate = recordDate.toLocalDateTime(LocalTime.MIDNIGHT);
                        } else if (period == PERIOD_CODE.PERIOD_M5) {
                            newDate = recordDate.toDateTime(recordTime).toLocalDateTime();
                        } else {
                            throw new RuntimeException("Not implemented for period=" + period);
                        }

                        String openStr = record.get(IDX_STOOQ_OPEN);
                        String closeStr = record.get(IDX_STOOQ_CLOSE);
                        BigDecimal openBD = new BigDecimal(openStr);
                        openBD = openBD.subtract(ZERO).compareTo(NEAR_ZERO) <= 0 ? NEAR_ZERO : openBD; // nasty hack but we use data with max 0.0000 precision anyway
                        BigDecimal deltaPercentage = (new BigDecimal(closeStr).subtract(openBD)).divide(openBD, 4, HALF_EVEN).multiply(ONE_HUNDRED_PROC);

                        if (!recordDate.isBefore(dateLimitBefore)) {
                            break;
                        }

                        if (recordDate.isAfter(dateLimitAfter)) {
                            List<String> outputSequence = new ArrayList<>();
                            outputSequence.add(newDate.toString(trainingDataDateFormat)); //0
                            outputSequence.add(tickerName); //1
                            outputSequence.add(openStr); //2
                            outputSequence.add(closeStr); //3
                            outputSequence.add(deltaPercentage.setScale(2, HALF_EVEN).toString()); //4

                            try {
                                outputPrinter.printRecord(outputSequence);
                                outputPrinter.flush();

                                lastRecordTime.set(newDate);
                            } catch (Exception e) {
                                BetlejemUtils.logError(e);
                            }
                        }
                    }
                    BetlejemUtils.setLastScrapDate(lastRecordTime.get(), basePath, trainingDataDateFormat, scrapDateFilenameSuffix); // only store last scrap date if fresh data was found for the date...
                }
            } catch (Exception e) {
                BetlejemUtils.logError(e);
            }
        }
    }

    void reviewAndCleanUpData(String basePath, int maxDeltaUsd, int minRecordsAmount, boolean doDelete, String trainingDataDateFormat) throws IOException {
        String filenameDeltas = "deltas.txt";
        String filenameSuspicious = "suspicious.txt";
        LOG.info("Deleted existing deltas? " + new File(filenameDeltas).delete());
        LOG.info("Deleted existing suspicious? " + new File(filenameSuspicious).delete());
        String scrappedValorsDir = BetlejemUtils.pathDataHistorical(basePath);

        FileWriter outputDeltas = new FileWriter(filenameDeltas, true);
        BufferedWriter deltaFileWriter = new BufferedWriter(outputDeltas);
        FileWriter outputSuspicious = new FileWriter(filenameSuspicious, true);
        BufferedWriter suspiciousFileWriter = new BufferedWriter(outputSuspicious);

        Set<String> suspicious = new HashSet<>();
        Set<String> sparse = new HashSet<>();
        Set<String> inactive = new HashSet<>();
        AtomicInteger count = new AtomicInteger();

        List<String> tickersList = fetchAllScrappedValors(scrappedValorsDir, String.format(".%s", CSV_EXTENSION));
        tickersList.forEach(ticker -> {
            try (Reader in = new FileReader(String.format("%s/%s.%s", scrappedValorsDir, ticker, CSV_EXTENSION))) {
                Iterable<CSVRecord> records = CSVFormat.DEFAULT.parse(in);
                int tickerRecordsAmount = 0;
                boolean isStillActive = false;
                String dateStr = "not initialized";
                for (CSVRecord record : records) {
                    tickerRecordsAmount++;
                    String deltaStr = record.get(IDX_GPW_DELTA);
                    BigDecimal deltaBD = new BigDecimal(deltaStr);
                    deltaBD = deltaBD.setScale(2, HALF_EVEN);
                    String deltaEntry = String.format("%s\n", deltaBD.toString());
                    deltaFileWriter.write(deltaEntry);
                    if (deltaBD.abs().compareTo(new BigDecimal(maxDeltaUsd)) > 0) {
                        suspiciousFileWriter.write(record.get(1));
                        suspiciousFileWriter.write(",");
                        suspiciousFileWriter.write(record.get(IDX_GPW_DATE));
                        suspiciousFileWriter.write(",");
                        suspiciousFileWriter.write(deltaStr);
                        suspiciousFileWriter.write("\n");
                        count.getAndIncrement();
                        suspicious.add(ticker);
                    }

                    dateStr = record.get(IDX_GPW_DATE);
                    DateTimeFormatter formatter = new DateTimeFormatterBuilder().appendPattern(trainingDataDateFormat).toFormatter();
                    LocalDate dateObj = LocalDateTime.parse(dateStr, formatter).toLocalDate();
                    if (dateObj.isAfter(LocalDate.now().minusYears(1))) {
                        isStillActive = true;
                    }
                }

                if (tickerRecordsAmount < minRecordsAmount) {
                    sparse.add(ticker);
                    LOG.info(String.format("Sparse ticker detected: %s: %d%n", ticker, tickerRecordsAmount));
                }

                if (!isStillActive) {
                    inactive.add(ticker);
                    LOG.info(String.format("Inactive detected - last tx at: %s: %s%n", ticker, dateStr));
                }

                deltaFileWriter.flush();
                suspiciousFileWriter.flush();
            } catch (Exception e) {
                BetlejemUtils.logError(e);
            }
        });
        LOG.info("total suspicious data: " + count);
        LOG.info("total suspicious tickers: " + suspicious.size());
        LOG.info("total sparse tickers: " + sparse.size());
        LOG.info("total inactive tickers: " + inactive.size());

        if (doDelete) {
            suspicious.forEach(ticker -> LOG.info("Deleted suspicious " + ticker + "? " + new File(String.format("%s/%s.%s", scrappedValorsDir, ticker, CSV_EXTENSION)).delete()));
            sparse.forEach(ticker -> LOG.info("Deleted sparse " + ticker + "? " + new File(String.format("%s/%s.%s", scrappedValorsDir, ticker, CSV_EXTENSION)).delete()));
            inactive.forEach(ticker -> LOG.info("Deleted inactive " + ticker + "? " + new File(String.format("%s/%s.%s", scrappedValorsDir, ticker, CSV_EXTENSION)).delete()));
        }
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

    public static String pathDataEvaluation(String basePath) {
        return String.format("%s%sevaluation", basePath, File.separator);
    }
}
