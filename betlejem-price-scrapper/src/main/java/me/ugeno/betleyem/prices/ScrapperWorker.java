package me.ugeno.betlejem.prices;

import me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils;
import me.ugeno.betlejem.prices.scrapper.PricesUpdater;
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
import pro.xstore.api.message.records.SymbolRecord;
import pro.xstore.api.message.response.APIErrorResponse;
import pro.xstore.api.message.response.AllSymbolsResponse;
import pro.xstore.api.sync.SyncAPIConnector;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static me.ugeno.betlejem.common.utils.BetlejemUtils.cleanSymbolName;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.CSV_EXTENSION;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.CURRENCY_PLN;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.CURRENCY_USD;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.DATA_PATH_GPW_D1;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.DATA_PATH_GPW_M5;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.DATA_PATH_US_D1;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.DATA_PATH_US_M5;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.TRAINING_DATA_DATE_FORMAT;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.TRAINING_DATA_DATE_FORMAT_MIN;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils.CATEGORY;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils.extractSymbolNames;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils.inGpwMarketCloseHours;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils.inGpwMarketOpenHours;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils.inUsMarketCloseHours;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils.inUsMarketOpenHours;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils.serverType;
import static org.joda.time.LocalDateTime.now;
import static pro.xstore.api.message.codes.PERIOD_CODE.PERIOD_D1;
import static pro.xstore.api.message.codes.PERIOD_CODE.PERIOD_M5;
import static pro.xstore.api.message.command.APICommandFactory.executeAllSymbolsCommand;
import static pro.xstore.api.sync.ServerData.ServerEnum.DEMO;

@Component
public class ScrapperWorker extends XtbClient {
    private static final Logger LOG = LoggerFactory.getLogger(ScrapperWorker.class);

    private List<SymbolRecord> tickersGpw;
    private List<SymbolRecord> tickersUs;

    private LocalDate lastDailyPredictionsDateUs;
    private LocalDate lastDailyPredictionsDateGpw;

    private boolean doUs;
    private Map<String, String> fileNamesUs;
    private Map<String, String> fileNamesGpw;
    private List<String> xtbSymbolNamesOrderedUs;
    private List<String> xtbSymbolNamesOrderedGpw;

    @Autowired
    public ScrapperWorker() {
    }

    @PostConstruct
    public void init() {
        // TODO: Handle connection in separate workers/tasks...
    }

