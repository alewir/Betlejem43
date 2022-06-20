package me.ugeno.betlejem.tradebot.expose;

import me.ugeno.betlejem.common.data.Prediction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Created by alwi on 22/03/2021.
 * All rights reserved.
 */
@Service
public class PredictionService implements IPredictionService {

    private final PredictionsRepository repository;

    @Autowired
    public PredictionService(PredictionsRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Prediction> loadUsD1Predictions() {
        return repository.fetchUsD1Predictions();
    }

    @Override
    public List<Prediction> loadGpwD1Predictions() {
        return repository.fetchGpwD1Predictions();
    }

    @Override
    public List<Prediction> loadUsM5Predictions() {
        return repository.fetchUsM5Predictions();
    }

    @Override
    public List<Prediction> loadGpwM5Predictions() {
        return repository.fetchGpwM5Predictions();
    }
}
