package com.ledger.pay.repository;


import com.ledger.pay.domain.Account;
import com.ledger.pay.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry,Long> {
    Optional<LedgerEntry> findTopByAccountOrderByCreatedAtDesc(Account account);
}
