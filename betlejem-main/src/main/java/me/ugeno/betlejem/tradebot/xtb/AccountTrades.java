package me.ugeno.betlejem.tradebot.xtb;

import pro.xstore.api.message.codes.TRADE_OPERATION_CODE;
import pro.xstore.api.message.records.TradeRecord;
import pro.xstore.api.message.response.TradesResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.math.RoundingMode.HALF_EVEN;
import static me.ugeno.betlejem.common.utils.xtb.BetlejemXtbConstants.PRICE_SCALE;

/**
 * Created by alwi on 03/03/2021.
 * All rights reserved.
 */
public class AccountTrades {
    private List<TradeRecord> tradeRecords;
    private List<TradeRecord> openPositions;
    private List<TradeRecord> pendingOrders;

    AccountTrades(TradesResponse trades) {
        tradeRecords = trades.getTradeRecords();

        openPositions = tradeRecords.stream()
                .filter(r -> r.getCmd() == (int) TRADE_OPERATION_CODE.BUY.getCode())
                .collect(Collectors.toList());

        pendingOrders = tradeRecords.stream()
                .filter(r -> r.getCmd() == (int) TRADE_OPERATION_CODE.SELL_LIMIT.getCode() || r.getCmd() == (int) TRADE_OPERATION_CODE.SELL_STOP.getCode() || r.getCmd() == (int) TRADE_OPERATION_CODE.BUY_LIMIT.getCode())
                .collect(Collectors.toList());
    }

    private static String getCmdName(TradeRecord r) {
        switch (r.getCmd()) {
            case 3:
                return "SELL_LIMIT";
            case 5:
                return "SELL_STOP";
            case 0:
                return "BUY";
            case 2:
                return "BUY_LIMIT";
            case 4:
                return "BUY_STOP";
            case 6:
                return "BALANCE";
            case 7:
                return "CREDIT";
            default:
                throw new RuntimeException(String.format("Not implemented for: %d", r.getCmd()));
        }
    }

    public List<TradeRecord> getTradeRecords() {
        return tradeRecords;
    }

    List<String> getTradeRecordsSymbols() {
        return tradeRecords.stream()
                .map(r -> r.getSymbol())
                .collect(Collectors.toList());
    }

    List<TradeRecord> getOpenPositions() {
        return openPositions;
    }

    Set<String> getOpenPositionsSymbols() {
        return openPositions.stream()
                .map(r -> r.getSymbol())
                .collect(Collectors.toSet());
    }

    List<TradeRecord> getPendingOrders() {
        return pendingOrders;
    }

    Optional<TradeRecord> findPendingOrder(String symbol) {
        return getPendingOrders().stream()
                .filter(o -> o.getSymbol().equals(symbol))
                .findFirst();
    }

    Set<String> getPendingOrdersSymbols() {
        return pendingOrders.stream()
                .map(r -> r.getSymbol())
                .collect(Collectors.toSet());
    }

    void print() {
        System.out.println("Open positions:");
        openPositions.forEach(position -> {
            BigDecimal openPrice = new BigDecimal(position.getOpen_price()).setScale(PRICE_SCALE, HALF_EVEN);
            System.out.printf("%s - for %.2f - %s%n", position.getSymbol(), openPrice, getCmdName(position));
        });

        System.out.println("\nPending orders:");
        pendingOrders.forEach(order -> {
            BigDecimal openPrice = new BigDecimal(order.getOpen_price()).setScale(PRICE_SCALE, HALF_EVEN);
            System.out.printf("%s - for %.2f - %s%n", order.getSymbol(), openPrice, getCmdName(order));
        });
    }
}
