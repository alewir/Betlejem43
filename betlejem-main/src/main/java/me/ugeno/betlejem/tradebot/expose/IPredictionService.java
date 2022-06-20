package me.ugeno.betlejem.tradebot.expose;

import me.ugeno.betlejem.common.data.Prediction;

import java.util.List;

/**
 * Created by alwi on 22/03/2021.
 * All rights reserved.
 */
public interface IPredictionService {
    List<Prediction> loadUsD1Predictions();

    List<Prediction> loadUsM5Predictions();

    List<Prediction> loadGpwD1Predictions();

    List<Prediction> loadGpwM5Predictions();
}