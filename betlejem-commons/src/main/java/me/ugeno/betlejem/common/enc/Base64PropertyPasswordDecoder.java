package me.ugeno.betlejem.common.enc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Base64PropertyPasswordDecoder implements PropertyPasswordDecoder {

    @Override
    public String decode(String encodedPassword) {
        byte[] decodedData = Base64.getDecoder().decode(encodedPassword);
        return new String(decodedData, StandardCharsets.UTF_8);
    }

    public static void main(String[] args) {
        String str = "sample";
        byte[] base64Str = Base64.getEncoder().encode(str.getBytes());
        System.out.println(new String(base64Str, StandardCharsets.UTF_8));
    }
}