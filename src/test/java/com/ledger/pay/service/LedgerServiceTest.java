package com.ledger.pay.service;

import com.ledger.pay.domain.Account;
import com.ledger.pay.domain.Transaction;
import com.ledger.pay.enums.AccountType;
import com.ledger.pay.enums.TransactionStatus;
import com.ledger.pay.enums.TransactionType;
import com.ledger.pay.observability.LedgerMetrics;
import com.ledger.pay.repository.AccountRepository;
import com.ledger.pay.repository.LedgerEntryRepository;
import com.ledger.pay.repository.OutboxRepository;
import com.ledger.pay.repository.TransactionRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)

class LedgerServiceTest {

    @InjectMocks
    LedgerService ledgerService;
    @Mock
     AccountRepository accountRepository;
    @Mock
    TransactionRepository transactionRepository;
    @Mock
     LedgerEntryRepository ledgerEntryRepository;
    @Mock
   OutboxRepository outboxRepository;
    @Mock
    KafkaEventPublisher kafkaEventPublisher;
   @Mock
   LedgerMetrics metrics;

    @Test
    void shouldSuccessfullyMakeATransfer() {

        String ref ="ref-1";
        String sourceAccountRef ="user:alice:wallet";
        String destinationAccountRef = "user:bob:wallet";

        Account source = wallet("user:alice:wallet",new BigDecimal("100"),BigDecimal.ZERO);
        Account destination = wallet("user:bob:wallet",BigDecimal.ZERO,BigDecimal.ZERO);
        when( transactionRepository.findByReference(ref)).thenReturn(Optional.empty());
        when(accountRepository.findByAccountRefForUpdate(sourceAccountRef)).thenReturn(Optional.of(source));
        when(accountRepository.findByAccountRef(destinationAccountRef)).thenReturn(Optional.of(destination));
        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenAnswer(inv -> {

                    Transaction tx = inv.getArgument(0);
                    tx.setId(1L);
                    return tx;

                });

      Transaction transaction = ledgerService.transfer("user:alice:wallet",
                "user:bob:wallet",new BigDecimal("50"),ref,"transfer");
        assertEquals(new BigDecimal("50"),source.getAccountBalance());

        assertEquals(new BigDecimal("50"),destination.getAccountBalance());

        assertEquals(new BigDecimal("50"),transaction.getAmount());
        assertEquals(TransactionType.TRANSFER, transaction.getType());
        assertEquals(TransactionStatus.COMPLETED, transaction.getStatus());
    }

    @Test
    void shouldSuccessfullyMakeADeposit() {

        String ref = "ref-deposit-1";
        String externalRef = "bank:gtb";
        String walletRef = "user:alice:wallet";

        Account external = external(externalRef);
        Account wallet = wallet(walletRef, BigDecimal.ZERO, BigDecimal.ZERO);

        when(transactionRepository.findByReference(ref))
                .thenReturn(Optional.empty());

        when(accountRepository.findByAccountRefAndAccountType(externalRef, AccountType.EXTERNAL))
                .thenReturn(Optional.of(external));

        when(accountRepository.findByAccountRefAndAccountType(walletRef, AccountType.USER_WALLET))
                .thenReturn(Optional.of(wallet));

        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(inv -> {
                    Transaction tx = inv.getArgument(0);
                    tx.setId(2L);
                    return tx;
                });

        Transaction transaction = ledgerService.deposit(
                externalRef,
                walletRef,
                new BigDecimal("100"),
                ref,
                "deposit"
        );

        assertEquals(new BigDecimal("100"), wallet.getAccountBalance());
        assertEquals(new BigDecimal("-100"), external.getAccountBalance());

        assertEquals(new BigDecimal("100"), transaction.getAmount());
        assertEquals(TransactionType.DEPOSIT, transaction.getType());
        assertEquals(TransactionStatus.COMPLETED, transaction.getStatus());
        assertEquals(ref, transaction.getReference());
    }

    private Account wallet(String ref, BigDecimal totalDeposit, BigDecimal totalWithdrawal) {
        Account acc = new Account();
        acc.setAccountRef(ref);
        acc.setAccountType(AccountType.USER_WALLET);
        acc.setTotalDeposit(totalDeposit);
        acc.setTotalWithdrawal(totalWithdrawal);
        return acc;
    }

    private Account external(String ref) {
        Account acc = new Account();
        acc.setAccountRef(ref);
        acc.setAccountType(AccountType.EXTERNAL);
        acc.setTotalDeposit(BigDecimal.ZERO);
        acc.setTotalWithdrawal(BigDecimal.ZERO);
        return acc;
    }

}