package me.ugeno.betlejem.lcalc;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import me.ugeno.betlejem.common.utils.BetlejemUtils;
import me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.javatuples.Quartet;
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
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static me.ugeno.betlejem.binance.training.BinanceTrainingDataGenerator.KEY_DELTA;
import static me.ugeno.betlejem.binance.training.BinanceTrainingDataGenerator.KEY_PRICE;
import static me.ugeno.betlejem.binance.training.BinanceTrainingDataGenerator.KEY_TIMESTAMP;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.CSV_EXTENSION;
import static me.ugeno.betlejem.lcalc.LcalcTrainerApplication.BALANCE_SPLIT_AMOUNT;
import static me.ugeno.betlejem.lcalc.LcalcTrainerApplication.BASE_PATH;
import static me.ugeno.betlejem.lcalc.LcalcTrainerApplication.LIMIT_BEST_BALANCES_PER_INTERVAL;
import static me.ugeno.betlejem.lcalc.LcalcTrainerApplication.LOSS_PERCENTAGE;
import static me.ugeno.betlejem.lcalc.LcalcTrainerApplication.SIM_INIT_PRIMARY;
import static me.ugeno.betlejem.tradebot.trainer.TrainingDataGenerator.appendEnhancedSequenceEntry;
import static me.ugeno.betlejem.tradebot.trainer.TrainingDataGenerator.enhanceDataset;
import static me.ugeno.betlejem.tradebot.trainer.TrainingDataGenerator.pathDataEvaluation;

/**
 * Created by alwi on 06/12/2021.
 * All rights reserved.
 */
@SuppressWarnings("Duplicates")
public class Lcalc {
    private static final Logger LOG = LoggerFactory.getLogger(LcalcTrainerApplication.class);

    private static final String DATA_FILE_EXT = ".csv";

    private static long outputFilesIdx = 0; // index used to indicate that file with name to use already exists

    private boolean trainingMode;

    private static final Random randomizer = new Random();

    public Lcalc(boolean trainingMode) {
        this.trainingMode = trainingMode;
    }

    @SuppressWarnings("SameParameterValue")
    private String prepareFilename(String filename, String extension, String outputPath) {
        String idx = String.format("_%03d", outputFilesIdx);
        File f = new File(outputPath, filename + idx + extension);
        while (f.exists()) {
            outputFilesIdx++;
            idx = String.format("_%03d", outputFilesIdx);
            f = new File(BASE_PATH, filename + idx + extension);
            LOG.info("File name taken, trying: " + f.getAbsoluteFile());
        }

        LOG.info("Using filename: " + f.getName());
        return f.getAbsolutePath();
    }

