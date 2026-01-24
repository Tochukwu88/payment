package com.ledger.pay.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaEventPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    public void publish(String topic, String key, Map<String, Object> payload) {
        log.info("Publishing to topic {}: key={}", topic, key);

        kafkaTemplate.send(topic, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish to {}: {}", topic, ex.getMessage());
                    } else {
                        log.info("Published to {} partition {} offset {}",
                                topic,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
