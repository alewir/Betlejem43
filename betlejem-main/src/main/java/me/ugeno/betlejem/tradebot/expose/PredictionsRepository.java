package me.ugeno.betlejem.tradebot.expose;

import me.ugeno.betlejem.common.data.Prediction;
import me.ugeno.betlejem.tradebot.TradeBotWorker;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static me.ugeno.betlejem.common.utils.BetlejemUtils.logError;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.DATA_PATH_GPW_D1;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.DATA_PATH_GPW_M5;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.DATA_PATH_US_D1;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.DATA_PATH_US_M5;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.DAYS_BACK_TO_FRIDAY;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.TRAINING_DATA_DATE_FORMAT;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.TRAINING_DATA_DATE_FORMAT_MIN;
import static me.ugeno.betlejem.tradebot.trainer.TrainingDataGenerator.pathDataEvaluation;
import static org.joda.time.LocalDateTime.parse;

/**
 * Created by alwi on 22/03/2021.
 * All rights reserved.
 */
@Repository
public class PredictionsRepository {
    private static final Logger LOG = LoggerFactory.getLogger(TradeBotWorker.class);
    private static final String EVAL_FILE_PATTERN = "%s/results_eval_%s.csv";

    private static final String LBL_FIELD_DATE = "fdate";
    private static final String LBL_FIELD_NAME = "name";
    private static final String LBL_FIELD_BUY = "buy";
    private static final String LBL_FIELD_AMOUNT = "amount";

    List<Prediction> fetchUsD1Predictions() {
        return load_recent_evaluations(DATA_PATH_US_D1, TRAINING_DATA_DATE_FORMAT, "04in01", "04in02", "04in03", "sell_04");
    }

    List<Prediction> fetchGpwD1Predictions() {
        return load_recent_evaluations(DATA_PATH_GPW_D1, TRAINING_DATA_DATE_FORMAT, "04in01", "04in02", "04in03", "sell_04");
    }

    List<Prediction> fetchUsM5Predictions() {
        return load_recent_evaluations(DATA_PATH_US_M5, TRAINING_DATA_DATE_FORMAT_MIN, "03in12", "03in13", "03in14", "sell_03");
    }

    List<Prediction> fetchGpwM5Predictions() {
        return load_recent_evaluations(DATA_PATH_GPW_M5, TRAINING_DATA_DATE_FORMAT_MIN, "03in12", "03in13", "03in14", "sell_03");
    }

    @SuppressWarnings("Duplicates")
    public static List<Prediction> load_recent_evaluations(String basePath, String recordDateFormat, String lblPre, String lblMid, String lblPost, String lblSell) {
        LocalDate today = LocalDate.now().toDateTimeAtStartOfDay().toLocalDateTime().toLocalDate();

        List<Prediction> predictions = new ArrayList<>();
        try {
            String evaluationResultsFilename = String.format(EVAL_FILE_PATTERN, pathDataEvaluation(basePath), today);
            LOG.debug(String.format("Loading evaluation results from: %s", evaluationResultsFilename));

            if (!new File(evaluationResultsFilename).exists()) {
                LOG.warn("File not found: " + evaluationResultsFilename);
                return List.of(); // only return if file for Today was generated
            }

            try (Reader evalResultsFileReader = new FileReader(evaluationResultsFilename)) {
                CSVParser evalResults = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(evalResultsFileReader);
                for (CSVRecord resultRecord : evalResults) {
                    String dateStr = resultRecord.get(LBL_FIELD_DATE);
                    String name = resultRecord.get(LBL_FIELD_NAME);
                    String buyStr = resultRecord.get(LBL_FIELD_BUY);
                    String amountStr = resultRecord.get(LBL_FIELD_AMOUNT);
                    String sellStr = resultRecord.get(lblSell);

                    String preStr = resultRecord.get(lblPre);
                    String midStr = resultRecord.get(lblMid);
                    String postStr = resultRecord.get(lblPost);

                    LocalDateTime recordDate = parse(dateStr, new DateTimeFormatterBuilder().appendPattern(recordDateFormat).toFormatter());

                    if (!recordDate.toLocalDate().isBefore(today.minusDays(DAYS_BACK_TO_FRIDAY))) {
                        BigDecimal pre = new BigDecimal(preStr);
                        BigDecimal mid = new BigDecimal(midStr);
                        BigDecimal post = new BigDecimal(postStr);
                        BigDecimal buy = new BigDecimal(buyStr);
                        BigDecimal amount = new BigDecimal(amountStr);
                        BigDecimal sell = new BigDecimal(sellStr);

                        Prediction pred = new Prediction();
                        pred.setDate(recordDate);
                        pred.setName(name);
                        pred.setPre(pre);
                        pred.setMid(mid);
                        pred.setPost(post);
                        pred.setBuy(buy);
                        pred.setAmount(amount);
                        pred.setSell(sell);

                        predictions.add(pred);
                    }
                }

                evalResults.close();
            } catch (Exception e) {
                logError(e);
            }
        } catch (Exception e) {
            logError(e);
        }

        LOG.debug(String.format("Loaded %d positions.%n", predictions.size()));
        return predictions;
    }
}