    String calculateDecisionsTable(List<String[]> dataset, String outputName, String symbolName, int calculateIntervalsForward, int balanceSplitAmount) {
        if (!trainingMode) {
            return null;
        }

        // Prepare dry pricesList
        List<Double> valuesList = new ArrayList<>();
        for (String[] inputFileLine : dataset) {
            String priceStr = inputFileLine[1];
            valuesList.add(Double.parseDouble(priceStr));
        }

        // Prepare output file
        String outputCsv = prepareFilename(outputName + "_dt", DATA_FILE_EXT, BASE_PATH);
        try (CSVWriter outputWriter = new CSVWriter(new FileWriter(outputCsv), BetlejemXtbConstants.CSV_SEP, CSVWriter.NO_QUOTE_CHARACTER, CSVWriter.NO_ESCAPE_CHARACTER, CSVWriter.RFC4180_LINE_END)) {
            List<String> columns = new ArrayList<>();

            // Write training data to file
            double prevValue = 0.;

            // Create header
            LOG.info("");
            LOG.info("DECISION TABLE:");
            StringBuilder variantsHeaderStr = new StringBuilder();
            for (int m = 0; m <= balanceSplitAmount; m++) {
                int fractionOfBase = (int) ((100. / balanceSplitAmount) * m); // 1/4 1/2 3/4 4/4..
                variantsHeaderStr.append(String.format("%7d%%,", fractionOfBase));
            }
            variantsHeaderStr = new StringBuilder(StringUtils.chop(variantsHeaderStr.toString()));
            LOG.info(String.format("%s,   %s", "lineInd,    timestamp,    name,    price,   delta", variantsHeaderStr.toString()));

            // Calculate content
            for (int lineInd = 0; lineInd < dataset.size() - calculateIntervalsForward; lineInd++) {
                // post process existing columns - calculate delta/percentage values for each interval change
                String[] datasetEntry = dataset.get(lineInd);
                String timestamp = datasetEntry[0];
                double currentPrice = Double.valueOf(datasetEntry[1]);

                String idxStr = String.valueOf(lineInd);
                String priceStr = String.format("%10.5f", valuesList.get(lineInd));
                String deltaStr = calculateDelta(prevValue, currentPrice);
                prevValue = currentPrice;

                columns.clear();
                Collections.addAll(columns, idxStr, timestamp, symbolName, priceStr, deltaStr);

                if (trainingMode) {
                    // Calculate best suggested operation for current interval (looking given amount of hours forward)
                    BestOperationVariants opVariants = estimateBestOperationFor(valuesList, calculateIntervalsForward, lineInd);
                    StringBuilder variantsStr = new StringBuilder();
                    for (Op op : opVariants.getBestOpList()) {
                        String opVariant = String.format("%+.2f", op.getSuggestion());
                        variantsStr.append(String.format("%8s,", opVariant));

                        Collections.addAll(columns, opVariant);
                    }
                    variantsStr = new StringBuilder(StringUtils.chop(variantsStr.toString()));
                    LOG.info(String.format("%s,%s,%s,%s,%s,%s", idxStr, timestamp, symbolName, priceStr, deltaStr, variantsStr.toString()));
                }

                String[] nextLine = columns.toArray(new String[0]);
                outputWriter.writeNext(nextLine);
                outputWriter.flush();
            }
        } catch (IOException e) {
            BetlejemUtils.logError(e);
        }

        return outputCsv;
    }

    private String calculateDelta(double prevValue, double currentValue) {
        double prev = prevValue == 0 ? currentValue : prevValue;
        return String.format("%+.5f", (currentValue - prev) / prev * 100.);
    }

