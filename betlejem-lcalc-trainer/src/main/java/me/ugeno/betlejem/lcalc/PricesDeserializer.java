package me.ugeno.betlejem.lcalc;

import me.ugeno.betlejem.common.crypto.kafka.KafkaDeserializer;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.Map;

/**
 * Created by alwi on 12/12/2021.
 * All rights reserved.
 */
@SuppressWarnings("Duplicates")
public class PricesDeserializer implements KafkaDeserializer {
    @Override
    public String[] rewrite(ConsumerRecord<String, String> element) {
        String dateStr = element.key();
        Map<String, String> record = extractRecord(element);
        /*
          SAMPLE ELEMENT
          {"open":"4296.70000000"}
         */
        String priceStr = record.get("open");
        return new String[]{dateStr, priceStr};
    }
}
