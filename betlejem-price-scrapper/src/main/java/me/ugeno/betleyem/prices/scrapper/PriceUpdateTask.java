package me.ugeno.betlejem.prices.scrapper;

import me.ugeno.betlejem.common.utils.BetlejemUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.joda.time.LocalDateTime;
import pro.xstore.api.message.codes.PERIOD_CODE;
import pro.xstore.api.message.command.APICommandFactory;
import pro.xstore.api.message.response.ChartResponse;
import pro.xstore.api.sync.SyncAPIConnector;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static me.ugeno.betlejem.common.utils.BetlejemUtils.setLastScrapDate;
import static me.ugeno.betlejem.common.utils.BetlejemUtils.transformXtbMarketRecord;
import static me.ugeno.betlejem.prices.scrapper.PricesUpdater.assembleScrapDateFilenameSuffix;
import static org.joda.time.LocalDateTime.fromDateFields;

/**
 * Created by alwi on 18/03/2021.
 * All rights reserved.
 */
class PriceUpdateTask extends RecursiveAction {
    private SyncAPIConnector connector;
    private final String dataPath;
    private final String outputFile;
    private final LocalDateTime lastScrapDate;
    private final String dateFormat;
    private final String xtbName;
    private final PERIOD_CODE period;

    PriceUpdateTask(SyncAPIConnector connector, String dataPath, String outputFile, LocalDateTime lastScrapDate, String dateFormat, String xtbName, PERIOD_CODE period) {
        this.connector = connector;
        this.dataPath = dataPath;
        this.outputFile = outputFile;
        this.lastScrapDate = lastScrapDate;
        this.dateFormat = dateFormat;
        this.xtbName = xtbName;
        this.period = period;
    }

    @Override
    protected void compute() {
        try {
            ChartResponse chartData = APICommandFactory.executeChartLastCommand(connector, xtbName, period, lastScrapDate.toDate().getTime());
            String cleanSymbolName = BetlejemUtils.cleanSymbolName(xtbName);

            int digits = chartData.getDigits();
            AtomicInteger counter = new AtomicInteger();
            try (FileWriter outputFileWriter = new FileWriter(outputFile, true)) {
                try (CSVPrinter outputPrinter = new CSVPrinter(outputFileWriter, CSVFormat.DEFAULT)) {
                    AtomicReference<LocalDateTime> lastRecordTime = new AtomicReference<>(lastScrapDate);
                    AtomicBoolean stored = new AtomicBoolean(true);
                    chartData.getRateInfos()
                            .forEach((rateRecord -> {
                                try {
                                    LocalDateTime dateTimestamp = fromDateFields(Date.from(Instant.ofEpochMilli(rateRecord.getCtm())));

                                    if (dateTimestamp.isAfter(lastScrapDate)) {
                                        outputPrinter.printRecord(transformXtbMarketRecord(dateFormat, cleanSymbolName, digits, rateRecord, dateTimestamp));
                                        counter.getAndIncrement();
                                    }

                                    lastRecordTime.set(dateTimestamp);
                                } catch (IOException e) {
                                    stored.set(false);
                                    System.err.printf("IOException during write to file: %s%n", e.getMessage());
                                }
                            }));

                    if (stored.get()) {
                        outputPrinter.flush();
                        setLastScrapDate(lastRecordTime.get(), dataPath, dateFormat, assembleScrapDateFilenameSuffix(cleanSymbolName)); // only store last scrap date if fresh data was found for the date...
                    }
                }
            } catch (IOException e) {
                System.err.printf("Error during write to file %s: %s%n", e.getClass().getSimpleName(), e.getMessage());
                e.printStackTrace();
            }

            int count = counter.get();
            if (count > 0) {
                System.out.printf("%s-%d-%s ", xtbName, count, lastScrapDate.toString(dateFormat));
            } else {
                System.out.printf("%s-skipped ", xtbName);
            }
        } catch (Exception e) {
            System.err.printf("Error during fetching recent prices for %s - %s: %s%n", xtbName, e.getClass().getSimpleName(), e.getMessage());
            e.printStackTrace();
        }
    }
}
