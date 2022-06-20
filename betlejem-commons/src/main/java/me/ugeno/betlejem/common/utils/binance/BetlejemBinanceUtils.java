package me.ugeno.betlejem.common.utils.binance;

import me.ugeno.betlejem.common.enc.Base64PropertyPasswordDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static me.ugeno.betlejem.common.utils.BetlejemProperties.getBinanceValue;

/**
 * Created by alwi on 06/03/2021.
 * All rights reserved.
 */
public class BetlejemBinanceUtils {
    private static final Logger LOG = LoggerFactory.getLogger(BetlejemBinanceUtils.class);

    public static String fetchBinanceApiKey() throws IOException {
        return getStringValue(BetlejemBinanceConstants.BINANCE_API_KEY);
    }

    public static String fetchBinanceApiSec() throws IOException {
        return getStringValue(BetlejemBinanceConstants.BINANCE_API_SEC);
    }

    public static String fetchBinancePassphrase() throws IOException {
        return getStringValue(BetlejemBinanceConstants.BINANCE_PASSPHRASE);
    }

    private static String getStringValue(String key) throws IOException {
        Base64PropertyPasswordDecoder dec = new Base64PropertyPasswordDecoder();
        return dec.decode(getBinanceValue(key));
    }
}
