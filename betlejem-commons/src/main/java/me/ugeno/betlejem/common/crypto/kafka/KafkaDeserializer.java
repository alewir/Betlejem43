package me.ugeno.betlejem.common.crypto.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import me.ugeno.betlejem.binance.prices.KafkaConnector;
import me.ugeno.betlejem.common.utils.BetlejemUtils;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Created by alwi on 12/12/2021.
 * All rights reserved.
 */
@SuppressWarnings("Duplicates")
public interface KafkaDeserializer {
    Logger LOG = LoggerFactory.getLogger(KafkaDeserializer.class);

    String[] rewrite(ConsumerRecord<String, String> element);

    @NotNull
    default Map<String, String> extractRecord(ConsumerRecord<String, String> element) {
        Map<String, String> record = new HashMap<>();
        String json = element.value();
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode;
        try {
            jsonNode = objectMapper.readTree(json);
            for (Iterator<Map.Entry<String, JsonNode>> iterator = jsonNode.fields(); iterator.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                JsonNode value = entry.getValue();
                record.put(entry.getKey(), String.valueOf(value.getNodeType() == JsonNodeType.NUMBER ? value.numberValue() : value.asText()));
            }
        } catch (JsonProcessingException e) {
            BetlejemUtils.logError(e);
        }
        return record;
    }

    default List<String[]> fetch(Consumer<String, String> consumer, TopicPartition partition) {
        List<String[]> all = new ArrayList<>();
        int readAmount;
        do {
            long offset = consumer.position(partition);
            consumer.seek(partition, offset);
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(KafkaConnector.RECORDS_PULL_TIMEOUT));
            readAmount = records.count();

            LOG.debug("Loaded records: {} from offset {}", readAmount, offset);
            all.addAll(records.records(partition)
                    .stream()
                    .map(this::rewrite)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
        } while (readAmount > 0);
        return all;
    }
}
