package com.ledger.pay.domain;

import com.ledger.pay.common.entity.BaseEntity;
import com.ledger.pay.enums.StepStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "saga_steps")
public class SagaStep extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saga_id", nullable = false)
    private Saga saga;

    @Column(nullable = false)
    private int stepNumber;

    @Column(nullable = false)
    private String stepName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StepStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> result;
}
