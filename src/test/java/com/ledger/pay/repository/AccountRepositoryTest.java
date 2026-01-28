package com.ledger.pay.repository;

import com.ledger.pay.config.TestAuditingConfig;
import com.ledger.pay.domain.Account;
import com.ledger.pay.enums.AccountType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestAuditingConfig.class)
class AccountRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer=
            new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

    @Autowired
    AccountRepository underTest;
    @BeforeEach
    void  setUp(){
        Account account = Account.builder()
                .accountRef("user:alice:wallet")
                .accountType(AccountType.USER_WALLET)
                .accountBalance(BigDecimal.ZERO)
                .totalWithdrawal(BigDecimal.ZERO)
                .totalDeposit(BigDecimal.ZERO)

                .currency("NGN")

                .build();
        underTest.save(account);

    }
@AfterEach
void tearDown(){
        underTest.deleteAll();
}


    @Test
    void shouldReturnAccountWhenFindByAccountRef() {
        Optional<Account> account = underTest.findByAccountRef("user:alice:wallet");
        assertThat(account).isPresent();
    }
    @Test
    void shouldNotReturnAccountWhenFindByAccountRef() {
        Optional<Account> account = underTest.findByAccountRef("user:akon:wallet");
        assertThat(account).isNotPresent();
    }

    @Test
    void ShouldReturnAccountWhenFindByAccountRefAndAccountType() {

        Optional<Account> account = underTest.findByAccountRefAndAccountType("user:alice:wallet",AccountType.USER_WALLET);
        assertThat(account).isPresent();

    }


}