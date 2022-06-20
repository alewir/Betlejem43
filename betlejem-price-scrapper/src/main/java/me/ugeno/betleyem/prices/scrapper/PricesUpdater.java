package me.ugeno.betlejem.prices.scrapper;

import com.google.common.collect.Lists;
import org.joda.time.LocalDateTime;
import pro.xstore.api.message.codes.PERIOD_CODE;
import pro.xstore.api.message.records.SymbolRecord;
import pro.xstore.api.sync.SyncAPIConnector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;

import static java.io.File.separator;
import static me.ugeno.betlejem.common.utils.BetlejemUtils.cleanSymbolName;
import static me.ugeno.betlejem.common.utils.BetlejemUtils.getLastScrapDate;
import static me.ugeno.betlejem.common.utils.BetlejemUtils.lastScrapDateFallback;
import static me.ugeno.betlejem.common.utils.BetlejemUtils.pathDataHistorical;

/**
 * Created by alwi on 06/03/2021.
 * All rights reserved.
 */
public class PricesUpdater {
    public static void updatePrices(SyncAPIConnector connector, String dataPath, PERIOD_CODE period, List<SymbolRecord> tickers, String dateFormat, Map<String, String> fileNames, List<String> xtbNamesOrdered) {
        System.out.printf("Updating files with prices for period=%s min. in %s...%n", period.getCode(), dataPath);

        // TODO: open appropriate amount of connection and sustain them
        int poolSize = 5;
        List<List<String>> xtbNamesOrderedPartitioned = Lists.partition(xtbNamesOrdered, poolSize);
        for (List<String> xtbNamesPart : xtbNamesOrderedPartitioned) {
            ForkJoinPool commonPool = ForkJoinPool.commonPool();
            try {
                commonPool.awaitTermination(10, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


            List<PriceUpdateTask> subTasks = new ArrayList<>();
            for (String xtbName : xtbNamesPart) {
                SymbolRecord ticker = tickers.stream().filter(t -> t.getSymbol().startsWith(xtbName)).findFirst().orElseThrow();
                String cleanSymbolName = cleanSymbolName(ticker.getSymbol());

                String outputFile = String.format("%s%s%s", pathDataHistorical(dataPath), separator, fileNames.get(cleanSymbolName));
                LocalDateTime lastScrapDate = getLastScrapDate(dataPath, dateFormat, lastScrapDateFallback(period), assembleScrapDateFilenameSuffix(cleanSymbolName));

                // TODO: each task would require own connector (max 50 open connections)
                subTasks.add(new PriceUpdateTask(connector, dataPath, outputFile, lastScrapDate, dateFormat, xtbName, period));
            }

            ForkJoinTask.invokeAll(subTasks);
        }


        System.out.println("\nFinished updatePrices...");
    }

    static String assembleScrapDateFilenameSuffix(String cleanSymbolName) {
        return String.format("_%s", cleanSymbolName);
    }
}
