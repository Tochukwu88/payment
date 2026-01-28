package com.ledger.pay.domain;


import com.ledger.pay.common.entity.BaseEntity;
import com.ledger.pay.enums.LedgerEntryType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Table(
        name = "ledger_entries",
        indexes = {
                @Index(name = "idx_ledger_txn", columnList = "transaction_id"),
                @Index(name = "idx_ledger_account", columnList = "account_id")
        }
)
public class LedgerEntry extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;


    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private LedgerEntryType entryType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;


}
