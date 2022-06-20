package me.ugeno.betlejem.web.old.exchange.web.bitbay.pages.entities;

import java.math.BigDecimal;

/**
 * Created by alwi on 10/12/2017.
 * All rights reserved.
 */
public class DataRow {
    private BigDecimal unitPrice; // realisation rate
    private BigDecimal volume; // amount
    private BigDecimal volumeValue; // total price for amount

    public DataRow(String realisationRate, String amount, String price) {
        this.unitPrice = new BigDecimal(realisationRate);
        this.volume = new BigDecimal(amount);
        this.volumeValue = new BigDecimal(price);
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public BigDecimal getVolumeValue() {
        return volumeValue;
    }
}
