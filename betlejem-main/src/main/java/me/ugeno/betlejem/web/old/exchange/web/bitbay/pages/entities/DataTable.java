package me.ugeno.betlejem.web.old.exchange.web.bitbay.pages.entities;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by alwi on 10/12/2017.
 * All rights reserved.
 */
public class DataTable {
    private List<DataRow> data = new ArrayList<>();

    private BigDecimal sumAmountTimesPrice = BigDecimal.ZERO;
    private BigDecimal sumPrice = BigDecimal.ZERO;
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private BigDecimal totalVolumeValue = BigDecimal.ZERO;

    public void addRow(DataRow row) {
        data.add(row);

        BigDecimal amount = row.getVolume();
        BigDecimal price = row.getUnitPrice();
        BigDecimal volumeValue = row.getVolumeValue();

        sumPrice = sumPrice.add(price);
        sumAmountTimesPrice = sumAmountTimesPrice.add(amount.multiply(price));
        totalAmount = totalAmount.add(amount);
        totalVolumeValue = totalVolumeValue.add(volumeValue);
    }

    @SuppressWarnings("SameParameterValue")
    public BigDecimal getWeightAvgPrice(int scale) {
        BigDecimal weightedAvg;
        if (totalAmount.equals(new BigDecimal(0))) {
            weightedAvg = sumAmountTimesPrice.divide(new BigDecimal(1), scale, BigDecimal.ROUND_HALF_DOWN);
        } else {
            weightedAvg = sumAmountTimesPrice.divide(totalAmount, scale, BigDecimal.ROUND_HALF_DOWN);
        }

        return weightedAvg;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getTotalVolumeValue() {
        return totalVolumeValue;
    }
}
