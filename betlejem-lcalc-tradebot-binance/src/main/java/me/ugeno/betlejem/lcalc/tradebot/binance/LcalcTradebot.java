package me.ugeno.betlejem.lcalc.tradebot.binance;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.sun.istack.NotNull;
import me.ugeno.betlejem.binance.prices.BinanceConnector;
import me.ugeno.betlejem.common.utils.BetlejemException;
import me.ugeno.betlejem.common.utils.BetlejemUtils;
import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiException;
import org.openapitools.client.Pair;
import org.openapitools.client.api.MarketApi;
import org.openapitools.client.api.TradeApi;
import org.openapitools.client.model.Account;
import org.openapitools.client.model.AccountBalances;
import org.openapitools.client.model.InlineResponse2002;
import org.openapitools.client.model.InlineResponse2003;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Created by alwi on 12/12/2021.
 * All rights reserved.
 */
@SuppressWarnings("Duplicates")
class LcalcTradebot implements BinanceConnector {
    private static final Logger LOG = LoggerFactory.getLogger(LcalcTradebot.class);
    private static final int RECV_WINDOW = 5000;

    static final int SCALE_FOR_DIV = 8;

    private final PairConfig pairConfig;
    private final String txRegistryJsonFile;

    private static final BigDecimal PACKAGE_VALUE_IN_SEC = new BigDecimal(50); // TODO: some proportion of current wealth, eg. 50% & max allowed = 2 txs, requires packaging adjustment for replacing past txs
    static final BigDecimal MIN_GAIN_DIRECT = new BigDecimal(0.085);
    static final BigDecimal MIN_GAIN_REVERSE = new BigDecimal(0.085);
    static final BigDecimal CERTAINTY_LVL_DIRECT = new BigDecimal(0.80);
    static final BigDecimal CERTAINTY_LVL_REVERSE = new BigDecimal(0.80);
    private static final BigDecimal CERTAINTY_PROFIT_OVERRIDE_THRESHOLD = new BigDecimal(1.0); // enabled at exact 100%

    static final BigDecimal SIMULATED_INIT_BALANCE_PRI = new BigDecimal("1"); // init wealth value - used for sim if not fetched from real account
    static final BigDecimal SIMULATED_INIT_BALANCE_SEC = new BigDecimal("3500");
    private static final BigDecimal SIMULATED_COMMISSION_FEE = new BigDecimal(0.004);

    static final String OP_DIRECT = "DIRECT"; // e.g. for symbol ETHUSDC, direct means swap ETH => USDC
    static final String OP_REVERSE = "REVERSE";

    private static final String OP_PASS = "HODL";
    private static final String OP_INCONCLUSIVE = "----";

    private static final String SIDE_SELL = "SELL";
    private static final String SIDE_BUY = "BUY";
    private static final String ORDER_TYPE_MARKET = "MARKET";

    private static final String SWAP_INPUT_PRICE_IN_SEC = "priceInSec";
    private static final String SWAP_INPUT_PACKAGE_VALUE_IN_SEC = "packageValueInSec";

    private BigDecimal packageValueInSec;
    private BigDecimal certaintyLvlDirect;
    private BigDecimal certaintyLvlReverse;
    private BigDecimal certaintyProfitOverrideThreshold;
    private BigDecimal minGainDirect;
    private BigDecimal minGainReverse;

    private BigDecimal balancePri;
    private BigDecimal balanceSec;
    private BigDecimal initWealthInSec;
    private BigDecimal initPrice;
    private BigDecimal recentPrice;

    private MarketApi marketApi;
    private TradeApi tradeApi;

    private boolean initialised = false;
    private TxRegistry txRegistry = new TxRegistry(new ArrayList<>());

    private boolean simulation;
    private boolean online;
    private BigDecimal latestPriceAtSimTime;

    private BigDecimal wealthChange;
    private BigDecimal priceChange;

    private boolean txRegistryUpdated = false;
    private OpPriority opPriority;
    private int txAmount = 0; // just a stat

    private static Map<String, ReentrantLock> rxRegistrySaveLocks = new HashMap<>();
    private boolean switchedSignal;

    LcalcTradebot(PairConfig pairConfig) {
        String pairName = pairConfig.getPairName();

        this.pairConfig = pairConfig;
        this.txRegistryJsonFile = String.format("txRegistry_%s.json", pairName);

        this.balancePri = SIMULATED_INIT_BALANCE_PRI;
        this.balanceSec = SIMULATED_INIT_BALANCE_SEC;
        this.initWealthInSec = BigDecimal.ZERO;
        this.initPrice = BigDecimal.ZERO;
        this.wealthChange = BigDecimal.ZERO;
        this.priceChange = BigDecimal.ZERO;
        this.opPriority = OpPriority.SELL_FIRST;
        this.simulation = false;
        this.online = true;

        this.packageValueInSec = PACKAGE_VALUE_IN_SEC;
        this.certaintyLvlDirect = CERTAINTY_LVL_DIRECT;
        this.certaintyLvlReverse = CERTAINTY_LVL_REVERSE;
        this.certaintyProfitOverrideThreshold = CERTAINTY_PROFIT_OVERRIDE_THRESHOLD;
        this.minGainDirect = MIN_GAIN_DIRECT;
        this.minGainReverse = MIN_GAIN_REVERSE;

        rxRegistrySaveLocks.putIfAbsent(pairName, new ReentrantLock());

        this.switchedSignal = false;
    }

