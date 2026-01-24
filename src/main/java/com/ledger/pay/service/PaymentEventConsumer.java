package com.ledger.pay.service;



import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class PaymentEventConsumer {

    @KafkaListener(topics = "payment-events", groupId = "notification-service")
    public void handlePaymentEvent(Map<String, Object> event) {
        log.info("Notification service received payment event: {}", event);

        // In a real system, this would:
        // - Send email/SMS to user
        // - Push notification to mobile app
        // - Update external dashboards

        String transactionRef = (String) event.get("transactionRef");
        Object amount = event.get("amount");

        log.info("Would send notification: Transfer {} completed for amount {}", transactionRef, amount);
    }


}
