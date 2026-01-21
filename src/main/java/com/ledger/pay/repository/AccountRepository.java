package com.ledger.pay.repository;

import com.ledger.pay.domain.Account;
import com.ledger.pay.enums.AccountType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountRef(String accountRef);
    Optional<Account> findByAccountRefAndAccountType(String accountRef, AccountType accountType);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountRef =:accountRef")
    Optional<Account> findByAccountRefForUpdate(@Param("accountRef")String accountRef);
}
