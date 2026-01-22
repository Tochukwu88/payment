package com.ledger.pay.service;

import com.ledger.pay.domain.Account;
import com.ledger.pay.domain.LedgerEntry;
import com.ledger.pay.domain.Outbox;
import com.ledger.pay.domain.Transaction;
import com.ledger.pay.enums.*;
import com.ledger.pay.repository.AccountRepository;
import com.ledger.pay.repository.LedgerEntryRepository;
import com.ledger.pay.repository.OutboxRepository;
import com.ledger.pay.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        BigDecimal sourceBalance = getCurrentBalance(sourceAccount);
        if (sourceBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds. Available: " + sourceBalance + ", Requested: " + amount);
        }
        Transaction transaction = Transaction.builder()
                .reference(reference)
                .type(TransactionType.TRANSFER)
                .idempotencyHash(hash)
                .status(TransactionStatus.COMPLETED)
                .description(description)
                .build();
        try {
            transaction = transactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException e) {
            // Race condition: another request with same reference just committed
            return transactionRepository.findByReference(reference)
                    .orElseThrow(() -> new IllegalStateException("Transaction disappeared"));
        }
        BigDecimal newSourceBalance = sourceBalance.subtract(amount);
        BigDecimal destinationBalance = getCurrentBalance(destinationAccount);
        BigDecimal newDestinationBalance = destinationBalance.add(amount);

        LedgerEntry debitEntry = LedgerEntry.builder()
                .transaction(transaction)
                .account(sourceAccount)
                .entryType(LedgerEntryType.DEBIT)
                .amount(amount)
                .balanceAfter(newSourceBalance)
                .build();

        LedgerEntry creditEntry = LedgerEntry.builder()
                .transaction(transaction)
                .account(destinationAccount)
                .entryType(LedgerEntryType.CREDIT)
                .amount(amount)
                .balanceAfter(newDestinationBalance)
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

    private BigDecimal getCurrentBalance(Account account){

           return  ledgerEntryRepository.findTopByAccountOrderByCreatedAtDesc(account).map(LedgerEntry::getBalanceAfter).orElse(BigDecimal.ZERO);
    }
    @Transactional
    public Transaction deposit(
            String externalAccountRef,
            String userWalletRef,
            BigDecimal amount,
            String reference,
            String description
    ) {
        String hash = computeIdempotencyHash(
                externalAccountRef,
                userWalletRef,
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
        Account sourceAccount = accountRepository.findByAccountRefAndAccountType(externalAccountRef,AccountType.EXTERNAL)
                .orElseThrow(()->
                        new IllegalArgumentException("Source account not found: " + externalAccountRef));
        Account destinationAccount = accountRepository.findByAccountRefAndAccountType(userWalletRef,AccountType.USER_WALLET)
                .orElseThrow(()->
                        new IllegalArgumentException("Destination account not found: " + userWalletRef));
        BigDecimal sourceBalance = getCurrentBalance(sourceAccount);

        Transaction transaction = Transaction.builder()
                .reference(reference)
                .type(TransactionType.DEPOSIT)
                .idempotencyHash(hash)
                .status(TransactionStatus.COMPLETED)
                .description(description)
                .build();

        try {
            transaction = transactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException e) {
            // Race condition: another request with same reference just committed
            return transactionRepository.findByReference(reference)
                    .orElseThrow(() -> new IllegalStateException("Transaction disappeared"));
        }
        BigDecimal newSourceBalance = sourceBalance.subtract(amount);
        BigDecimal destinationBalance = getCurrentBalance(destinationAccount);
        BigDecimal newDestinationBalance = destinationBalance.add(amount);

        LedgerEntry debitEntry = LedgerEntry.builder()
                .transaction(transaction)
                .account(sourceAccount)
                .entryType(LedgerEntryType.DEBIT)
                .amount(amount)
                .balanceAfter(newSourceBalance)
                .build();

        LedgerEntry creditEntry = LedgerEntry.builder()
                .transaction(transaction)
                .account(destinationAccount)
                .entryType(LedgerEntryType.CREDIT)
                .amount(amount)
                .balanceAfter(newDestinationBalance)
                .build();

        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);

        return transaction;

    }
    @Transactional
    public  Transaction hold(BigDecimal amount ,String accountRef,String holdAccountRef, String description,String reference){
        String hash = computeIdempotencyHash(
                accountRef,
                holdAccountRef,
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
        if(amount.compareTo(BigDecimal.ZERO)<=0){
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
        Account account = accountRepository.findByAccountRefForUpdate(accountRef)
                .orElseThrow(()->
                        new IllegalArgumentException("Source account not found: " + accountRef));
        Account holdAccount;
        Optional<Account> holdAccountExist = accountRepository.findByAccountRefAndAccountType(holdAccountRef, AccountType.HOLD);
        if(holdAccountExist.isPresent()){
            holdAccount= holdAccountExist.get();
        }else
        {
            holdAccount= Account.builder().accountType(AccountType.HOLD).accountRef(holdAccountRef).currency("NGN").build();
            accountRepository.save(holdAccount);
        }

        BigDecimal accountBalance = getCurrentBalance(account);
        if(accountBalance.compareTo(amount)< 0){
            throw new IllegalStateException("Insufficient funds. Available: " + accountBalance + ", Requested: " + amount);

        }
        BigDecimal newAccountBalance = accountBalance.subtract(amount);
        BigDecimal newHoldAccountBalance = getCurrentBalance(holdAccount).add(amount);

        Transaction transaction = Transaction.builder()
                .reference(reference)
                .type(TransactionType.HOLD)
                .idempotencyHash(hash)
                .status(TransactionStatus.COMPLETED)
                .description(description)
                .build();
        try {
            transaction = transactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException e) {
            // Race condition: another request with same reference just committed
            return transactionRepository.findByReference(reference)
                    .orElseThrow(() -> new IllegalStateException("Transaction disappeared"));
        }
        LedgerEntry debitEntry = LedgerEntry.builder()
                .transaction(transaction)
                .account(account)
                .entryType(LedgerEntryType.DEBIT)
                .amount(amount)
                .balanceAfter(newAccountBalance)
                .build();

        LedgerEntry creditEntry = LedgerEntry.builder()
                .transaction(transaction)
                .account(holdAccount)
                .entryType(LedgerEntryType.CREDIT)
                .amount(amount)
                .balanceAfter(newHoldAccountBalance)
                .build();

        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);

        return transaction;

    }
    @Transactional
    public  Transaction captureHold(BigDecimal amount ,String destinationAccountRef,String holdAccountRef, String description,String reference){
        String hash = computeIdempotencyHash(
                holdAccountRef,
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
        if(amount.compareTo(BigDecimal.ZERO)<=0){
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
        Account destinationAccount = accountRepository.findByAccountRef(destinationAccountRef)
                .orElseThrow(()->
                        new IllegalArgumentException("destination account not found: " + destinationAccountRef));

        Account holdAccount = accountRepository.findByAccountRefForUpdate(holdAccountRef).orElseThrow(()->
                new IllegalArgumentException("hold account not found: " + holdAccountRef));


        BigDecimal holdAccountBalance = getCurrentBalance(holdAccount);
        if(holdAccountBalance.compareTo(amount)< 0){
            throw new IllegalStateException("Insufficient funds. Available: " + holdAccountBalance + ", Requested: " + amount);

        }
        BigDecimal newHoldAccountBalance = holdAccountBalance.subtract(amount);
        BigDecimal newDestinationAccountBalance = getCurrentBalance(destinationAccount).add(amount);

        Transaction transaction = Transaction.builder()
                .reference(reference)
                .type(TransactionType.CAPTURE)
                .idempotencyHash(hash)
                .status(TransactionStatus.COMPLETED)
                .description(description)
                .build();
        try {
            transaction = transactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException e) {
            // Race condition: another request with same reference just committed
            return transactionRepository.findByReference(reference)
                    .orElseThrow(() -> new IllegalStateException("Transaction disappeared"));
        }
        LedgerEntry debitEntry = LedgerEntry.builder()
                .transaction(transaction)
                .account(holdAccount)
                .entryType(LedgerEntryType.DEBIT)
                .amount(amount)
                .balanceAfter(newHoldAccountBalance)
                .build();

        LedgerEntry creditEntry = LedgerEntry.builder()
                .transaction(transaction)
                .account(destinationAccount)
                .entryType(LedgerEntryType.CREDIT)
                .amount(amount)
                .balanceAfter(newDestinationAccountBalance)
                .build();

        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);

        return transaction;

    }
    @Transactional
    public  Outbox processEvent(Outbox event){
        log.info("Would send to Kafka: type={}, payload={}", event.getEventType(), event.getPayload());
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
}
