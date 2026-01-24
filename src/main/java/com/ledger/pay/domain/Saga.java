package com.ledger.pay.domain;

import com.ledger.pay.common.entity.BaseEntity;
import com.ledger.pay.enums.SagaStatus;
import com.ledger.pay.enums.SagaType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "sagas")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Saga extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String sagaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaStatus status;

    @Column(nullable = false)
    private int currentStep;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> context;

    @Column
    private String failureReason;

}
