package me.ugeno.betlejem.common.enc;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Aleksander Wirth (alwi) on 24/02/2021.
 * Company: Ultsoft
 * All rights reserved.
 */
public class ObfuscatedPasswordsContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final Pattern decodePasswordPattern = Pattern.compile("ENC\\((.*?)\\)");

    private PropertyPasswordDecoder passwordDecoder = new Base64PropertyPasswordDecoder();

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            Map<String, Object> propertyOverrides = new LinkedHashMap<>();
            decodePasswords(propertySource, propertyOverrides);
            if (!propertyOverrides.isEmpty()) {
                PropertySource<?> decodedProperties = new MapPropertySource("decoded " + propertySource.getName(), propertyOverrides);
                environment.getPropertySources().addBefore(propertySource.getName(), decodedProperties);
            }
        }
    }

    private void decodePasswords(PropertySource<?> source, Map<String, Object> propertyOverrides) {
        if (source instanceof EnumerablePropertySource) {
            EnumerablePropertySource<?> enumerablePropertySource = (EnumerablePropertySource<?>) source;
            for (String key : enumerablePropertySource.getPropertyNames()) {
                Object rawValue = source.getProperty(key);
                if (rawValue instanceof String) {
                    String decodedValue = decodePasswordsInString((String) rawValue);
                    propertyOverrides.put(key, decodedValue);
                }
            }
        }
    }

    private String decodePasswordsInString(String input) {
        if (input == null) return null;
        StringBuffer output = new StringBuffer();
        Matcher matcher = decodePasswordPattern.matcher(input);
        while (matcher.find()) {
            String encodedPassword = matcher.group(1);
            String replacement = passwordDecoder.decode(encodedPassword);
            matcher.appendReplacement(output, replacement);
        }
        matcher.appendTail(output);
        return output.toString();
    }
}