    @SuppressWarnings("Duplicates")
    @Scheduled(fixedDelay = 10000)
    public void mainLoop() {
        DateTime now = DateTime.now();
        if (now.getHourOfDay() < 5 || now.getHourOfDay() > 23) {
            LOG.debug("\n\nSleeping...");
            return;
        }

        LocalDateTime begin = now();
        if (LOG.isDebugEnabled()) {
            LOG.debug("\n\nEntering XTB prices scrap...");
        }

        try {
            if (connected) {
                LOG.debug("Connection is active.");

                if (tickersUs == null) { // TODO: this is probably needed only once per day
                    tickersUs = fetchAllSymbolsUs();
                    xtbSymbolNamesOrderedUs = extractSymbolNames(tickersUs);
                    fileNamesUs = updateFileNames(xtbSymbolNamesOrderedUs);
                }

                if (tickersGpw == null) { // TODO: this is probably needed only once per day
                    tickersGpw = fetchAllSymbolsGpw();
                    xtbSymbolNamesOrderedGpw = extractSymbolNames(tickersGpw);
                    fileNamesGpw = updateFileNames(xtbSymbolNamesOrderedGpw);
                }

                if (doUs) {
                    updateUsMarket(connector, tickersUs, xtbSymbolNamesOrderedUs, fileNamesUs);
                    doUs = false;
                } else {
                    updateGpw(connector, tickersGpw, xtbSymbolNamesOrderedGpw, fileNamesGpw);
                    doUs = true;
                }
            } else {
                throw new Exception("Seems to not be connected...");
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
            LOG.debug(String.format("Exiting XTB prices scrap... Took: %.2f min", new Interval(begin.toDateTime(), end.toDateTime()).toDurationMillis() / 60000f));
        }
    }

    private void updateGpw(SyncAPIConnector connector, List<SymbolRecord> tickersGpw, List<String> xtbSymbolNamesOrderedGpw, Map<String, String> fileNamesGpw) {
        LocalDateTime now = now();
        LocalDate today = now.toLocalDate();

        if (today.equals(lastDailyPredictionsDateGpw)) {
            LOG.debug("D1 prices already fetched for date: " + lastDailyPredictionsDateGpw);
        } else {
            if (inGpwMarketCloseHours(now)) {
                PricesUpdater.updatePrices(connector, DATA_PATH_GPW_D1, PERIOD_D1, tickersGpw, TRAINING_DATA_DATE_FORMAT, fileNamesGpw, xtbSymbolNamesOrderedGpw);
            } else {
                LOG.debug("Don't attempt to get daily GPW prices during stock market working hours as we will get incomplete interval for today");
            }

            lastDailyPredictionsDateGpw = today;
        }

        if (inGpwMarketOpenHours(now)) {
            PricesUpdater.updatePrices(connector, DATA_PATH_GPW_M5, PERIOD_M5, tickersGpw, TRAINING_DATA_DATE_FORMAT_MIN, fileNamesGpw, xtbSymbolNamesOrderedGpw);
        }

        // TODO: store prices into DB
    }

    private void updateUsMarket(SyncAPIConnector connector, List<SymbolRecord> tickersUs, List<String> xtbSymbolNamesOrderedUs, Map<String, String> fileNamesUs) {
        LocalDateTime now = now();
        LocalDate today = now.toLocalDate();

        if (today.equals(lastDailyPredictionsDateUs)) {
            LOG.debug("D1 prices already fetched for date: " + lastDailyPredictionsDateUs);
        } else {
            if (inUsMarketCloseHours(now)) {
                PricesUpdater.updatePrices(connector, DATA_PATH_US_D1, PERIOD_D1, tickersUs, TRAINING_DATA_DATE_FORMAT, fileNamesUs, xtbSymbolNamesOrderedUs);
            } else {
                LOG.debug("Don't attempt to get daily US prices during stock market working hours as we will get incomplete interval for today");
            }

            lastDailyPredictionsDateUs = today;
        }

        if (inUsMarketOpenHours(now)) {
            PricesUpdater.updatePrices(connector, DATA_PATH_US_M5, PERIOD_M5, tickersUs, TRAINING_DATA_DATE_FORMAT_MIN, fileNamesUs, xtbSymbolNamesOrderedUs);
        }

        // TODO: store prices into DB
    }

    private Map<String, String> updateFileNames(List<String> symbols) {
        LOG.debug("Updated files names...");
        Map<String, String> fileNames = new HashMap<>();
        symbols.forEach(s -> {
            String name = cleanSymbolName(s);
            fileNames.put(name, String.format("%s.%s", name, CSV_EXTENSION));
        });
        LOG.debug("\nFinished files name update...");
        return fileNames;
    }

    private List<SymbolRecord> fetchAllSymbolsUs() throws APICommandConstructionException, APIReplyParseException, APICommunicationException, APIErrorResponse {
        AllSymbolsResponse all = executeAllSymbolsCommand(connector);
        return fetchAllSymbols(all, BetlejemXtbUtils.TYPE_US_STC_DEMO, BetlejemXtbUtils.TYPE_US_STC_REAL, CURRENCY_USD);
    }

    private List<SymbolRecord> fetchAllSymbolsGpw() throws APICommandConstructionException, APIReplyParseException, APICommunicationException, APIErrorResponse {
        AllSymbolsResponse all = executeAllSymbolsCommand(connector);
        return fetchAllSymbols(all, BetlejemXtbUtils.TYPE_GPW_STC_DEMO, BetlejemXtbUtils.TYPE_GPW_STC_REAL, CURRENCY_PLN);
    }

    private List<SymbolRecord> fetchAllSymbols(AllSymbolsResponse all, int typeStcDemo, int typeStcReal, String currency) {
        return all.getSymbolRecords()
                .stream()
                .filter(r -> r.getType() == (serverType == DEMO ? typeStcDemo : typeStcReal))
                .filter(r -> r.getCurrency().equals(currency))
                .filter(r -> r.getCategoryName().equals(CATEGORY))
                .collect(Collectors.toList());
    }
}