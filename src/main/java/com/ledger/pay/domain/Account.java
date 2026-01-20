package com.ledger.pay.domain;


import com.ledger.pay.common.entity.BaseEntity;
import com.ledger.pay.enums.AccountType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account extends BaseEntity {
    @Column(name = "account_ref", nullable = false, length = 100)
    private String accountRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 50)
    private AccountType accountType;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "NGN";


    @Column(columnDefinition = "jsonb")
    private String metadata;
}
