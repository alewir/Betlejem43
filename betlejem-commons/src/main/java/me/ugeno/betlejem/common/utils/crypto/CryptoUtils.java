package me.ugeno.betlejem.common.utils.crypto;

import me.ugeno.betlejem.common.enc.Base64PropertyPasswordDecoder;
import me.ugeno.betlejem.common.utils.BetlejemProperties;

import java.io.IOException;

/**
 * Created by alwi on 28/11/2021.
 * All rights reserved.
 */
public class CryptoUtils {
    public static String fetchMnemonic() throws IOException {
        Base64PropertyPasswordDecoder dec = new Base64PropertyPasswordDecoder();
        String mnemonic = BetlejemProperties.getCryptoValue(CryptoConstants.MNEMONIC);
        return dec.decode(mnemonic);
    }
}
