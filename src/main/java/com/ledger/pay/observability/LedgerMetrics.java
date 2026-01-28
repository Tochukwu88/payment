package com.ledger.pay.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class LedgerMetrics {

    private final Counter transferCounter;
    private final Counter depositCounter;
    private final Counter failedTransactionCounter;
    private final Timer transferTimer;
    private final MeterRegistry registry;

    public LedgerMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.transferCounter = Counter.builder("ledger.transactions")
                .tag("type", "transfer")
                .description("Number of transfers processed")
                .register(registry);

        this.depositCounter = Counter.builder("ledger.transactions")
                .tag("type", "deposit")
                .description("Number of deposits processed")
                .register(registry);

        this.failedTransactionCounter = Counter.builder("ledger.transactions.failed")
                .description("Number of failed transactions")
                .register(registry);

        this.transferTimer = Timer.builder("ledger.transfer.duration")
                .description("Time taken to process transfers")
                .register(registry);
    }

    public void recordTransfer() {
        transferCounter.increment();
    }

    public void recordDeposit() {
        depositCounter.increment();
    }

    public void recordFailure(String reason) {
        failedTransactionCounter.increment();
        log.warn("Transaction failed: {}", reason);
    }

    public void recordTransferDuration(long milliseconds) {
        transferTimer.record(milliseconds, TimeUnit.MILLISECONDS);
    }

    public void recordTransactionAmount(String type, BigDecimal amount) {
        registry.gauge("ledger.transaction.amount",
                io.micrometer.core.instrument.Tags.of("type", type),
                amount.doubleValue());
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void stopTimer(Timer.Sample sample) {
        sample.stop(transferTimer);
    }
}