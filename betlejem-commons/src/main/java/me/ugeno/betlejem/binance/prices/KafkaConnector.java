package me.ugeno.betlejem.binance.prices;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.ugeno.betlejem.common.utils.BetlejemUtils;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Created by alwi on 03/12/2021.
 * All rights reserved.
 */
public interface KafkaConnector {
    String KAFKA_BOOTSTRAP_SERVER_CONFIG = "localhost:9093";
    String KEY_DATE_PATTERN = "YYYYMMddHHmm";
    int RECORDS_PULL_TIMEOUT = 10;
    int IDX_SCRAP_IN_OPEN = 1;

    default Optional<String> fetchLastEntryKey(Consumer<String, String> consumer, Collection<TopicPartition> topicPartitions) {
        consumer.seekToEnd(topicPartitions);
        TopicPartition partition = topicPartitions.stream().findFirst().orElse(null);
        long offset = consumer.position(partition) - 1;
        if (offset > 0) {
            consumer.seek(partition, offset);
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(RECORDS_PULL_TIMEOUT));
            System.out.println("Records: " + records.count() + " from offset " + offset);
            return records.records(partition).stream().map(ConsumerRecord::key).findFirst();
        } else {
            return Optional.empty();
        }
    }

    default void uploadPrices(List<List<String>> recordsList, String topicName, Producer<String, String> producer, String keyDatePattern, int scale) {
        for (List<String> record : recordsList) {

            /*
                Data sample:
                1499040000000,      // Open time 0
                "0.01634790",       // Open 1
                "0.80000000",       // High 2
                "0.01575800",       // Low 3
                "0.01577100",       // Close 4
                "148976.11427815",  // Volume 5
                1499644799999,      // Close time 6
                "2434.19055334",    // Quote asset volume 7
                308,                // Number of trades 8
                "1756.87402397",    // Taker buy base asset volume 9
                "28.46694368",      // Taker buy quote asset volume 10
                "17928899.62484339" // Ignore.
            */

            DateTime timestamp = new DateTime(Long.parseLong(record.get(0)));

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("open", new BigDecimal(String.valueOf(record.get(IDX_SCRAP_IN_OPEN))).setScale(scale, RoundingMode.HALF_EVEN));
            ObjectMapper objectMapper = new ObjectMapper();

            try {
                String value = objectMapper.writeValueAsString(map);
                producer.send(new ProducerRecord<>(topicName, timestamp.toString(keyDatePattern), value));
            } catch (JsonProcessingException e) {
                BetlejemUtils.logError(e);
            }
        }
    }

    @NotNull
    default Consumer<String, String> prepareConsumer(String value) {
        Properties consumerProps = new Properties();
        consumerProps.put("bootstrap.servers", KAFKA_BOOTSTRAP_SERVER_CONFIG);
        consumerProps.put("key.deserializer", org.apache.kafka.common.serialization.StringDeserializer.class.getCanonicalName());
        consumerProps.put("value.deserializer", org.apache.kafka.common.serialization.StringDeserializer.class.getCanonicalName());
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, value);
        return new KafkaConsumer<>(consumerProps);
    }

    @NotNull
    default Producer<String, String> prepareProducer() {
        Properties producerProps = new Properties();
        producerProps.put("bootstrap.servers", KAFKA_BOOTSTRAP_SERVER_CONFIG);
        producerProps.put("key.serializer", org.apache.kafka.common.serialization.StringSerializer.class.getCanonicalName());
        producerProps.put("value.serializer", org.apache.kafka.common.serialization.StringSerializer.class.getCanonicalName());
        return new KafkaProducer<>(producerProps);
    }
}
