package me.ugeno.betlejem.binance.prices;

import me.ugeno.betlejem.binance.utils.Signature;
import me.ugeno.betlejem.common.utils.binance.BetlejemBinanceUtils;
import org.jetbrains.annotations.NotNull;
import org.openapitools.client.ApiClient;
import org.openapitools.client.Pair;
import org.openapitools.client.api.MarketApi;
import org.openapitools.client.api.TradeApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Created by alwi on 03/12/2021.
 * All rights reserved.
 */
public interface BinanceConnector {
    Logger LOG = LoggerFactory.getLogger(BinanceConnector.class);

    String BINANCE_BASE_PATH = "https://api.binance.com";
    // String BINANCE_BASE_PATH = "https://testnet.binance.vision";

    default String getSignature(List<Pair> parameters) throws IOException {
        String queryPath = joinQueryParameters(parameters);
        LOG.debug(String.format("Query path to sigh: %s", queryPath));
        return Signature.getSignature(queryPath, BetlejemBinanceUtils.fetchBinanceApiSec());
    }

    private static String joinQueryParameters(List<Pair> parameters) {
        StringBuilder urlPath = new StringBuilder();
        boolean isFirst = true;
        for (Pair pair : parameters) {
            if (isFirst) {
                isFirst = false;
                urlPath.append(pair.getName()).append("=").append(pair.getValue());
            } else {
                urlPath.append("&").append(pair.getName()).append("=").append(pair.getValue());
            }
        }
        return urlPath.toString();
    }

    @NotNull
    default MarketApi prepareBinanceClientForMarketApi() throws IOException {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(BINANCE_BASE_PATH);
        apiClient.setApiKey(BetlejemBinanceUtils.fetchBinanceApiKey());

        MarketApi marketApi = new MarketApi();
        marketApi.setApiClient(apiClient);
        return marketApi;
    }

    @NotNull
    default TradeApi prepareBinanceClientForTradeApi() throws IOException {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(BINANCE_BASE_PATH);
        apiClient.setApiKey(BetlejemBinanceUtils.fetchBinanceApiKey());

        TradeApi tradeApi = new TradeApi();
        tradeApi.setApiClient(apiClient);
        return tradeApi;
    }
}
