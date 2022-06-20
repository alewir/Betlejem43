package me.ugeno.betlejem.lcalc.evaluation;

import me.ugeno.betlejem.binance.data.LcalcPrediction;
import me.ugeno.betlejem.binance.training.DataSet;
import me.ugeno.betlejem.common.crypto.kafka.DataProviderKafka;
import me.ugeno.betlejem.common.crypto.kafka.EvalDeserializer;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by alwi on 13/01/2022.
 * All rights reserved.
 */
@Component
public class LcalcEvaluationCache {
    private Map<String, List<LcalcPrediction>> predictions = new LinkedHashMap<>();

    void store(String datasetName, List<LcalcPrediction> predictions) {
        this.predictions.put(datasetName, predictions);
    }

    List<LcalcPrediction> getCurrentPredictionsETHUSDT() {
        return predictions.get(DataSet.ETHUSDT_1m.TOPIC_PRICES);
    }

    List<LcalcPrediction> getCurrentPredictions() {
        return predictions.values().stream().flatMap(List::stream).collect(Collectors.toList());
    }

    List<String[]> getHistoricalPredictions(String topic) {
        DataProviderKafka dataProviderKafka = new DataProviderKafka();
        String datasetName = topic == null || topic.isBlank() ? DataSet.ETHUSDT_1m.TOPIC_EVAL : topic + "_1m_eval";
        return dataProviderKafka.fetchRecentFrom(datasetName, 2048, new EvalDeserializer());
    }
}
