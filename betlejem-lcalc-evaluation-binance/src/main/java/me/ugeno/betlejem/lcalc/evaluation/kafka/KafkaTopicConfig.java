package me.ugeno.betlejem.lcalc.evaluation.kafka;

import me.ugeno.betlejem.binance.prices.KafkaConnector;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaConnector.KAFKA_BOOTSTRAP_SERVER_CONFIG);
        return new KafkaAdmin(configs);
    }

    @Bean
    public NewTopic ETHUSDC_1m() {
        return new NewTopic("ETHUSDC_1m", 1, (short) 1);
    }

    @Bean
    public NewTopic ETHUSDC_1m_eval() {
        return new NewTopic("ETHUSDC_1m_eval", 1, (short) 1);
    }
}