package com.ledger.pay.domain;

import com.ledger.pay.common.entity.BaseEntity;
import com.ledger.pay.enums.AggregateType;
import com.ledger.pay.enums.EventType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;


@Entity
@Table(
        name = "outbox",
        indexes = {
                @Index(name = "idx_outbox_unprocessed", columnList = "created_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Outbox extends BaseEntity {


    @Enumerated(EnumType.STRING)
    @Column(name = "aggregate_type", nullable = false, length = 100)
    private AggregateType aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 100)
    private EventType eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> payload;
    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    public void markProcessed() {
        this.processedAt = OffsetDateTime.now();
    }
}
