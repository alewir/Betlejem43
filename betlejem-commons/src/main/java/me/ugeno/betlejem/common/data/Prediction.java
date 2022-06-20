package me.ugeno.betlejem.common.data;

import org.joda.time.LocalDateTime;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Created by alwi on 01/03/2021.
 * All rights reserved.
 */
public class Prediction {
    private LocalDateTime date;
    private String name;
    private BigDecimal pre;
    private BigDecimal mid;
    private BigDecimal post;
    private BigDecimal buy;
    private BigDecimal amount;
    private BigDecimal sell;

    @Override
    public String toString() {
        return String.format("%s [%s] amnt=%s: %s=>%s --- %s%%", name, date, amount, buy, sell, getAvg().toString());
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    public LocalDateTime getTimestamp() {
        return date;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setPre(BigDecimal pre) {
        this.pre = pre;
    }

    public BigDecimal getPre() {
        return pre;
    }

    public void setMid(BigDecimal main) {
        this.mid = main;
    }

    public BigDecimal getMid() {
        return mid;
    }

    public void setPost(BigDecimal post) {
        this.post = post;
    }

    public BigDecimal getPost() {
        return post;
    }

    public void setBuy(BigDecimal buy) {
        this.buy = buy;
    }

    public BigDecimal getBuy() {
        return buy;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setSell(BigDecimal sell) {
        this.sell = sell;
    }

    public BigDecimal getSell() {
        return sell;
    }

    public BigDecimal getAvg() {
        return (pre.add(mid).add(post)).divide(new BigDecimal(3), 2, RoundingMode.HALF_EVEN);
    }

    public boolean notOlderThanMin(int maxMinBack) {
        return date.isAfter(LocalDateTime.now().minusMinutes(maxMinBack));
    }
}
