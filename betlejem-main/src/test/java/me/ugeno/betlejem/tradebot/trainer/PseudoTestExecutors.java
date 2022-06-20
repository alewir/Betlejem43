package me.ugeno.betlejem.tradebot.trainer;

import com.google.common.collect.Lists;
import me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import pro.xstore.api.message.codes.PERIOD_CODE;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.DATA_PATH_GPW_D1;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.DATA_PATH_GPW_M5;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.DATA_PATH_US_D1;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.DATA_PATH_US_M5;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.F_DATA_RETRO_DAYS;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.TRAINING_DATA_DATE_FORMAT;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.TRAINING_DATA_DATE_FORMAT_MIN;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.VALOR_NAME_ALL;
import static me.ugeno.betlejem.tradebot.trainer.TrainingDataGenerator.INIT_SCRAP_DATE;

/**
 * Created by alwi on 13/12/2019.
 * All rights reserved.
 */
@SuppressWarnings({"ConstantConditions", "UnnecessaryLocalVariable", "Duplicates"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PseudoTestExecutors {

    private TrainingDataGenerator trainer = new TrainingDataGenerator(VALOR_NAME_ALL, F_DATA_RETRO_DAYS);

    /*
     * ================================================== GPW ========================================================
     */

    @Test
    void doScrapGpw() {
        String initScrapDate = INIT_SCRAP_DATE;
        String dataPath = DATA_PATH_GPW_D1;
        List<String> activeValorsToConsider = trainer.fetchActiveValorsGpw();

        trainer.doScrapDailyGpw(LocalDateTime.now().minusDays(30), activeValorsToConsider, dataPath, initScrapDate);
    }

    @Test
    void transformMarketDataStooqGpw() throws IOException {
        String basePath = DATA_PATH_GPW_M5;
        PERIOD_CODE period = PERIOD_CODE.PERIOD_M5;
        String trainingDataDateFormat = TRAINING_DATA_DATE_FORMAT_MIN;
        String limitDateSince = "2018-01-01";
        String limitDateTill = "2021-02-02";
        List<String> tickers = trainer.fetchActiveValorsGpwStooqMappings();
        int maxDailyDeltaPercent = 100;
        int minRecordsAmount = 10;
        boolean doDelete = true;

        trainer.transformStooqMarketData(limitDateSince, limitDateTill, tickers, basePath, period, trainingDataDateFormat, ".PL");
        trainer.reviewAndCleanUpData(basePath, maxDailyDeltaPercent, minRecordsAmount, doDelete, trainingDataDateFormat);
    }

    @Test
    void runTfDataEvaluationGpw() throws IOException, InterruptedException {
        String basePath = DATA_PATH_GPW_M5;
        String trainingDataDateFormat = TRAINING_DATA_DATE_FORMAT_MIN;
        List<String> activeValorsToConsider = trainer.fetchActiveValorsGpwStooqMappings();

        trainer.prepareDataForEvaluation(basePath, activeValorsToConsider, trainingDataDateFormat, ".PL");
        trainer.runTfDataEvaluationGpw();
    }

    @Test
    void prepareDataForEvaluationGpw() {
        String dataPath = DATA_PATH_GPW_M5;
        String trainingDataDateFormat = TRAINING_DATA_DATE_FORMAT_MIN;
        List<String> activeValorsToConsider = trainer.fetchActiveValorsGpwStooqMappings();

        trainer.prepareDataForEvaluation(dataPath, activeValorsToConsider, trainingDataDateFormat, ".PL");
    }

    @Test
    void prepareSingleDatasetGpw() {
        int expectedIncrease = 4;
        int withinIntervals = 12;
        boolean calculateOnlyForSpecificDay = true;

        String dataPath = DATA_PATH_GPW_M5;
        String inputTimestampFormat = TRAINING_DATA_DATE_FORMAT_MIN;
        List<String> activeValorsToConsider = trainer.fetchActiveValorsGpwStooqMappings();
        String specificValorName = VALOR_NAME_ALL;

        trainer.prepareSingleDataset(expectedIncrease, withinIntervals, calculateOnlyForSpecificDay, specificValorName, activeValorsToConsider, dataPath, inputTimestampFormat, "");
    }

    @Test
    void generateMultipleDatasetsGpw() {
        int minIncrease = 2;
        int maxIncrease = 4;
        int minIntervals = 12;
        int maxIntervals = 14;
        boolean calculateOnlyForSpecificDay = true;

        String dataPath = DATA_PATH_GPW_M5;
        String inputDateTimeFormat = TRAINING_DATA_DATE_FORMAT_MIN;
        List<String> activeValorsToConsider = trainer.fetchActiveValorsGpwStooqMappings();
        ArrayList<String> specificValorsName = Lists.newArrayList(VALOR_NAME_ALL);

        trainer.generateMultipleDatasets(minIncrease, maxIncrease, minIntervals, maxIntervals, calculateOnlyForSpecificDay, specificValorsName, activeValorsToConsider, dataPath, inputDateTimeFormat, ".PL");
    }

    @Test
    void analyzeDataGpw() {
        String dataPath = DATA_PATH_GPW_D1;
        boolean normalize = true;
        BigDecimal[] boundaries = BetlejemXtbConstants.BOUNDARIES_GPW;
        trainer.analyzeData(dataPath, normalize, boundaries);
    }

    /*
     * ================================================== US ========================================================
     */

    @Test
    void prepareDataForEvaluationUS() {
        String dataPath = DATA_PATH_US_D1;
        String dateFormat = TRAINING_DATA_DATE_FORMAT;
        List<String> activeValorsToConsider = trainer.fetchActiveTickersUs();

        trainer.prepareDataForEvaluation(dataPath, activeValorsToConsider, dateFormat, "");
    }

    @Test
    void runTfEvaluationUs() throws IOException, InterruptedException {
        trainer.runTfDataEvaluationUs("predict_rnn_us_m5.py");
    }

    @Test
    void reviewAndCleanUpDataUs() throws IOException {
        String dataPath = DATA_PATH_US_D1;
        String trainingDataDateFormat = TRAINING_DATA_DATE_FORMAT;
        int maxDailyDeltaPercent = 100;
        int minRecordsAmount = 512;
        boolean doDelete = true;

        trainer.reviewAndCleanUpData(dataPath, maxDailyDeltaPercent, minRecordsAmount, doDelete, trainingDataDateFormat);
    }

    @Test
    void transformMarketDataStooqUs() throws IOException {
        String basePath = DATA_PATH_US_D1;
        PERIOD_CODE periodD1 = PERIOD_CODE.PERIOD_D1;
        String trainingDataDateFormat = TRAINING_DATA_DATE_FORMAT;
        String dateLimitSinceStr = "2018-01-01";
        String dateLimitTillStr = LocalDate.now().toString();
        List<String> tickers = trainer.fetchActiveTickersUs();
        int maxDailyDeltaPercent = 100;
        int minRecordsAmount = 512;
        boolean doDelete = true;

        trainer.transformStooqMarketData(dateLimitSinceStr, dateLimitTillStr, tickers, basePath, periodD1, trainingDataDateFormat, "");
        trainer.reviewAndCleanUpData(basePath, maxDailyDeltaPercent, minRecordsAmount, doDelete, trainingDataDateFormat);
    }

    @Test
    void prepareSingleDatasetUs() {
        int expectedIncrease = 4;
        int withinIntervals = 12;
        boolean calculateOnlyForSpecificDay = true;

        String basePath = DATA_PATH_US_M5;
        String trainingDataDateFormatMin = TRAINING_DATA_DATE_FORMAT_MIN;
        List<String> activeValorsToConsider = trainer.fetchActiveTickersUs();
        String specificValorName = VALOR_NAME_ALL;

        trainer.prepareSingleDataset(expectedIncrease, withinIntervals, calculateOnlyForSpecificDay, specificValorName, activeValorsToConsider, basePath, trainingDataDateFormatMin, "");
    }

    @Test
    void generateMultipleDatasetsUS() {
        int minIncrease = 2;
        int maxIncrease = 4;
        int minIntervals = 12;
        int maxIntervals = 14;
        boolean calculateOnlyForSpecificDay = true;

        String dataPath = DATA_PATH_US_M5;
        String inputTimestampFormat = TRAINING_DATA_DATE_FORMAT_MIN;
        List<String> activeValorsToConsider = trainer.fetchActiveTickersUs();
        ArrayList<String> specificValorsName = Lists.newArrayList(VALOR_NAME_ALL);

        trainer.generateMultipleDatasets(minIncrease, maxIncrease, minIntervals, maxIntervals, calculateOnlyForSpecificDay, specificValorsName, activeValorsToConsider, dataPath, inputTimestampFormat, "");
    }

    @Test
    void analyzeDataUs() {
        String dataPath = DATA_PATH_US_D1;
        boolean normalize = true;
        BigDecimal[] boundaries = BetlejemXtbConstants.BOUNDARIES_US;
        trainer.analyzeData(dataPath, normalize, boundaries);
    }

    /*
     * ==============================================================================
     */

    @AfterEach
    void tearDown() {
        trainer.closeWebDriver();
    }
}
