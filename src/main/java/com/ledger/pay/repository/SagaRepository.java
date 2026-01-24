package com.ledger.pay.repository;

import com.ledger.pay.domain.Saga;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SagaRepository extends JpaRepository<Saga,Long> {
}
