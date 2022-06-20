package me.ugeno.betlejem.binance.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Created by alwi on 11/12/2021.
 * All rights reserved.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
public class LcalcPrediction {
    @Id
    private String date;
    private String name;
    private BigDecimal price;
    private BigDecimal sell;
    private BigDecimal pass;
    private BigDecimal buy;

    public LcalcPrediction() {
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getSell() {
        return sell;
    }

    public void setSell(BigDecimal sell) {
        this.sell = sell;
    }

    public BigDecimal getPass() {
        return pass;
    }

    public void setPass(BigDecimal pass) {
        this.pass = pass;
    }

    public BigDecimal getBuy() {
        return buy;
    }

    public void setBuy(BigDecimal buy) {
        this.buy = buy;
    }

    @Override
    public String toString() {
        return "LcalcPrediction{" +
                "date=" + date +
                ", sell=" + sell.setScale(2, RoundingMode.HALF_DOWN) +
                ", pass=" + pass.setScale(2, RoundingMode.HALF_DOWN) +
                ", buy=" + buy.setScale(2, RoundingMode.HALF_DOWN) +
                ", price=" + price.setScale(2, RoundingMode.HALF_DOWN) +
                ", name='" + name + '\'' +
                '}';
    }
}
