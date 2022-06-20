package me.ugeno.betlejem.tradebot.expose;

import com.opencsv.CSVWriter;
import com.opencsv.bean.ColumnPositionMappingStrategy;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvException;
import me.ugeno.betlejem.common.data.Prediction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.CSV_SEP;

/**
 * Created by alwi on 22/03/2021.
 * All rights reserved.
 */
@RestController
public class ExposePredictionsController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExposePredictionsController.class);

    final IPredictionService service;

    @Autowired
    public ExposePredictionsController(IPredictionService service) {
        this.service = service;
    }

    @RequestMapping(value = "/gpw/d1/", produces = "text/csv")
    public void loadGpwD1Predictions(HttpServletResponse response) throws IOException {
        writePredictions(response.getWriter(), service.loadGpwD1Predictions());
    }

    @RequestMapping(value = "/gpw/m5/", produces = "text/csv")
    public void loadGpwM5Predictions(HttpServletResponse response) throws IOException {
        writePredictions(response.getWriter(), service.loadGpwM5Predictions());
    }

    @RequestMapping(value = "/us/d1/", produces = "text/csv")
    public void loadUsD1Predictions(HttpServletResponse response) throws IOException {
        writePredictions(response.getWriter(), service.loadUsD1Predictions());
    }

    @RequestMapping(value = "/us/m5/", produces = "text/csv")
    public void loadUsM5Predictions(HttpServletResponse response) throws IOException {
        writePredictions(response.getWriter(), service.loadUsM5Predictions());
    }

    private static void writePredictions(PrintWriter writer, List<Prediction> predictions) {
        try {
            ColumnPositionMappingStrategy<Prediction> mapStrategy = new ColumnPositionMappingStrategy<>();
            mapStrategy.setType(Prediction.class);

            String[] columns = new String[]{"date", "name", "pre", "mid", "post", "buy", "amount", "sell"};
            mapStrategy.setColumnMapping(columns);

            StatefulBeanToCsv<Prediction> csvContentFromBean = new StatefulBeanToCsvBuilder<Prediction>(writer)
                    .withEscapechar(CSVWriter.NO_ESCAPE_CHARACTER)
                    .withQuotechar(CSVWriter.NO_QUOTE_CHARACTER)
                    .withLineEnd(CSVWriter.RFC4180_LINE_END)
                    .withMappingStrategy(mapStrategy)
                    .withSeparator(CSV_SEP)
                    .build();
            csvContentFromBean.write(predictions);

        } catch (CsvException ex) {
            LOGGER.error("Error mapping Bean to CSV", ex);
        }
    }
}