    public void runSimulation(String inputCsvDecisionTable, String outputName, int pricesStartIndex, int percentageAccurracy, int pastIntervals, double lossPercentage) {
        if (!trainingMode) {
            return;
        }

        // Load data with decision table
        List<BestOperationVariants> bestOperations = new ArrayList<>();
        List<Double> pricesList = new ArrayList<>();

        try (CSVReader inputReader = new CSVReader(new FileReader(inputCsvDecisionTable))) {
            String[] line;
            while ((line = inputReader.readNext()) != null) {
                // lineInd,    timestamp,    name,    price,   delta,   variantsStr {BALANCE_SPLIT_AMOUNT + 1}

                // parse price to numeric format
                Double priceNum = Double.parseDouble(line[3]);
                pricesList.add(priceNum);

                // parse best operations list per wallet ratio from decision table file
                // 00,0.00,0.00,0.00,0.00,0.00
                bestOperations.add(operationVariants(line));
            }
        } catch (IOException | CsvValidationException e) {
            BetlejemUtils.logError(e);
        }

        assert pricesList.size() == bestOperations.size() : String.format("Prices list size: %d should be equal to op list size: %d", pricesList.size(), bestOperations.size());

        // Prepare output file
        String outputCsv = prepareFilename(outputName + "_sim", DATA_FILE_EXT, BASE_PATH);
        double currentPrice = pricesList.get(pricesStartIndex);
        try (FileWriter writer = new FileWriter(outputCsv)) {
            try (CSVWriter outputWriter = new CSVWriter(writer, BetlejemXtbConstants.CSV_SEP, CSVWriter.NO_QUOTE_CHARACTER, CSVWriter.DEFAULT_ESCAPE_CHARACTER, CSVWriter.RFC4180_LINE_END)) {
                // Calculate scenarios starting from each primary:secondary variant
                LOG.info("");
                LOG.info("SIMULATION:");
                List<String> columns = new ArrayList<>();
                for (int m = 0; m <= BALANCE_SPLIT_AMOUNT; m++) {
                    double fractionOfPri = (1. / BALANCE_SPLIT_AMOUNT) * m; // 1/4 1/2 3/4 4/4..

                    if (fractionOfPri < 1) {
                        continue;
                    }

                    Balance balance = new Balance(SIM_INIT_PRIMARY);
                    balance.split(fractionOfPri, currentPrice);

                    String headerLine = String.format("%4s,%10s,%12s,%12s,%12s,%12s,%8s,%8s", "line", "price", "usd", "amount", "equivalent", "wealth", "ratio[%]", "op");
                    LOG.info(headerLine);
                    for (int j = pricesStartIndex; j < pricesList.size() - pastIntervals; j++) {
                        // Calculate ratio for current price
                        currentPrice = pricesList.get(j);

                        double pri = balance.getPri();
                        double sec = balance.getSec();
                        balance.updateValue(pri, sec, currentPrice);
                        double equivalent = balance.getEquivalent();

                        fractionOfPri = balance.getPri() / (balance.getPri() + equivalent);

                        // Select suggested operation for current ratio
                        BestOperationVariants opVariants = bestOperations.get(j);

                        Op op = randomizer.nextInt(100) < percentageAccurracy ? opVariants.getOperationVariantFor(fractionOfPri) : Op.values()[randomizer.nextInt(Op.values().length)];

                        double newPri = balance.getPri();
                        double newSec = balance.getSec();
                        double newWealth = balance.getWealth();
                        double opVal = op.getSuggestion();
                        int ratio = (int) (100 * newPri / (newPri + equivalent));

                        // Print scenario result
                        LOG.info(String.format("%4s,%10.2f,%12.2f,%12.8f,%12.2f,%12.2f,%8s,%8.2f",
                                j, currentPrice, newPri, newSec, equivalent, newWealth, ratio, opVal));

                        columns.clear();
                        Collections.addAll(columns,
                                String.valueOf(j),
                                dec(currentPrice, "0.00"),
                                dec(newPri, "0.00"),
                                dec(newSec, "0.00"),
                                dec(equivalent, "0.00"),
                                dec(newWealth, "0.00"),
                                String.valueOf(ratio),
                                op == Op.PASS ? "--" : op.name());
                        String[] nextLine = columns.toArray(new String[0]);

                        outputWriter.writeNext(nextLine);
                        outputWriter.flush();

                        // Apply suggested ratio
                        balance = new Balance(balance, op, currentPrice, lossPercentage);
                    }
                    LOG.info(headerLine);
                }
            }
        } catch (IOException e) {
            BetlejemUtils.logError(e);
        }
    }