    LcalcTradebot(
            PairConfig pairConfig,
            boolean simulation,
            BigDecimal certaintyLvlDirect,
            BigDecimal certaintyLvlReverse,
            BigDecimal minGainDirect,
            BigDecimal minGainReverse,
            BigDecimal simulatedInitPri,
            BigDecimal simulatedInitSec,
            BigDecimal latestPriceForSim) {

        this(pairConfig);
        this.simulation = simulation;
        this.latestPriceAtSimTime = latestPriceForSim;
        this.online = false;

        this.balancePri = simulatedInitPri;
        this.balanceSec = simulatedInitSec;
        this.certaintyLvlDirect = certaintyLvlDirect;
        this.certaintyLvlReverse = certaintyLvlReverse;
        this.minGainDirect = minGainDirect;
        this.minGainReverse = minGainReverse;
    }

    private BigDecimal recalculateWealth(BigDecimal pricePriToSec) {
        return scaled(balanceSec.add(balancePri.multiply(pricePriToSec)), pairConfig.getScaleSec());
    }

    synchronized void receive(String date, double sell, double pass, double buy, double intervalOpenPrice) {
        if (mainTxProcessing()) LOG.debug("received new data");

        double temp;
        if (switchedSignal) {
            temp = sell;

            sell = buy;
            buy = temp;
        }
        String operation = calculateOperation(sell, pass, buy);

        BigDecimal priceInSec = new BigDecimal(intervalOpenPrice);

        try {
            boolean override = new BigDecimal(Math.max(sell, buy)).compareTo(certaintyProfitOverrideThreshold) >= 0;

            BigDecimal wealthSecBeforeTx = recalculateWealth(priceInSec);
            if (!initialised) {
                init(wealthSecBeforeTx, priceInSec);
                initialised = true;
            }

            if (mainTxProcessing()) {
                txRegistry = loadTxRegistry();

                if (online) {
                    updateAccountBalance(this.tradeApi);
                }
            }

            BigDecimal actualPrice = handleOperation(priceInSec, operation, override);

            this.recentPrice = actualPrice;
            BigDecimal wealthSecAfterTx = recalculateWealth(priceInSec);

            if (initWealthInSec.compareTo(BigDecimal.ZERO) != 0) {
                wealthChange = scaled((wealthSecAfterTx.subtract(initWealthInSec)).divide(initWealthInSec, SCALE_FOR_DIV, RoundingMode.HALF_EVEN).multiply(BigDecimal.valueOf(100L)), pairConfig.getScaleSec());
            } else {
                wealthChange = BigDecimal.ZERO;
            }
            priceChange = scaled((actualPrice.subtract(initPrice)).divide(initPrice, SCALE_FOR_DIV, RoundingMode.HALF_EVEN).multiply(BigDecimal.valueOf(100L)), pairConfig.getScaleSec());

            printStatus(date, sell, pass, buy, priceInSec.doubleValue(), operation, balanceSec.doubleValue(), balancePri.doubleValue(), wealthSecAfterTx.doubleValue(), wealthChange.doubleValue(), actualPrice.doubleValue(), priceChange.doubleValue());
        } catch (Exception e) {
            BetlejemUtils.logError(e);
        }

        if (mainTxProcessing() && txRegistryUpdated) {
            saveTxRegistry();
            txRegistryUpdated = false;
        }
    }

    private TxRegistry loadTxRegistry() {
        File file = new File(txRegistryJsonFile);
        if (file.exists()) {
            try (FileReader fileReader = new FileReader(file)) {
                while (rxRegistrySaveLocks.get(pairConfig.getPairName()).isLocked()) { // locking for saving should be rare and quick...
                    Thread.yield();
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        BetlejemUtils.logError(e);
                    }
                }
                txRegistry = getObjectMapper().readValue(fileReader, TxRegistry.class);

                if (mainTxProcessing()) LOG.debug(String.format("Loaded transactions (%d): %s", txRegistry.size(), txRegistry));
                return txRegistry;
            } catch (IOException e) {
                LOG.error("Issue with loading file " + txRegistryJsonFile);
                BetlejemUtils.logError(e);
            }
        }

