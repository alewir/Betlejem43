package me.ugeno.betlejem.common.utils;

import java.util.Random;

public class Value {
    public static final String DIGITS = "1234567890";

    private Value() {
        // Utility class - should not be instantiated
    }

    public static String gen(String charClass, int max) {
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < max; i++) {
            sb.append(charClass.charAt(random.nextInt(charClass.length())));
        }

        return roughEscape(sb.toString());
    }

    private static String roughEscape(String content) {
        content = content.replace("&", "&amp;"); // ampersand has to be escaped first
        content = content.replace("'", "&apos;");
        content = content.replace("\"", "&quot;");
        content = content.replace("<", "&lt;");
        content = content.replace(">", "&gt;");
        return content;
    }
}
