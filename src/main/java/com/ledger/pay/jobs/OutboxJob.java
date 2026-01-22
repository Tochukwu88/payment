package com.ledger.pay.jobs;

import com.ledger.pay.domain.Outbox;
import com.ledger.pay.repository.OutboxRepository;
import com.ledger.pay.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxJob {
    private  final OutboxRepository outboxRepository;
    private  final LedgerService ledgerService;
    private final ExecutorService executor =
            Executors.newVirtualThreadPerTaskExecutor();
    @Scheduled(fixedDelayString = "${app.outbox.interval-ms:10000}")
    public void processOutboxEvents() {
        try{
        List<Outbox> events =outboxRepository.findUnprocessedEvents(PageRequest.of(0, 50));

        if (events.isEmpty()) {
            return;
        }

        log.info("Processing {} outbox events", events.size());
        try{
            List<? extends Future<?>> futures =  events.stream().map(event ->
                    executor.submit(()->
                    {
                        try {
                            ledgerService.processEvent(event);

                        } catch (Exception e) {
                            log.error("Failed to process event {}", event.getId(), e);
                        }
                    })).toList();

            for (Future<?> future : futures) {
                future.get();
            }

        } finally {

        }} catch (Exception e) {
            log.error("Error in outbox processor: {}", e.getMessage(), e);
        }

    }
}