    void prepareTrainingDataset(String inputCsvDecisionTable, String outputName, String symbolName, int pastIntervals) {
        if (!trainingMode) {
            return;
        }

        // Load data with decision table
        List<BestOperationVariants> bestOperations = new ArrayList<>();
        List<Map<String, BigDecimal>> dataset = new ArrayList<>();
        List<String> datasetTimestamps = new ArrayList<>();
        try (CSVReader inputReader = new CSVReader(new FileReader(inputCsvDecisionTable))) {
            String[] line;
            while ((line = inputReader.readNext()) != null) {
                //  lineInd,    timestamp,    valor,    close,   delta,   variantsStr {BALANCE_SPLIT_AMOUNT + 1}
                String timestamp = line[1].trim();
                datasetTimestamps.add(timestamp);
                BigDecimal close = new BigDecimal(line[3].trim());
                BigDecimal delta = new BigDecimal(line[4].trim());

                Map<String, BigDecimal> trainingBase = new HashMap<>();
                trainingBase.put(KEY_PRICE, close);
                trainingBase.put(KEY_DELTA, delta);
                dataset.add(trainingBase);

                if (trainingMode) {
                    bestOperations.add(operationVariants(line));
                }
            }
        } catch (IOException | CsvValidationException e) {
            BetlejemUtils.logError(e);
        }

        enhanceDataset(dataset);

        // Prepare output file
        String pathLcalcDataOut = String.format("%s\\training\\", BASE_PATH);
        String outputCsv = prepareFilename(outputName + (trainingMode ? "" : "_eval"), DATA_FILE_EXT, pathLcalcDataOut);
        try (CSVWriter outputWriter = new CSVWriter(new FileWriter(outputCsv), BetlejemXtbConstants.CSV_SEP, CSVWriter.NO_QUOTE_CHARACTER, CSVWriter.NO_ESCAPE_CHARACTER, CSVWriter.RFC4180_LINE_END)) {

            List<String> columns = new ArrayList<>();
            LOG.info("");
            LOG.info("TRAINING MATRIX:");

            for (int i = 1; i < dataset.size() - pastIntervals; i++) {
                List<String> outputSequence = new ArrayList<>();
                BigDecimal sumOfChanges = new BigDecimal(0);
                for (int j = 0; j < pastIntervals; j++) {
                    // calculate suggested transaction variant for current price
                    int interval = i + j;

                    Map<String, BigDecimal> symbolData = dataset.get(interval);
                    String timestamp = datasetTimestamps.get(interval);
                    String delta = symbolData.get(KEY_DELTA).toString();
                    appendEnhancedSequenceEntry(outputSequence, symbolData);

                    sumOfChanges = sumOfChanges.add(new BigDecimal(delta));

                    boolean processingLastInterval = j == pastIntervals - 1;
                    if (processingLastInterval) {
                        boolean isNonZero = sumOfChanges.compareTo(new BigDecimal(0)) != 0; // skip lines that consist of zeros only
                        if (isNonZero) {
                            columns.clear();
                            columns.add(timestamp);
                            columns.add(symbolName);
                            columns.addAll(outputSequence);

                            if (trainingMode) {
                                BestOperationVariants opVariants = bestOperations.get(interval);
                                Op whenCanSell = opVariants.getOperationVariantFor(0.);
                                Op whenCanBuy = opVariants.getOperationVariantFor(1.);
                                Op op;
                                if (whenCanSell == Op.SELL) {
                                    op = Op.SELL;
                                } else if (whenCanBuy == Op.BUY) {
                                    op = Op.BUY;
                                } else {
                                    op = Op.PASS;
                                }
                                for (int o : op.asOneHot()) {
                                    columns.add(String.format("%d", o));
                                }
                            }

//                            LOG.info(String.valueOf(columns));
                            String[] nextLine = columns.toArray(new String[0]);

                            outputWriter.writeNext(nextLine);
                            outputWriter.flush();
                        } else {
                            LOG.info("Skipping line of zeros...");
                        }

                    }
                }
            }

        } catch (IOException e) {
            BetlejemUtils.logError(e);
        }

        trainingDataPostProcessing(outputCsv);
    }

