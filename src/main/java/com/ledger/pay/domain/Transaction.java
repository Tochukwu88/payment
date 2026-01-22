package com.ledger.pay.domain;

import com.ledger.pay.common.entity.BaseEntity;
import com.ledger.pay.enums.TransactionStatus;
import com.ledger.pay.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Table(
        name = "transactions",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "reference")
        }
)
public class Transaction extends BaseEntity {
    @Column(nullable = false, unique = true, length = 100)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.COMPLETED;

    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(name = "idempotency_hash", length = 64)
    private String idempotencyHash;


    @Column(columnDefinition = "jsonb")
    private String metadata;

}
