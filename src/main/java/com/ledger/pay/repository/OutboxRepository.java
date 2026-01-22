package com.ledger.pay.repository;

import com.ledger.pay.domain.Outbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface OutboxRepository extends JpaRepository<Outbox,Long> {
    @Query("SELECT o FROM Outbox o WHERE o.processedAt IS NULL ORDER BY o.createdAt ")
    List<Outbox> findUnprocessedEvents( Pageable pageable);
}
