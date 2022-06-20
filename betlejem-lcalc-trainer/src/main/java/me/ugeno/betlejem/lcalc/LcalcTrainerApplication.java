package me.ugeno.betlejem.lcalc;

import me.ugeno.betlejem.binance.training.DataSet;
import me.ugeno.betlejem.common.crypto.kafka.DataProviderKafka;
import me.ugeno.betlejem.common.enc.ObfuscatedPasswordsContextInitializer;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.annotation.PostConstruct;
import java.util.List;

@SpringBootApplication
public class LcalcTrainerApplication {
    /****************************************************
     * The following 2 settings are the most important. */

    public static final int PAST_INTERVALS = 96; // WARN (shared by eval component) how many intervals back (e.g. minutes) the RNN will be looking at to make predictions
    private static final int FILE_V = 11; // some arbitrary number to distinguish lately generated files easily and not run training on past data by mistake (requires manual adjusting of training script to align)
    private static final int CALCULATE_INTERVALS_FORWARD = 2; // how far in the future - how many intervals forward (e.g. minutes) - should the RNN try to predict the situation

    /****************************************************/

    private static final int SIM_NUMBER_OF_DAYS = 7; // just to check if amount of intervals forward produce satisfying result on some recent data set

    /****************************************************/

    private static final String DATASET_PAIR_NAME = DataSet.ETHUSDT_1m.TOPIC_PRICES;
    private static final String OUTPUT_NAME = prepareOutputName();
    public static final String BASE_PATH = "betlejem-neural-networks\\data\\crypto\\";

    static final int LIMIT_BEST_BALANCES_PER_INTERVAL = 10; // after each interval choose top results so far and drop the rest
    static final int BALANCE_SPLIT_AMOUNT = 1; // some beta feature (trading based on ratio of the pair in the wallet, currently using setting 1 which means 0 or 100% of each)
    static final double SIM_INIT_PRIMARY = 100; // e.g. USD or PLN or USDT/USDC
    public static final double LOSS_PERCENTAGE = 0.5; // MAX. allowed percentage loss, incl. fee 0.3%
    private static final int STARTING_OFFSET = 0; // how much data to take from Kafka (0 = all possible)
    private static final int MINUTES_PER_DAY = 60 * 24;

    // TODO:
    // Training app - Schedule new training when finished
    // Training app - Check if sim gave positive results
    // Training app - When new training input is ready send msg to training script
    // Training script - on message received run training
    // Training script - when finished, pick the latest from output dir and move to useful, then delete the dir
    // Evaluation script - load newest models

    /**
     * This applications generates RNN training data based on predictions stored in Kafka.
     */
    public static void main(String[] args) {
        System.out.printf("Symbol: %s%n", DATASET_PAIR_NAME);
        System.out.printf("Intervals forward: %d%n", CALCULATE_INTERVALS_FORWARD);
        SpringApplication app = new SpringApplication(LcalcTrainerApplication.class);
        app.addInitializers(new ObfuscatedPasswordsContextInitializer());
        app.setBannerMode(Banner.Mode.CONSOLE);
        app.run(args);
    }

    @PostConstruct
    void start() {
        Lcalc lcalc = new Lcalc(true);
        DataProviderKafka dataProviderKafka = new DataProviderKafka();
        List<String[]> inputDataset = dataProviderKafka.fetchAllFrom(DATASET_PAIR_NAME, STARTING_OFFSET, new PricesDeserializer());
        System.out.println("DATASET length: " + inputDataset.size());

        String decisionTableCsv = lcalc.calculateDecisionsTable(inputDataset, OUTPUT_NAME, DATASET_PAIR_NAME, CALCULATE_INTERVALS_FORWARD, BALANCE_SPLIT_AMOUNT);
        int simIntervalsAmount = MINUTES_PER_DAY * SIM_NUMBER_OF_DAYS;
        int pastIntervals = PAST_INTERVALS;
        int pricesStartOffset = inputDataset.size() - simIntervalsAmount;
        lcalc.runSimulation(decisionTableCsv, OUTPUT_NAME, pricesStartOffset, 100, pastIntervals, LOSS_PERCENTAGE);
        lcalc.prepareTrainingDataset(decisionTableCsv, OUTPUT_NAME, DATASET_PAIR_NAME, pastIntervals);

        System.out.println("Training data preparation finished.");
        System.exit(0); // TODO: call training script, e.g.: -d v02_ETHUSDT_1m.past016_forward005_000.csv_pp.csv
    }

    private static String prepareOutputName() {
        return String.format("v%02d_%s.past%03d_forward%03d",
                FILE_V, DATASET_PAIR_NAME, PAST_INTERVALS, CALCULATE_INTERVALS_FORWARD);
    }
}