        return new TxRegistry(new ArrayList<>());
    }

    private void saveTxRegistry() {
        if (mainTxProcessing()) {
            LOG.info("Updating txRegistry in {}.", txRegistryJsonFile);
            try (FileWriter fileWriter = new FileWriter(txRegistryJsonFile, false)) {
                rxRegistrySaveLocks.get(pairConfig.getPairName()).lock();
                try {
                    Thread.sleep(1000); // give some time to all the readers to finish
                } catch (InterruptedException e) {
                    BetlejemUtils.logError(e);
                }
                try (BufferedWriter bufferedWriter = new BufferedWriter(fileWriter)) {
                    String json = getObjectMapper().writeValueAsString(txRegistry);
                    bufferedWriter.write(json);
                }
            } catch (IOException e) {
                LOG.error("Issue with writing to file " + txRegistryJsonFile + ", txRegistry: " + txRegistry);
                BetlejemUtils.logError(e);
            } finally {
                rxRegistrySaveLocks.get(pairConfig.getPairName()).unlock();
            }
        }
    }

    @NotNull
    private String calculateOperation(double sell, double pass, double buy) {
        double lesserCertainty = Math.min(certaintyLvlReverse.doubleValue(), certaintyLvlDirect.doubleValue());
        switch (opPriority) {
            case SELL_FIRST:
                return sell > certaintyLvlReverse.doubleValue() ? OP_REVERSE
                        : buy > certaintyLvlDirect.doubleValue() ? OP_DIRECT
                        : pass > lesserCertainty ? OP_PASS
                        : OP_INCONCLUSIVE;
            case BUY_FIRST:
                return buy > certaintyLvlDirect.doubleValue() ? OP_DIRECT
                        : sell > certaintyLvlReverse.doubleValue() ? OP_REVERSE
                        : pass > lesserCertainty ? OP_PASS
                        : OP_INCONCLUSIVE;
            default:
                return pass > lesserCertainty ? OP_PASS
                        : sell > certaintyLvlReverse.doubleValue() ? OP_REVERSE
                        : buy > certaintyLvlDirect.doubleValue() ? OP_DIRECT
                        : OP_INCONCLUSIVE;
        }
    }

    private BigDecimal handleOperation(BigDecimal priceInSec, String operation, boolean override) throws ApiException {
        if (mainTxProcessing()) LOG.debug("handle operation");

        return handleTransaction(operation, priceInSec, override);
    }

    void updateAccountBalance(TradeApi api) {
        if (mainTxProcessing()) LOG.debug("updating account balance");
        ApiClient apiClient = api.getApiClient();
        long recvWindow = RECV_WINDOW;
        long timestamp = System.currentTimeMillis();

        List<Pair> localVarQueryParams = new ArrayList<>();
        localVarQueryParams.addAll(apiClient.parameterToPair("recvWindow", recvWindow));
        localVarQueryParams.addAll(apiClient.parameterToPair("timestamp", timestamp));

        try {
            String signature = getSignature(localVarQueryParams);

            Account response = api.apiV3AccountGet(timestamp, signature, recvWindow);
            String keySec = pairConfig.getSymbolSec();
            String keyPri = pairConfig.getSymbolPri();

            Map<String, List<AccountBalances>> balances = response.getBalances().stream()
                    .filter(b -> b.getAsset().equals(keyPri) || b.getAsset().equals(keySec))
                    .peek(b -> {
                        if (mainTxProcessing()) LOG.debug(b.getAsset() + ": " + b.getFree() + " (" + b.getLocked() + ")");
                    })
                    .collect(Collectors.groupingBy(AccountBalances::getAsset));

            balanceSec = balances.get(keySec).stream().findFirst().map(b -> new BigDecimal(b.getFree())).orElseThrow(() -> new BetlejemException(String.format("Could not parse %s balance", keySec)));
            balancePri = balances.get(keyPri).stream().findFirst().map(b -> new BigDecimal(b.getFree())).orElseThrow(() -> new BetlejemException(String.format("Could not parse %s balance", keyPri)));
        } catch (IOException | ApiException e) {
            BetlejemUtils.logError(e);
        }
    }

    @SuppressWarnings("Duplicates")
    private BigDecimal handleTransaction(String operation, BigDecimal priceInSec, boolean override) throws ApiException {
        if (mainTxProcessing()) {
            if (operation.equals(OP_DIRECT) || operation.equals(OP_REVERSE)) {
                LOG.info(pairConfig.getPairName() + String.format(" > *** Incoming operation: %s ***", operation));
            }
        }

        Optional<BigDecimal> ask = Optional.empty();
        Optional<BigDecimal> bid = Optional.empty();

        if (mainTxProcessing() && online) {
            InlineResponse2002 depth = marketApi.apiV3DepthGet(pairConfig.getPairName(), 5);
            ask = depth.getAsks().stream().findFirst().map(a -> new BigDecimal(a.get(0)));
            bid = depth.getBids().stream().findFirst().map(a -> new BigDecimal(a.get(0)));
        }

        Function<Map<String, BigDecimal>, BigDecimal> buyPri = this::txBuyPri;
        Function<Map<String, BigDecimal>, BigDecimal> buySec = this::txBuySec;

        Function<Map<String, BigDecimal>, BigDecimal> swapFunction;
        String counterOperation;
        BigDecimal availableBalanceInSec;
        // Update price pri->sec (eg. ETHUSDC) used later on to current value
        if (OP_REVERSE.equals(operation)) {
            priceInSec = ask.orElse(simulation || !online ? priceInSec : null);
            availableBalanceInSec = balanceSec;
            swapFunction = buyPri;
            counterOperation = OP_DIRECT;
        } else if (OP_DIRECT.equals(operation)) {
            priceInSec = bid.orElse(simulation || !online ? priceInSec : null);
            availableBalanceInSec = balancePri.multiply(priceInSec == null ? BigDecimal.ZERO : priceInSec);
            swapFunction = buySec;
            counterOperation = OP_REVERSE;
            if (mainTxProcessing()) {
                LOG.info(pairConfig.getPairName() + String.format(" > Current balance of %s %s is worth %s %s", scaled(balancePri, pairConfig.getScalePri()), pairConfig.getSymbolPri(), scaled(availableBalanceInSec, pairConfig.getScaleSec()), pairConfig.getSymbolSec()));
            }
        } else if (OP_INCONCLUSIVE.equals(operation) || OP_PASS.equals(operation)) {
            return priceInSec;
        } else {
            throw new BetlejemException("Operation not supported: " + operation);
        }

        if (priceInSec == null) {
            throw new BetlejemException(String.format("Could not retrieve price for operation: %s", operation));
        }

        Map<String, BigDecimal> swapInput = Map.of(SWAP_INPUT_PRICE_IN_SEC, priceInSec, SWAP_INPUT_PACKAGE_VALUE_IN_SEC, packageValueInSec);

        List<Tx> pastTxToRemove = new ArrayList<>();
        BigDecimal amountSwappedInSec;
        List<Tx> pastCounterTxesWithBestProfit = getPastOperationsSortedFromBiggestGainWithProfit(priceInSec, counterOperation);

        // Phase I: tracked swap with profit
        Optional<Tx> pastTxMaybe = pastCounterTxesWithBestProfit.stream().findFirst();
        if (pastTxMaybe.isPresent()) {
            Tx pastTx = pastTxMaybe.get();
            BigDecimal pastTxTradedSec = pastTx.getTradedSec();
            if (mainTxProcessing()) {
                LOG.info(pairConfig.getPairName() + String.format(" >> Found past %s for current counter %s with gain: %+5.3f for package value of: %.2f %s at price of %.2f %s",
                        pastTx.getOperation(), operation, pastTx.getGainForPrice(priceInSec).doubleValue(), pastTxTradedSec, pairConfig.getSymbolSec(), pastTx.getPriceInSec(), pairConfig.getSymbolSec()));
            }

            amountSwappedInSec = swapFunction.apply(swapInput);
            if (amountSwappedInSec.compareTo(BigDecimal.ZERO) > 0) {
                storeTxInfo(operation, priceInSec, amountSwappedInSec);
                availableBalanceInSec = availableBalanceInSec.subtract(amountSwappedInSec);
                pastTxToRemove.add(pastTx);

                if (mainTxProcessing()) {
                    LOG.info(pairConfig.getPairName() + String.format(" >> Executed %s at price %s", operation, scaled(priceInSec, pairConfig.getScaleSec())));
                    LOG.info(pairConfig.getPairName() + String.format(" >> Removing %s at price %s", pastTx.getOperation(), scaled(pastTx.getPriceInSec(), pairConfig.getScaleSec())));
                    LOG.info(pairConfig.getPairName() + String.format(" >> %s with profit made", operation));
                }
            } else {
                if (mainTxProcessing()) {
                    LOG.info(pairConfig.getPairName() + " >>> Swap function returned 0 amount traded...");
                }
            }
        }

        // Check for leftovers
        List<Tx> pastCounterTxSortedByGainDesc = getPastOperationsSortedByGain(priceInSec, counterOperation, Comparator.reverseOrder());
        pastCounterTxSortedByGainDesc.removeAll(pastTxToRemove);
        Optional<Tx> pastCounterTxWithBiggestGain = pastCounterTxSortedByGainDesc.stream().findFirst();
        if (mainTxProcessing()) {
            LOG.info(pairConfig.getPairName() + String.format(" > Past %s tracked: %d [%s]",
                    counterOperation,
                    pastCounterTxSortedByGainDesc.size(),
                    pastCounterTxSortedByGainDesc.stream()
                            .map(tx1 -> scaled(tx1.getPriceInSec(), pairConfig.getScaleSec()).toString())
                            .collect(Collectors.joining(", "))));

            if (pastCounterTxWithBiggestGain.isPresent()) {
                Tx pastTx = pastCounterTxWithBiggestGain.get();
                LOG.info(pairConfig.getPairName() + String.format(" > Delta to current best tracked: %5.3f for past price %8.2f vs current %8.2f and package %8.2f",
                        pastTx.getGainForPrice(priceInSec).doubleValue(), pastTx.getPriceInSec(), priceInSec.doubleValue(), pastTx.getTradedSec()));
            }
        }

        // Phase II: udrożnianie skrzepów...
        List<Tx> pastCounterTxSortedByGainAsc = getPastOperationsSortedByGain(priceInSec, counterOperation, Comparator.naturalOrder());
        pastCounterTxSortedByGainAsc.removeAll(pastTxToRemove);
        Optional<Tx> pastCounterTxWithLeastGain = pastCounterTxSortedByGainAsc.stream().findFirst();
        if (override) {
            if (pastCounterTxWithLeastGain.isPresent()) {
                amountSwappedInSec = swapFunction.apply(swapInput);
                if (amountSwappedInSec.compareTo(BigDecimal.ZERO) > 0) {
                    Tx newTx = storeTxInfo(operation, priceInSec, amountSwappedInSec);
                    availableBalanceInSec = availableBalanceInSec.subtract(amountSwappedInSec);
                    Tx pastTx = pastCounterTxWithLeastGain.get();
                    pastTxToRemove.add(pastTx);

                    if (mainTxProcessing()) {
                        LOG.info(pairConfig.getPairName() + String.format(" >> Override due to high certainty level. Liquidating tangling worst operation made at %8.2f. Allowing new swap with loss: %8.3f...",
                                pastTx.getPriceInSec(), pastTx.getGainForPrice(priceInSec).doubleValue()));
                        LOG.info(pairConfig.getPairName() + String.format(" >> Executed %s at price %s", newTx.getOperation(), scaled(priceInSec, pairConfig.getScaleSec())));
                        LOG.info(pairConfig.getPairName() + String.format(" >> Removing %s at price %s", pastTx.getOperation(), scaled(pastTx.getPriceInSec(), pairConfig.getScaleSec())));
                    }
                } else {
                    if (mainTxProcessing()) {
                        LOG.info(pairConfig.getPairName() + " >>> Swap function returned 0 amount traded...");
                    }
                }
            }
        }

        // Phase III: additional swap if balance allows
        Optional<BigDecimal> lockedTotalWorthInSec = pastCounterTxSortedByGainDesc.stream()
                .map(Tx::getTradedSec)
                .reduce(BigDecimal::add);

        if (lockedTotalWorthInSec.isPresent()) {
            BigDecimal totalLockedByCounterTxesInSec = lockedTotalWorthInSec.get();
            BigDecimal availableBalanceValueInSec = availableBalanceInSec.compareTo(totalLockedByCounterTxesInSec) > 0 ? availableBalanceInSec.subtract(totalLockedByCounterTxesInSec) : BigDecimal.ZERO;

            if (availableBalanceValueInSec.compareTo(pairConfig.minOrderInSec()) >= 0) {
                if (mainTxProcessing()) {
                    LOG.info(pairConfig.getPairName() + String.format(" >> %5.2f of %5.2f are locked because of past %s. Using available funds (value: %5.2f) for new swap %s...",
                            totalLockedByCounterTxesInSec, availableBalanceInSec, counterOperation, availableBalanceValueInSec, operation));
                }

                amountSwappedInSec = swapFunction.apply(swapInput);
                if (amountSwappedInSec.compareTo(BigDecimal.ZERO) > 0) {
                    Tx newTx = storeTxInfo(operation, priceInSec, amountSwappedInSec);

                    if (mainTxProcessing()) {
                        LOG.info(pairConfig.getPairName() + String.format(" >> Executed %s at price %s", newTx.getOperation(), scaled(priceInSec, pairConfig.getScaleSec())));
                    }
                } else {
                    if (mainTxProcessing()) {
                        LOG.info(pairConfig.getPairName() + " >>> Swap function returned 0 amount traded...");
                    }
                }
            } else {
                if (mainTxProcessing()) {
                    LOG.info(pairConfig.getPairName() + String.format(" >> No free funds at the moment - available balance value in %s = %5.2f (%5.2f locked). Pending to gain more to unlock past funds...",
                            pairConfig.getSymbolSec(), availableBalanceValueInSec.doubleValue(), totalLockedByCounterTxesInSec.doubleValue()));
                }
            }
        } else {
            if (mainTxProcessing()) {
                LOG.info(pairConfig.getPairName() + String.format(" >> No previous %s found. Allowing new swap...", counterOperation));
            }

            amountSwappedInSec = swapFunction.apply(swapInput);
            if (amountSwappedInSec.compareTo(BigDecimal.ZERO) > 0) {
                Tx newTx = storeTxInfo(operation, priceInSec, amountSwappedInSec);

                if (mainTxProcessing()) {
                    LOG.info(pairConfig.getPairName() + String.format(" >> Executed %s at price %s", newTx.getOperation(), scaled(priceInSec, pairConfig.getScaleSec())));
                }
            } else {
                if (mainTxProcessing()) {
                    LOG.info(pairConfig.getPairName() + " >>> Swap function returned 0 amount traded...");
                }
            }
        }

        if (!pastTxToRemove.isEmpty()) {
            txRegistry.removeAll(pastTxToRemove);
            txRegistryUpdated = true;
        }
        return priceInSec;
    }

    private boolean mainTxProcessing() {
        return !simulation;
    }

    @NotNull
    private Tx storeTxInfo(String operation, BigDecimal priceInSec, BigDecimal amountOfSec) {
        Tx newTx = new Tx(operation, amountOfSec, priceInSec, pairConfig.getScalePri(), pairConfig.getScaleSec());
        txRegistry.add(newTx);
        txRegistryUpdated = true;
        return newTx;
    }

    @NotNull
    private List<Tx> getPastOperationsSortedFromBiggestGainWithProfit(BigDecimal currentPriceInSec, String pastOp) {
        return txRegistry.stream()
                .filter(tx -> tx.getOperation().equals(pastOp))
                .filter(getTxAllowedDiff(currentPriceInSec, pastOp))
                .sorted(Comparator.comparing(tx -> tx.getGainForPrice(currentPriceInSec), Comparator.reverseOrder())) // from the best
                .collect(Collectors.toList());
    }

    @NotNull
    private List<Tx> getPastOperationsSortedByGain(BigDecimal currentPriceInSec, String op, Comparator<BigDecimal> keyComparator) {
        return txRegistry.stream()
                .filter(tx -> tx.getOperation().equals(op))
                .sorted(Comparator.comparing(tx -> tx.getGainForPrice(currentPriceInSec), keyComparator)) // from the biggest gain or least lose
                .collect(Collectors.toList());
    }

    private Predicate<Tx> getTxAllowedDiff(BigDecimal currentPriceInSec, String pastOp) {
        return tx -> {
            if (pastOp.equals(OP_DIRECT)) {
                return tx.getGainForPrice(currentPriceInSec).compareTo(minGainReverse) >= 0;
            } else if (pastOp.equals(OP_REVERSE)) {
                return tx.getGainForPrice(currentPriceInSec).compareTo(minGainDirect) >= 0;
            } else {
                throw new BetlejemException("Operation not supported: " + pastOp);
            }
        };
    }

    private void printStatus(String date, double sell, double pass, double buy, double priceInSec, String operation, double balanceSec, double balancePri, double wealthSec, double wealthDelta, double actualPrice, double priceChange) {
        if (mainTxProcessing()) {
            LOG.info(String.format("%s %s %9s %4.2f  PASS %4.2f  %9s %4.2f  - PRICE: %7.2f  <=== %8s using %8.2f  |  %5s: %8.2f %5s: %10.6f | w. (%s): %8.2f (w. diff: %+6.2f%% | price diff: %+6.2f%%)",
                    date,
                    switchedSignal ? "-*-" : "---",
                    OP_REVERSE, sell,
                    pass,
                    OP_DIRECT, buy,
                    priceInSec,
                    operation,
                    actualPrice, pairConfig.getSymbolSec(),
                    balanceSec, pairConfig.getSymbolPri(),
                    balancePri, pairConfig.getSymbolSec(),
                    wealthSec,
                    wealthDelta,
                    priceChange));
        }
    }

    private BigDecimal txBuySec(Map<String, BigDecimal> swapInput) {
        BigDecimal priceInSec = swapInput.get(SWAP_INPUT_PRICE_IN_SEC);
        BigDecimal packageValueInSec = swapInput.get(SWAP_INPUT_PACKAGE_VALUE_IN_SEC);
        BigDecimal packageValueInPri = packageValueInSec.divide(priceInSec, SCALE_FOR_DIV, RoundingMode.HALF_EVEN);

        String side = SIDE_SELL;
        String symbol = pairConfig.getPairName();

        BigDecimal minimumValueInPriBasedOnMinOrderInSec = pairConfig.minOrderInSec().divide(priceInSec, SCALE_FOR_DIV, RoundingMode.UP);
        if (balancePri.compareTo(minimumValueInPriBasedOnMinOrderInSec) < 0) {
            if (mainTxProcessing()) {
                LOG.info(pairConfig.getPairName() + String.format(" >>> %s => %s: Too little balance: %s %s value", pairConfig.getSymbolPri(), pairConfig.getSymbolSec(), scaled(balancePri, pairConfig.getScaleSec()), pairConfig.getSymbolPri()));
            }
            return BigDecimal.ZERO;
        }

        if (packageValueInPri.compareTo(minimumValueInPriBasedOnMinOrderInSec) < 0) {
            packageValueInPri = minimumValueInPriBasedOnMinOrderInSec;
        }

        if (packageValueInPri.compareTo(balancePri) > 0 || (balancePri.subtract(packageValueInPri).multiply(priceInSec)).compareTo(pairConfig.minOrderInSec()) < 0) {
            packageValueInPri = balancePri;
        }

        BigDecimal packageValueInPriMinusCommission = packageValueInPri.subtract(packageValueInPri.multiply(SIMULATED_COMMISSION_FEE));

        try {
            // how much pri to sell for sec at market price
            postOrderQty(symbol, tradeApi, side, packageValueInPriMinusCommission, pairConfig.getScalePri());
        } catch (ApiException | IOException e) {
            BetlejemUtils.logError(e);
            return BigDecimal.ZERO;
        }

        if (mainTxProcessing()) {
            LOG.info(pairConfig.getPairName() + String.format(" >>> applying %s %s tx: %7.4f %s => %7.2f %s", side, symbol, packageValueInPri, pairConfig.getSymbolPri(), packageValueInSec, pairConfig.getSymbolSec()));
        }

        BigDecimal packageValueInSecMinusCommission = packageValueInPriMinusCommission.multiply(priceInSec);
        balancePri = scaled(balancePri.subtract(packageValueInPri), pairConfig.getScalePri());
        balanceSec = scaled(balanceSec.add(packageValueInSecMinusCommission), pairConfig.getScaleSec());

        return packageValueInSec;
    }

    private BigDecimal txBuyPri(Map<String, BigDecimal> swapInput) {
        BigDecimal priceInSec = swapInput.get(SWAP_INPUT_PRICE_IN_SEC);
        BigDecimal packageValueInSec = swapInput.get(SWAP_INPUT_PACKAGE_VALUE_IN_SEC);

        String side = SIDE_BUY;
        String symbol = pairConfig.getPairName();

        if (balanceSec.compareTo(pairConfig.minOrderInSec()) < 0) {
            if (mainTxProcessing()) {
                LOG.info(pairConfig.getPairName() + String.format(" >>> %s => %s: Too little balance: %s %s value", pairConfig.getSymbolSec(), pairConfig.getSymbolPri(), scaled(balanceSec, pairConfig.getScaleSec()), pairConfig.getSymbolSec()));
            }
            return BigDecimal.ZERO;
        }

        BigDecimal packageValueInPri;
        if (packageValueInSec.compareTo(pairConfig.minOrderInSec()) < 0) {
            packageValueInSec = pairConfig.minOrderInSec();
        }

        if (packageValueInSec.compareTo(balanceSec) > 0 || balanceSec.subtract(packageValueInSec).compareTo(pairConfig.minOrderInSec()) < 0) {
            packageValueInSec = balanceSec;
        }
        packageValueInPri = packageValueInSec.divide(priceInSec, SCALE_FOR_DIV, RoundingMode.HALF_EVEN);

        try {
            // how much sec to spend for buying pri at market price
            postOrderQuoteQty(symbol, tradeApi, side, packageValueInSec, pairConfig.getScaleSec());
        } catch (ApiException | IOException e) {
            BetlejemUtils.logError(e);
            return BigDecimal.ZERO;
        }

        if (mainTxProcessing()) {
            LOG.info(pairConfig.getPairName() + String.format(" >>> applying %s %s tx: %7.2f %s => %7.4f %s", side, symbol, packageValueInSec, pairConfig.getSymbolSec(), packageValueInPri, pairConfig.getSymbolPri()));
        }

        BigDecimal packageValueInPriMinusCommission = packageValueInPri.subtract(packageValueInPri.multiply(SIMULATED_COMMISSION_FEE));
        balancePri = scaled(balancePri.add(packageValueInPriMinusCommission), pairConfig.getScalePri());
        balanceSec = scaled(balanceSec.subtract(packageValueInSec), pairConfig.getScaleSec());

        return packageValueInSec;
    }

    private void postOrderQuoteQty(String symbol, TradeApi tradeApi, String side, BigDecimal quoteOrderQty, int scale) throws IOException, ApiException {
        if (mainTxProcessing() && online) {
            ApiClient localVarApiClient = tradeApi.getApiClient();
            String type = ORDER_TYPE_MARKET;
            long recvWindow = RECV_WINDOW;
            long timestamp = System.currentTimeMillis();
            String quoteOrderQtyStr = quoteOrderQty.setScale(scale, RoundingMode.DOWN).toString();

            List<Pair> localVarQueryParams = new ArrayList<>();
            localVarQueryParams.addAll(localVarApiClient.parameterToPair("symbol", symbol));
            localVarQueryParams.addAll(localVarApiClient.parameterToPair("side", side));
            localVarQueryParams.addAll(localVarApiClient.parameterToPair("type", type));
            localVarQueryParams.addAll(localVarApiClient.parameterToPair("quoteOrderQty", quoteOrderQtyStr));
            localVarQueryParams.addAll(localVarApiClient.parameterToPair("recvWindow", recvWindow));
            localVarQueryParams.addAll(localVarApiClient.parameterToPair("timestamp", timestamp));
            String signature = getSignature(localVarQueryParams);

            tradeApi.apiV3OrderPost(symbol, side, type, timestamp, signature, null, null, quoteOrderQtyStr, null, null, null, null, null, recvWindow);

            LOG.info(pairConfig.getPairName() + String.format(" >>>> submitted %s %s tx", side, symbol));
        }

        txAmount++;
    }

    void postOrderQty(String symbol, TradeApi tradeApi, String side, BigDecimal quantity, int scale) throws IOException, ApiException {
        if (mainTxProcessing() && online) {
            ApiClient localVarApiClient = tradeApi.getApiClient();
            String type = ORDER_TYPE_MARKET;
            long recvWindow = RECV_WINDOW;
            long timestamp = System.currentTimeMillis();
            String quantityStr = quantity.setScale(scale, RoundingMode.DOWN).toString();

            List<Pair> localVarQueryParams = new ArrayList<>();
            localVarQueryParams.addAll(localVarApiClient.parameterToPair("symbol", symbol));
            localVarQueryParams.addAll(localVarApiClient.parameterToPair("side", side));
            localVarQueryParams.addAll(localVarApiClient.parameterToPair("type", type));
            localVarQueryParams.addAll(localVarApiClient.parameterToPair("quantity", quantityStr));
            localVarQueryParams.addAll(localVarApiClient.parameterToPair("recvWindow", recvWindow));
            localVarQueryParams.addAll(localVarApiClient.parameterToPair("timestamp", timestamp));
            String signature = getSignature(localVarQueryParams);

            tradeApi.apiV3OrderPost(symbol, side, type, timestamp, signature, null, quantityStr, null, null, null, null, null, null, recvWindow);

            LOG.info(pairConfig.getPairName() + String.format(" >>>> submitted %s %s tx", side, symbol));
        }

        txAmount++;
    }

    private void init(BigDecimal wealthInSec, BigDecimal initPrice) throws IOException {
        if (mainTxProcessing()) {
            LOG.info("Initializing...");
        }

        if (simulation) { // for simulation & auto-tune...
            // Prepare some artificial symmetrical past transactions as constraints at simulation entry price

            // load existing tx-es
            TxRegistry existingTxes = loadTxRegistry();

            // check worst historical DIRECT for current price, e.g. ETH sold while it was cheap (comparing to latest price at sim. time)
            Optional<BigDecimal> worstDirect = existingTxes.stream()
                    .filter(tx -> tx.getOperation().equals(OP_DIRECT))
                    .map(Tx::getPriceInSec).min(Comparator.naturalOrder());

            // check worst historical REVERSE for current price, e.g. ETH bought while it was expensive (comparing to latest price at sim. time)
            Optional<BigDecimal> worstReverse = existingTxes.stream()
                    .filter(tx -> tx.getOperation().equals(OP_REVERSE))
                    .map(Tx::getPriceInSec).max(Comparator.naturalOrder());

            // recreate them relatively to init price (past at simulation entry)
            do {
                worstDirect.ifPresent(worst -> txRegistry.add(new Tx(OP_REVERSE, worst.add(initPrice.subtract(latestPriceAtSimTime)), packageValueInSec, pairConfig.getScalePri(), pairConfig.getScaleSec())));
                worstReverse.ifPresent(worst -> txRegistry.add(new Tx(OP_DIRECT, worst.add(initPrice.subtract(latestPriceAtSimTime)), packageValueInSec, pairConfig.getScalePri(), pairConfig.getScaleSec())));
            } while (txRegistry.size() > 0 && txRegistry.stream().map(Tx::getPriceInSec).reduce(BigDecimal::add).map(totalPastTxValue -> totalPastTxValue.compareTo(wealthInSec) < 0).orElse(false));
        }

        marketApi = prepareBinanceClientForMarketApi();
        tradeApi = prepareBinanceClientForTradeApi();

        this.initPrice = initPrice;
        initWealthInSec = wealthInSec;

        if (mainTxProcessing() && online) {
            try {
                updateAccountBalance(tradeApi);

                InlineResponse2003 avgPriceResponse = marketApi.apiV3AvgPriceGet(pairConfig.getPairName());
                String avgPrice = avgPriceResponse.getPrice();
                if (mainTxProcessing()) LOG.info(String.format("Avg price received: %s", avgPrice));

                this.initPrice = new BigDecimal(avgPrice);
                initWealthInSec = recalculateWealth(this.initPrice);
            } catch (ApiException e) {
                BetlejemUtils.logError(e);
            }
        }

        if (mainTxProcessing()) LOG.info(String.format("Init price: %8.2f", this.initPrice.doubleValue()));
        if (mainTxProcessing()) LOG.info(String.format("Init wealth: %8.2f", initWealthInSec.doubleValue()));
    }

    private ObjectMapper getObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.registerModule(new ParameterNamesModule(JsonCreator.Mode.PROPERTIES));
        return objectMapper;
    }

    static BigDecimal scaled(BigDecimal subtract, int balanceScale) {
        return subtract.setScale(balanceScale, RoundingMode.HALF_EVEN);
    }

    BigDecimal getWealth() {
        return recalculateWealth(recentPrice);
    }

    BigDecimal getWealthChange() {
        return wealthChange;
    }

    BigDecimal getPriceChange() {
        return priceChange;
    }

    BigDecimal getBalancePri() {
        return balancePri;
    }

    BigDecimal getBalanceSec() {
        return balanceSec;
    }

    int getTxAmount() {
        return txAmount;
    }

    synchronized void updateConfig(TradebotConfig config) {
        LOG.info("      {} Updating config with new values: {}", pairConfig.getPairName(), config);

        certaintyLvlDirect = config.getCertaintyLvlDirect();
        certaintyLvlReverse = config.getCertaintyLvlDirect();
        minGainDirect = config.getMinGainDirect();
        minGainReverse = config.getMinGainReverse();
    }

    @SuppressWarnings("SameParameterValue")
    void setOnline(boolean online) {
        this.online = online;
    }
}