    public Optional<String> prepareDataForEvaluation(String dataPath, List<String[]> dataset, int pastIntervals, String datasetName, String timestamp) {
        String filename = String.format("%s_%s.%s", datasetName, timestamp, CSV_EXTENSION);
        String outputPath = String.format("%s%s%s", pathDataEvaluation(dataPath), File.separator, filename);

        if (!trainingMode) {
            LOG.debug("Entering prepareDataForEvaluation....");
            int n = 0;

            try (FileWriter outputFileWriter = new FileWriter(outputPath, false)) {
                try (CSVPrinter outputPrinter = new CSVPrinter(outputFileWriter, CSVFormat.DEFAULT)) {
                    List<Map<String, BigDecimal>> datasetBd = new ArrayList<>();

                    double prevPrice = 0.;
                    for (String[] entry : dataset) {
                        String timestampStr = entry[0].trim();
                        String priceStr = entry[1].trim();
                        double currentPrice = Double.parseDouble(priceStr);
                        String deltaStr = calculateDelta(prevPrice, currentPrice);
                        prevPrice = currentPrice;

                        BigDecimal timestampBd = new BigDecimal(timestampStr);
                        BigDecimal priceBd = new BigDecimal(priceStr).setScale(5, RoundingMode.HALF_EVEN);
                        BigDecimal deltaBd = new BigDecimal(deltaStr);

                        Map<String, BigDecimal> trainingBase = new HashMap<>();
                        trainingBase.put(KEY_TIMESTAMP, timestampBd);
                        trainingBase.put(KEY_PRICE, priceBd);
                        trainingBase.put(KEY_DELTA, deltaBd);
                        datasetBd.add(trainingBase);
                    }

                    if (dataset.size() == 0) { // skip active valors for which there was no historical data
                        return Optional.empty();
                    }

                    enhanceDataset(datasetBd);

                    int valorDaysTotal = dataset.size();
                    LOG.debug("Loaded-{}", valorDaysTotal);

                    // Prepare output data
                    for (int i = 0; i < valorDaysTotal; i++) {
                        @SuppressWarnings("UnnecessaryLocalVariable") int dataForwardAmnt = pastIntervals;
                        if (i < valorDaysTotal - dataForwardAmnt) {
                            if (i == valorDaysTotal - dataForwardAmnt - 1) {
                                List<Map<String, BigDecimal>> baseSequence = new ArrayList<>();
                                Map<String, BigDecimal> sequenceEntry = null;
                                for (int k = 1; k <= dataForwardAmnt; k++) {
                                    int idx = i + k;
                                    sequenceEntry = datasetBd.get(idx);
                                    baseSequence.add(sequenceEntry);
                                }
                                assert sequenceEntry != null;
                                BigDecimal sequenceLastPrice = sequenceEntry.get(KEY_PRICE);
                                BigDecimal sequenceLastTimestamp = sequenceEntry.get(KEY_TIMESTAMP);

                                n++;

                                // add features to sequence
                                List<String> enhancedSequence = new ArrayList<>();
                                for (int x = 0; x < pastIntervals; x++) {
                                    sequenceEntry = baseSequence.get(x);

                                    if (x == 0) { // first columns with some basic information
                                        enhancedSequence.add(sequenceLastTimestamp.toString());
                                        enhancedSequence.add(datasetName);
                                        enhancedSequence.add(sequenceLastPrice.toString());
                                    }

                                    // ... then sequence of data
                                    appendEnhancedSequenceEntry(enhancedSequence, sequenceEntry);
                                }

//                                LOG.info(outputSequence);
                                try {
                                    outputPrinter.printRecord(enhancedSequence);
                                } catch (Exception e) {
                                    BetlejemUtils.logError(e);
                                }
                            }
                        } else {
                            break; // not enough data left to continue - pick up next valor
                        }
                    }

                    LOG.debug(String.format("%nTotal symbols with reasonable dataset: %d - %s%n%n", n, outputPath));
                    return Optional.of(filename);
                }
            } catch (IOException e) {
                BetlejemUtils.logError(e);
            }
        }

        return Optional.empty();
    }

