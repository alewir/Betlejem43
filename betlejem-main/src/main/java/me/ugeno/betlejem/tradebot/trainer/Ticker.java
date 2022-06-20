package me.ugeno.betlejem.tradebot.trainer;

import org.joda.time.LocalDateTime;

import java.util.List;


/**
 * Created by alwi on 19/12/2019.
 * All rights reserved.
 */
public class Ticker {
    private List<String> values;

    private LocalDateTime date;
    private String name;
    private String priceOpen;
    private String priceClose;
    private String priceDelta;

    Ticker(LocalDateTime date, List<String> values) {
        this(date,
                values.get(0), // name
                values.get(3), // open
                values.get(6), // close
                values.get(7)  // delta
        );

        this.values = values;
    }

    private Ticker(LocalDateTime date,
                   String name,
                   String priceOpen,
                   String priceClose,
                   String priceDelta) {
        this.date = date;
        this.name = name;
        this.priceOpen = priceOpen;
        this.priceClose = priceClose;
        this.priceDelta = priceDelta;
    }

    static String normalizeIntStr(String valueStr) {
        return valueStr
                .replaceAll(" ", "")
                .replaceAll(",", ".");
    }

    List<String> getValues() {
        return values;
    }

    @Override
    public String toString() {
        return "date=" + date +
                ", name='" + name + '\'' +
                ", open='" + priceOpen + '\'' +
                ", close='" + priceClose + '\'' +
                ", delta='" + priceDelta;
    }

    LocalDateTime getDate() {
        return date;
    }

    String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPriceOpen() {
        return priceOpen;
    }

    public String getPriceClose() {
        return priceClose;
    }

    public String getPriceDelta() {
        return priceDelta;
    }
}

