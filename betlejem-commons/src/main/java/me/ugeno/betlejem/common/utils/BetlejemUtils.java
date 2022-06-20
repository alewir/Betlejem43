package me.ugeno.betlejem.common.utils;

import org.apache.commons.io.FileUtils;
import org.joda.time.DateTimeConstants;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import pro.xstore.api.message.codes.PERIOD_CODE;
import pro.xstore.api.message.records.RateInfoRecord;

import java.io.File;
import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_EVEN;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.BY_HALF;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.DEFAULT_ENC;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.DIV_SCALE;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.NEAR_ZERO;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.ONE_HUNDRED_PROC;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.ONE_KILO;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.PRICE_SCALE;

/**
 * Created by alwi on 14/03/2021.
 * All rights reserved.
 */
public class BetlejemUtils {
    public static String cleanSymbolName(String symbolXtb) {
        return symbolXtb.replaceAll("_9", "");
    }

    public static LocalDateTime getLastScrapDate(String dataPath, String lastScrapDateFormat, LocalDateTime fallbackDate, String suffix) {
        DateTimeFormatter formatter = new DateTimeFormatterBuilder().appendPattern(lastScrapDateFormat).toFormatter();
        try {
            File dateFile = new File(pathFileWithDateOfScrap(dataPath, suffix));
            List<String> lines = FileUtils.readLines(dateFile, DEFAULT_ENC);
            return lines.stream()
                    .map(str -> LocalDateTime.parse(str, formatter))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            System.err.printf("WARN: Exception - %s: %s%n", e.getClass().getSimpleName(), e.getMessage());
            return fallbackDate;
        }
    }

    public static LocalDateTime lastScrapDateFallback(PERIOD_CODE period) {
        if (period.equals(PERIOD_CODE.PERIOD_M1)) {
            return LocalDateTime.now().minusMonths(1).plusDays(1).withHourOfDay(0).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0);
        } else if (period.equals(PERIOD_CODE.PERIOD_M5)) {
            return LocalDateTime.now().minusMonths(1).plusDays(1).withHourOfDay(0).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0);
        } else if (period.equals(PERIOD_CODE.PERIOD_D1)) {
            return LocalDateTime.now().minusMonths(13).plusDays(1).withHourOfDay(0).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0);
        } else {
            throw new RuntimeException(String.format("Fallback scrap date not configured for period: %s", period.toString()));
        }
    }

    public static String pathDataHistorical(String basePath) {
        return basePath + File.separator + "historical";
    }

    public static void setLastScrapDate(LocalDateTime date, String basePath, String lastScrapDateFormat, String filenameSuffix) {
        try {
            File dateFile = new File(pathFileWithDateOfScrap(basePath, filenameSuffix));
            FileUtils.write(dateFile, date.toString(lastScrapDateFormat), DEFAULT_ENC, false);
        } catch (Exception e) {
            logError(e);
        }
    }

    public static List<String> transformXtbMarketRecord(String dateFormat, String symbolName, int digits, RateInfoRecord rateRecord, LocalDateTime dateTimestamp) {
        List<String> outputSequence = new LinkedList<>();
        BigDecimal pricesDivisor = BigDecimal.TEN.pow(digits);
        BigDecimal open = new BigDecimal(rateRecord.getOpen()).divide(pricesDivisor, DIV_SCALE, HALF_EVEN).setScale(PRICE_SCALE, HALF_EVEN);
        BigDecimal close = open.add(new BigDecimal(rateRecord.getClose()).divide(pricesDivisor, DIV_SCALE, HALF_EVEN)).setScale(PRICE_SCALE, HALF_EVEN);
        open = open.subtract(ZERO).compareTo(NEAR_ZERO) <= 0 ? NEAR_ZERO : open; // nasty hack but we use data with max 0.0000 precision anyway
        BigDecimal deltaPercentage = (close.subtract(open)).divide(open, DIV_SCALE, HALF_EVEN).multiply(ONE_HUNDRED_PROC).setScale(PRICE_SCALE, HALF_EVEN);
        BigDecimal volume = new BigDecimal(rateRecord.getVol()).setScale(0, HALF_EVEN);
        BigDecimal avgPrice = (close.add(open)).divide(BY_HALF, DIV_SCALE, HALF_EVEN);
        @SuppressWarnings("UnnecessaryLocalVariable") BigDecimal txAmnt = ZERO;
        BigDecimal txTotalVal = avgPrice.multiply(volume).divide(ONE_KILO, DIV_SCALE, HALF_EVEN).setScale(0, HALF_EVEN); // in k$

        outputSequence.add(dateTimestamp.toString(dateFormat)); //0
        outputSequence.add(symbolName); //1
        outputSequence.add(open.toString()); //2
        outputSequence.add(close.toString()); //3
        outputSequence.add(deltaPercentage.toString()); //4
        outputSequence.add(volume.toString()); //5
        outputSequence.add(txAmnt.toString()); //6
        outputSequence.add(txTotalVal.toString()); //7
        return outputSequence;
    }

    private static String pathFileWithDateOfScrap(String basePath, String suffix) {
        return pathDataHistorical(basePath) + File.separator + "0_date" + suffix + ".txt";
    }

    public static void logError(Exception e) {
        System.out.printf("Exception - %s: %s%n", e.getClass().getSimpleName(), e.getMessage());
    }

    public static boolean isWeekend(LocalDateTime date) {
        int dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DateTimeConstants.SATURDAY || dayOfWeek == DateTimeConstants.SUNDAY;
    }
}
