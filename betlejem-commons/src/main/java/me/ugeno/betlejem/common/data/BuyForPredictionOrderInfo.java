package me.ugeno.betlejem.common.data;

import java.util.Objects;

/**
 * Created by alwi on 05/03/2021.
 * All rights reserved.
 */
public class BuyForPredictionOrderInfo {
    private final long orderNumber;
    private final Prediction predictionUsed;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BuyForPredictionOrderInfo that = (BuyForPredictionOrderInfo) o;
        return orderNumber == that.orderNumber;
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderNumber);
    }

    public BuyForPredictionOrderInfo(long orderNumber, Prediction predictionUsed) {
        this.orderNumber = orderNumber;
        this.predictionUsed = predictionUsed;
    }

    public long getOrderNumber() {
        return orderNumber;
    }

    public Prediction getPredictionUsed() {
        return predictionUsed;
    }
}
