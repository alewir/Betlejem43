package me.ugeno.betlejem.common.crypto.kafka;

import me.ugeno.betlejem.binance.prices.KafkaConnector;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;

import java.util.Collections;
import java.util.List;

/**
 * Created by alwi on 06/12/2021.
 * All rights reserved.
 */
@SuppressWarnings("Duplicates")
public
class DataProviderKafka implements KafkaConnector {
    private static final String BETLEJEM_4_LCALC_DATA_PROVIDER_FROM_OFFSET = "alwi-betlejem-lcalc-data-provider-from-offset";
    private static final String BETLEJEM_4_LCALC_DATA_PROVIDER_RECENT = "alwi-betlejem-lcalc-data-provider-recent";

    public List<String[]> fetchRecentFrom(String topic, long endingOffset, KafkaDeserializer pricesDeserializer) {
        Consumer<String, String> consumer = prepareConsumer(BETLEJEM_4_LCALC_DATA_PROVIDER_RECENT);
        try (consumer) {
            List<TopicPartition> topicPartitions = Collections.singletonList(new TopicPartition(topic, 0));
            consumer.assign(topicPartitions);
            TopicPartition partition = topicPartitions.stream().findFirst().orElse(null);
            consumer.seekToEnd(topicPartitions);
            long total = consumer.position(partition);
            long offset = total - endingOffset;
            consumer.seek(partition, offset < 0 ? 0 : offset);

            return pricesDeserializer.fetch(consumer, partition);
        }
    }

    public List<String[]> fetchAllFrom(String topic, int startingOffset, KafkaDeserializer pricesDeserializer) {
        Consumer<String, String> consumer = prepareConsumer(BETLEJEM_4_LCALC_DATA_PROVIDER_FROM_OFFSET);
        try (consumer) {
            List<TopicPartition> topicPartitions = Collections.singletonList(new TopicPartition(topic, 0));
            consumer.assign(topicPartitions);
            TopicPartition partition = topicPartitions.stream().findFirst().orElse(null);
            consumer.seekToBeginning(topicPartitions);
            long position = consumer.position(partition);
            consumer.seek(partition, position + startingOffset);

            return pricesDeserializer.fetch(consumer, partition);
        }
    }
}