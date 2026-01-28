package com.ledger.pay.service;

import com.ledger.pay.domain.Account;
import com.ledger.pay.domain.LedgerEntry;
import com.ledger.pay.domain.Outbox;
import com.ledger.pay.domain.Transaction;
import com.ledger.pay.enums.*;
import com.ledger.pay.observability.LedgerMetrics;
import com.ledger.pay.repository.AccountRepository;
import com.ledger.pay.repository.LedgerEntryRepository;
import com.ledger.pay.repository.OutboxRepository;
import com.ledger.pay.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private  final OutboxRepository outboxRepository;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final LedgerMetrics metrics;
    //        NOTE: this is a naive implementation of this operation its for learning purpose this is not suited for production


    @Transactional
    public Transaction transfer( String sourceAccountRef,
                                 String destinationAccountRef,
                                 BigDecimal amount,
                                 String reference,
                                 String description){
        String hash = computeIdempotencyHash(
                sourceAccountRef,
                destinationAccountRef,
                amount.toPlainString(),
                reference
        );
       Optional<Transaction>  idempotentTransaction=  transactionRepository.findByReference(reference);
       if(idempotentTransaction.isPresent()){
           Transaction txn =  idempotentTransaction.get();
           if (!hash.equals(txn.getIdempotencyHash())) {
               throw new IllegalArgumentException(
                       "Idempotency key '" + reference + "' already used with different parameters"
               );
           }
           return txn;
       }

        if(amount.compareTo(BigDecimal.ZERO) <= 0){
            throw new IllegalArgumentException("Amount must be positive");
        };
       Account sourceAccount = accountRepository.findByAccountRefForUpdate(sourceAccountRef)
               .orElseThrow(()->
                       new IllegalArgumentException("Source account not found: " + sourceAccountRef));
        Account destinationAccount = accountRepository.findByAccountRef(destinationAccountRef)
                .orElseThrow(()->
                        new IllegalArgumentException("Destination account not found: " + destinationAccountRef));

        Transaction transaction = Transaction.builder()
                .reference(reference)
                .type(TransactionType.TRANSFER)
                .idempotencyHash(hash)
                .status(TransactionStatus.COMPLETED)
                .amount(amount)
                .description(description)
                .build();
        sourceAccount.withdraw(transaction);
        if(sourceAccount.getAccountBalance().compareTo(BigDecimal.ZERO)< 0){
            throw new IllegalStateException("Insufficient funds. Available: "  + ", Requested: " + amount);
        }
        destinationAccount.deposit(transaction);
        try {
            transaction = transactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException e) {
            // Race condition: another request with same reference just committed
            return transactionRepository.findByReference(reference)
                    .orElseThrow(() -> new IllegalStateException("Transaction disappeared"));
        }
        this.accountRepository.save(sourceAccount);

        this.accountRepository.save(destinationAccount);

        LedgerEntry debitEntry = LedgerEntry.builder()
                .transaction(transaction)
                .account(sourceAccount)
                .entryType(LedgerEntryType.DEBIT)
                .amount(amount)
                .build();

        LedgerEntry creditEntry = LedgerEntry.builder()
                .transaction(transaction)
                .account(destinationAccount)
                .entryType(LedgerEntryType.CREDIT)
                .amount(amount)
                .build();

        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);


        Outbox outbox = Outbox.builder()
                .aggregateId(transaction.getId().toString())
                .aggregateType(AggregateType.TRANSACTION)
                .eventType(EventType.TRANSFER_COMPLETED)
                .payload(Map.of("sourceAccountRef",sourceAccountRef,
                        "transactionRef",transaction.getReference(),
                        "destinationAccountRef",destinationAccountRef,"amount",amount))
                .build();
        outboxRepository.save(outbox);

        return transaction;


    };


    @Transactional
    public Transaction deposit(
            String externalAccountRef,
            String userWalletRef,
            BigDecimal amount,
            String reference,
            String description
    ) {
        MDC.put("transactionRef", reference);
        MDC.put("sourceAccount", externalAccountRef);
        MDC.put("destinationAccount", userWalletRef);
        MDC.put("amount", amount.toPlainString());
        log.info("init deposit of {} from {} to {}",amount,externalAccountRef,userWalletRef);
        try {


            String hash = computeIdempotencyHash(
                    externalAccountRef,
                    userWalletRef,
                    amount.toPlainString(),
                    reference
            );
            Optional<Transaction> idempotentTransaction = transactionRepository.findByReference(reference);
            if (idempotentTransaction.isPresent()) {
                Transaction txn = idempotentTransaction.get();
                if (!hash.equals(txn.getIdempotencyHash())) {
                    throw new IllegalArgumentException(
                            "Idempotency key '" + reference + "' already used with different parameters"
                    );
                }
                return txn;
            }
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Amount must be positive");
            }
            ;
            Account sourceAccount = accountRepository.findByAccountRefAndAccountType(externalAccountRef, AccountType.EXTERNAL)
                    .orElseThrow(() ->
                            new IllegalArgumentException("Source account not found: " + externalAccountRef));
            Account destinationAccount = accountRepository.findByAccountRefAndAccountType(userWalletRef, AccountType.USER_WALLET)
                    .orElseThrow(() ->
                            new IllegalArgumentException("Destination account not found: " + userWalletRef));



            Transaction transaction = Transaction.builder()
                    .reference(reference)
                    .type(TransactionType.DEPOSIT)
                    .idempotencyHash(hash)
                    .amount(amount)
                    .status(TransactionStatus.COMPLETED)
                    .description(description)
                    .build();
            sourceAccount.withdraw(transaction);
            destinationAccount.deposit(transaction);

            try {
                transaction = transactionRepository.save(transaction);
            } catch (DataIntegrityViolationException e) {
                log.info("error {}", e);
                // Race condition: another request with same reference just committed
                return transactionRepository.findByReference(reference)
                        .orElseThrow(() -> new IllegalStateException("Transaction disappeared"));
            }
           this.accountRepository.save(sourceAccount);
            this.accountRepository.save(destinationAccount);

            LedgerEntry debitEntry = LedgerEntry.builder()
                    .transaction(transaction)
                    .account(sourceAccount)
                    .entryType(LedgerEntryType.DEBIT)
                    .amount(amount)

                    .build();

            LedgerEntry creditEntry = LedgerEntry.builder()
                    .transaction(transaction)
                    .account(destinationAccount)
                    .entryType(LedgerEntryType.CREDIT)
                    .amount(amount)
                    .build();

            ledgerEntryRepository.save(debitEntry);
            ledgerEntryRepository.save(creditEntry);
            Outbox outbox = Outbox.builder()
                    .aggregateId(transaction.getId().toString())
                    .aggregateType(AggregateType.TRANSACTION)
                    .eventType(EventType.TRANSFER_COMPLETED)  // You might want a DEPOSIT_COMPLETED event
                    .payload(Map.of(
                            "sourceAccountRef", externalAccountRef,
                            "transactionRef", transaction.getReference(),
                            "destinationAccountRef", userWalletRef,
                            "amount", amount
                    ))
                    .build();
            outboxRepository.save(outbox);
            metrics.recordDeposit();
            metrics.recordTransactionAmount("deposit", amount);

            return transaction;
        }catch (Exception e){
            log.error("Transfer failed", e);
            metrics.recordFailure(e.getMessage());
            throw e;
        }finally {

                MDC.clear();

        }

    }
    @Transactional
    public  Outbox processEvent(Outbox event){
        log.info(" sending to Kafka: type={}, payload={}", event.getEventType(), event.getPayload());
        kafkaEventPublisher.publish(resolveTopic(event),event.getAggregateId(),event.getPayload());

        event.markProcessed();
        outboxRepository.save(event);
        return  event;

    };

    private String generateReference() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "ref" + timestamp + "-" + uuid;
    }
    private String computeIdempotencyHash(String... values) {
        String combined = String.join("|", values);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
    private String resolveTopic(Outbox event) {


        return switch (event.getEventType()) {
            case TRANSFER_COMPLETED -> "payment-events";
            case SAGA_COMPLETED, SAGA_FAILED -> "saga-events";
            default -> "ledger-events";
        };
    }
}
