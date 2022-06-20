package me.ugeno.betlejem.lcalc.evaluation;

import me.ugeno.betlejem.binance.data.LcalcPrediction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
class LcalcEvaluationController {

    private final LcalcEvaluationCache evaluationCache;

    @Autowired
    public LcalcEvaluationController(LcalcEvaluationCache evaluationCache) {
        this.evaluationCache = evaluationCache;
    }

    @GetMapping("/eval")
    List<LcalcPrediction> current() {
        return evaluationCache.getCurrentPredictionsETHUSDT();
    }

    @GetMapping("/evalMulti")
    List<LcalcPrediction> currentMulti() {
        return evaluationCache.getCurrentPredictions();
    }

    @GetMapping("/evalHistory/{topic}")
    List<String[]> history(@PathVariable String topic) {
        return evaluationCache.getHistoricalPredictions(topic);
    }
}