package com.ledger.pay.domain;


import com.ledger.pay.common.entity.BaseEntity;
import com.ledger.pay.enums.AccountType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Map;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account extends BaseEntity {
    @Column(name = "account_ref", nullable = false, length = 100,unique = true)
    private String accountRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 50)
    private AccountType accountType;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "NGN";


    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    @Builder.Default
    @Column(name = "account_balance", nullable = false, precision = 19, scale = 4, columnDefinition = "DECIMAL(19,4) DEFAULT 0.0000")
    private BigDecimal accountBalance = BigDecimal.ZERO;
    @Builder.Default
    @Column(name = "total_deposit", nullable = false, precision = 19, scale = 4, columnDefinition = "DECIMAL(19,4) DEFAULT 0.0000" )
    private BigDecimal totalDeposit = BigDecimal.ZERO;
    @Builder.Default
    @Column(name = "total_withdrawal", nullable = false, precision = 19, scale = 4, columnDefinition = "DECIMAL(19,4) DEFAULT 0.0000")
    private BigDecimal totalWithdrawal = BigDecimal.ZERO;
    @Version
    int version;

    public  void  withdraw(Transaction transaction){
        this.totalWithdrawal = this.totalWithdrawal.add(transaction.getAmount());
        this.accountBalance = this.totalDeposit.subtract(this.totalWithdrawal);


    }
    public  Transaction  deposit(Transaction transaction){
        this.totalDeposit = this.totalDeposit.add(transaction.getAmount());
        this.accountBalance = this.totalDeposit.subtract(this.totalWithdrawal);
        return transaction;


    }

}