    /**
     * Balances already prepared training data files to get similar amount of samples for each class in labels.
     * Rewrites data to a separate *_pp.csv file.
     *
     * @param trainingDataFilePath base path for data folders
     */
    private void trainingDataPostProcessing(String trainingDataFilePath) {
        List<Long> selectedRecordsOrderedByTimestamp = new LinkedList<>();

        try (Reader trainingDataFileReader = new FileReader(trainingDataFilePath)) {
            CSVParser recordsIterator = CSVFormat.DEFAULT.parse(trainingDataFileReader);

            List<Quartet<Long, String, Long, String>> trainingDataRecords = new LinkedList<>();
            recordsIterator.forEach(record -> {
                Long timestamp = Long.parseLong(record.get(0));
                String name = record.get(1);

                Long csvRecordNumber = record.getRecordNumber();
                String category = record.get(record.size() - 3) + ":" + record.get(record.size() - 2) + ":" + record.get(record.size() - 1);
                trainingDataRecords.add(Quartet.with(timestamp, name, csvRecordNumber, category));
            });

            Map<String, List<Quartet<Long, String, Long, String>>> recordsInClass = new LinkedHashMap<>();
            trainingDataRecords.forEach(record -> {
                String category = record.getValue3();
                List<Quartet<Long, String, Long, String>> ric = recordsInClass.computeIfAbsent(category, k -> new ArrayList<>());
                ric.add(record);
            });

            List<Quartet<Long, String, Long, String>> sell = recordsInClass.get("1:0:0");
            List<Quartet<Long, String, Long, String>> pass = recordsInClass.get("0:1:0");
            List<Quartet<Long, String, Long, String>> buy = recordsInClass.get("0:0:1");

            if (sell == null) {
                LOG.error("No valuable training data found in: " + trainingDataFilePath);
                return;
            }
            trainingDataRecords.clear();
            recordsInClass.clear();

            int buyAmount = sell.size();
            int passAmount = pass.size();
            int sellAmount = buy.size();
            LOG.info("Sell op amount: " + buyAmount);
            LOG.info("Pass op amount: " + passAmount);
            LOG.info("Buy op amount: " + sellAmount);

            // TODO: Pick some sample for PASS too

            Collections.shuffle(pass);
            if (buyAmount < sellAmount) {
                // pick first random notInteresting up to limit, drop the rest
                Collections.shuffle(buy);
                buy = buy.stream()
                        .limit(buyAmount)
                        .collect(Collectors.toList());
                pass = pass.stream()
                        .limit(buyAmount)
                        .collect(Collectors.toList());
            } else {
                //  pick first random interesting up to limit, drop the rest
                Collections.shuffle(sell);
                sell = sell.stream()
                        .limit(sellAmount)
                        .collect(Collectors.toList());
                pass = pass.stream()
                        .limit(sellAmount)
                        .collect(Collectors.toList());
            }

            LinkedList<Quartet<Long, String, Long, String>> selectedRecords = new LinkedList<>();
            selectedRecords.addAll(sell);
            selectedRecords.addAll(pass);
            selectedRecords.addAll(buy);
            LOG.info("Selected amount: " + selectedRecords.size());
            sell.clear();
            pass.clear();
            buy.clear();

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

        rewriteTrainingData(selectedRecordsOrderedByTimestamp, trainingDataFilePath);
    }

    private void rewriteTrainingData(List<Long> selectedRecordsInFinalOrder, String trainingDataFilePath) {
        // Reopen input and rewrite only selected data
        ArrayList<Long> sortedRecordNumbers = new ArrayList<>(selectedRecordsInFinalOrder);
        sortedRecordNumbers.sort(Comparator.naturalOrder());

        String outputPath = String.format("%s%s", trainingDataFilePath, "_pp.csv");
        LOG.info(String.format("Generating: %s%n", outputPath));
        try (FileWriter outputFileWriter = new FileWriter(outputPath, false)) {
            try (PrintWriter outputPrinter = new PrintWriter(outputFileWriter)) {
                // re-load file content as string lines
                LOG.info("Reloading training data.");
                Map<Long, String> trainingDataFullContent = new LinkedHashMap<>();
                try (Reader trainingDataFileReader = new FileReader(trainingDataFilePath)) {
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

                LOG.info("Ready...");
            }
        } catch (Exception e) {
            BetlejemUtils.logError(e);
        }
    }

    private BestOperationVariants operationVariants(String[] line) {
        BestOperationVariants bestOp = new BestOperationVariants();
        for (int m = 0; m <= BALANCE_SPLIT_AMOUNT; m++) {
            double fractionOfPrimary = 1. / BALANCE_SPLIT_AMOUNT * m;
            bestOp.addVariant(fractionOfPrimary, Op.parseFromStr(line[5 + m]));
        }
        return bestOp;
    }

    @SuppressWarnings("SameParameterValue")
    private BestOperationVariants estimateBestOperationFor(List<Double> pricesList, int intervalsToLookForward, int currentLineIndex) {
        // Prepare list of future prices for given amount of hours to look forward to
        List<Double> futurePrices = new LinkedList<>();
        for (int forwardShift = 0; forwardShift < intervalsToLookForward; forwardShift++) {
            futurePrices.add(pricesList.get(currentLineIndex + forwardShift));
        }
        return calcOptimalResult(futurePrices);
    }

    private BestOperationVariants calcOptimalResult(List<Double> futurePrices) {
        BestOperationVariants bestOperation = new BestOperationVariants();
        for (int m = 0; m <= BALANCE_SPLIT_AMOUNT; m++) {
            double fractionOfPrimary = 1. / BALANCE_SPLIT_AMOUNT * m; // 1/4 1/2 3/4 4/4..
            Balance initBalance = new Balance(SIM_INIT_PRIMARY);

            Set<Balance> balances = new HashSet<>();
            balances.add(initBalance);

            Balance lastMax = initBalance;
            boolean initialize = true;
            for (Double price : futurePrices) {
                if (initialize) {
                    // execute once
                    initialize = false;
                    initBalance.split(fractionOfPrimary, price);
                }

                // analyze next hour
                Balance maxPerInterval = initBalance; // required to keep track on the best operation at the end of each hour - particularly to have one in place at the end of all hours...
                double maxPerIntervalWealth = 0;
                Set<Balance> currentBalances = new HashSet<>();
                for (Balance prevBalance : balances) {
                    // try all operations
                    for (Op operation : Op.values()) {
                        if (prevBalance.isSignificant(operation)) {
                            Balance newBalance = new Balance(prevBalance, operation, price, LOSS_PERCENTAGE);
                            if (newBalance.isAcceptable()) {
                                currentBalances.add(newBalance);
                                if (newBalance.getWealth() >= maxPerIntervalWealth) {
                                    maxPerInterval = newBalance;
                                    maxPerIntervalWealth = newBalance.getWealth();
                                }
                            }
                        }
                    }
                }
                lastMax = maxPerInterval;
                balances = selectTopResults(currentBalances);
            }

            Balance balance = lastMax;
            do {
                balance = balance.getParent();
            } while (balance.getParent().getParent() != null); // we take first calculated operation not the initial one (which is always PASS)

            bestOperation.addVariant(fractionOfPrimary, balance.getOperation());
        }

        return bestOperation;
    }

    private Set<Balance> selectTopResults(Set<Balance> currentBalances) {
        Set<Balance> balances = new HashSet<>();

        // Select some limited amount of locally best balances for each interval for further calc.
        ArrayList<Balance> bestPrimary = new ArrayList<>(currentBalances);
        bestPrimary.sort(Comparator.comparingLong(BalanceInternal::getSubPri));
        ArrayList<Balance> bestSecond = new ArrayList<>(currentBalances);
        bestSecond.sort(Comparator.comparingLong(BalanceInternal::getSubSec));
        ArrayList<Balance> bestWealth = new ArrayList<>(currentBalances);
        bestWealth.sort(Comparator.comparingDouble(Balance::getWealth));

        for (int i = currentBalances.size() - 1; i >= 0; i--) {
            balances.add(bestPrimary.get(i));
            balances.add(bestSecond.get(i));
            balances.add(bestWealth.get(i));

            if (i > LIMIT_BEST_BALANCES_PER_INTERVAL) {
                break;
            }
        }
        return balances;
    }

    private static String dec(double num, String pattern) {
        DecimalFormatSymbols decimalSymbols = DecimalFormatSymbols.getInstance();
        decimalSymbols.setDecimalSeparator('.');
        return new DecimalFormat(pattern, decimalSymbols).format(num);
    }
}
