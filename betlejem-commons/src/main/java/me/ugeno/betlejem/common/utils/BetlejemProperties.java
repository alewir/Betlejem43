package me.ugeno.betlejem.common.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Initial properties for JIF changing of which should not require rebuild.
 */
public class BetlejemProperties {
    private static final String BETLEJEM_XTB_CREDENTIALS_FILENAME = "xtb.credentials.properties";
    private static final String BETLEJEM_BINANCE_CONFIG_FILENAME = "binance.properties";
    private static final String BETLEJEM_CRYPTO_FILENAME = "crypto.properties";

    /*
     * UTILITY CLASS
     */
    private BetlejemProperties() {
    }

    public static String getXtbValue(String key) throws IOException {
        return getValue(BETLEJEM_XTB_CREDENTIALS_FILENAME, key);
    }

    public static String getBinanceValue(String key) throws IOException {
        return getValue(BETLEJEM_BINANCE_CONFIG_FILENAME, key);
    }

    public static String getCryptoValue(String key) throws IOException {
        return getValue(BETLEJEM_CRYPTO_FILENAME, key);
    }

    private static String getValue(String xtbConfig, String key) throws IOException {
        Properties propertiesFile = new Properties();
        String propertiesFilePath = Resources.getPath(xtbConfig);
        try (FileInputStream in = new FileInputStream(propertiesFilePath)) {
            propertiesFile.load(in);
            return (String) propertiesFile.get(key);
        }
    }
}
