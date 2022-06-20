package me.ugeno.betlejem.common.data;

import java.util.Map;

/**
 * Created by alwi on 02/03/2021.
 * All rights reserved.
 */
public class MarketPredictions {
    private final Map<String, Prediction> predictions_d1;
    private final Map<String, Prediction> predictions_m5;

    public MarketPredictions(Map<String, Prediction> predictions_d1, Map<String, Prediction> predictions_m5) {
        this.predictions_d1 = predictions_d1;
        this.predictions_m5 = predictions_m5;
    }

    public Map<String, Prediction> getPredictions_d1() {
        return predictions_d1;
    }

    public Map<String, Prediction> getPredictions_m5() {
        return predictions_m5;
    }
}
