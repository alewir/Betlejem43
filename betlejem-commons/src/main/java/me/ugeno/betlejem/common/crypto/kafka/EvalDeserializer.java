package me.ugeno.betlejem.common.crypto.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.Map;

@SuppressWarnings("Duplicates")
public class EvalDeserializer implements KafkaDeserializer {
    @Override
    public String[] rewrite(ConsumerRecord<String, String> element) {
        Map<String, String> record = extractRecord(element);
        /*
          SAMPLE ELEMENT
            {"date":"202112121050","name":"ETHUSDC_1m","price":4013.49,"sell":0.03,"pass":0.89,"buy":0.08}
         */
        String dateStr = record.get("date");
        String priceStr = record.get("price");
        String sellStr = record.get("sell");
        String passStr = record.get("pass");
        String buyStr = record.get("buy");
        return new String[]{dateStr, priceStr, sellStr, passStr, buyStr};
    }
}