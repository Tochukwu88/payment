# High-Throughput Payment Ledger System

A production-grade demonstration of building a financial ledger system for fintech applications. This project implements core banking patterns.

> ⚠️ **Disclaimer**: This is an educational project demonstrating financial system patterns. It is not production-ready and should not be used for real financial transactions without extensive additional work on security, compliance, and testing.



---

## Overview

This system demonstrates how to build a reliable, consistent financial ledger that:

- **Never loses money** : Every debit has a matching credit
- **Prevents double-spending**: Pessimistic locking ensures atomic balance checks
- **Handles retries safely** : Idempotency keys prevent duplicate transactions
- **Reliably publishes events** :Outbox pattern solves the dual-write problem
- **Scales under load** : Tested with concurrent requests using k6

### What This Project Covers

| Concept                   | Implementation |
|---------------------------|----------|
| Double-entry bookkeeping  | Balanced ledger entries for every transaction |
| Idempotency               | SHA-256 hash-based request deduplication |
| Pessimistic locking       | `SELECT FOR UPDATE` to prevent race conditions |
| Optimistic locking        | In addition to pessimistic locking, the system uses optimistic locking as a safety net.         |
| Outbox pattern            | Reliable event publishing to Kafka |
| Event-driven architecture | Kafka producers and consumers |
| Reconciliation            | SQL-based integrity checks |
| Observability             | Micrometer metrics + structured logging |
| Load testing              | k6 scripts for concurrency testing |

---


## Core Concepts

### Double-Entry Bookkeeping

Every financial transaction creates two ledger entries that must balance:


**Key principle**: Money doesn't appear or disappear—it moves between accounts.


### Idempotency

Network failures cause retries. Without idempotency, the same payment could process multiple times.



### Concurrency Control

When multiple requests try to transfer from the same account simultaneously, race conditions can cause overdrafts.




### Outbox Pattern

You cannot atomically update a database AND send a Kafka message. The outbox pattern solves this.




---

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 4.x |
| Database | PostgreSQL 16 |
| Message Broker | Apache Kafka 3.7 (KRaft mode) |
| Metrics | Micrometer + Prometheus |
| Load Testing | k6 |
| Containerization | Docker Compose |

---

## Getting Started

### Prerequisites

- Java 21+
- Docker and Docker Compose
- Maven
- k6 (for load testing)

### 1. Clone the Repository
```bash
git clone https://github.com/yourusername/payment-ledger.git
cd payment-ledger
```

### 2. Start Infrastructure
```bash
docker-compose up -d
```

This starts:
- Kafka (localhost:9094 for host, kafka:9092 for containers)
- Kafka UI (http://localhost:8090)

### 3. Configure Database

Ensure PostgreSQL is running and create the database:
```sql
CREATE DATABASE paydb;
```

Update `application.properties` with your database credentials.

### 4. Run the Application
```bash
mvn spring-boot:run
```

### 5. Create Test Accounts
```sql
INSERT INTO accounts (account_ref, account_type, currency, account_balance, total_deposit, total_withdrawal, version, created_at, updated_at) 
VALUES 
    ('user:alice:wallet', 'USER_WALLET', 'NGN', 0, 0, 0, 0, NOW(), NOW()),
    ('user:bob:wallet', 'USER_WALLET', 'NGN', 0, 0, 0, 0, NOW(), NOW()),
    ('external:bank', 'EXTERNAL', 'NGN', 0, 0, 0, 0, NOW(), NOW());
```

### 6. Test the API
```bash
# Deposit funds
curl -X POST http://localhost:8080/api/v1/deposit \
  -H "Content-Type: application/json" \
  -d '{
    "externalAccountRef": "external:bank",
    "userWalletRef": "user:alice:wallet",
    "amount": 100000,
    "reference": "DEP_001",
    "description": "Initial deposit"
  }'

# Transfer funds
curl -X POST http://localhost:8080/api/v1/transfer \
  -H "Content-Type: application/json" \
  -d '{
    "sourceAccountRef": "user:alice:wallet",
    "destinationAccountRef": "user:bob:wallet",
    "amount": 10000,
    "reference": "TXN_001",
    "description": "Payment for services"
  }'
```

---

## API Reference

### POST /api/v1/deposit

Deposit funds from an external source into a user wallet.

**Request**:
```json
{
  "externalAccountRef": "external:bank",
  "userWalletRef": "user:alice:wallet",
  "amount": 50000,
  "reference": "DEP_unique_123",
  "description": "Salary deposit"
}
```

**Response** (201 Created):
```json
{
  "id": 1,
  "reference": "DEP_unique_123",
  "type": "DEPOSIT",
  "status": "COMPLETED",
  "description": "Salary deposit",
  "createdAt": "2024-01-24T10:30:00"
}
```

### POST /api/v1/transfer

Transfer funds between accounts.

**Request**:
```json
{
  "sourceAccountRef": "user:alice:wallet",
  "destinationAccountRef": "user:bob:wallet",
  "amount": 10000,
  "reference": "TXN_unique_456",
  "description": "Payment"
}
```

**Response** (201 Created):
```json
{
  "id": 2,
  "reference": "TXN_unique_456",
  "type": "TRANSFER",
  "status": "COMPLETED",
  "description": "Payment",
  "createdAt": "2024-01-24T10:35:00"
}
```




## Testing

### Load Testing with k6

Install k6:
```bash
# macOS
brew install k6

# Linux
sudo apt-get install k6

# Windows
choco install k6
```

Run the double-spend test:
```bash
k6 run load-tests/transfer.js
```

This test Sends concurrent transfers from the same account

### Verifying Results

After load testing, run these SQL queries to verify integrity:
```sql
-- Check global balance (debits should equal credits)
SELECT 
    entry_type,
    SUM(amount) as total
FROM ledger_entries
GROUP BY entry_type;

-- Check for negative balances (should return zero rows)
SELECT account_ref, account_balance
FROM accounts
WHERE account_type = 'USER_WALLET' AND account_balance < 0;

-- Full reconciliation summary
WITH ledger_check AS (
    SELECT 
        SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END) as total_debits,
        SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END) as total_credits
    FROM ledger_entries
)
SELECT 
    total_debits,
    total_credits,
    total_debits = total_credits as balanced,
    CASE WHEN total_debits = total_credits THEN 'HEALTHY' ELSE 'UNBALANCED' END as status
FROM ledger_check;
```

---



## Observability

### Metrics

Access Prometheus metrics:
```bash
curl http://localhost:8080/actuator/prometheus
```

Key metrics:

| Metric | Description |
|--------|-------------|
| `ledger_transactions_total{type="transfer"}` | Total transfers processed |
| `ledger_transactions_total{type="deposit"}` | Total deposits processed |
| `ledger_transactions_failed_total` | Failed transactions |
| `ledger_transfer_duration_seconds` | Transfer processing time |
| `ledger_outbox_pending` | Pending outbox events |

### Structured Logging

Logs include MDC context for traceability:
```json
{
  "timestamp": "2024-01-24T10:30:00.123Z",
  "level": "INFO",
  "message": "Transfer completed",
  "transactionRef": "TXN_001",
  "sourceAccount": "user:alice:wallet",
  "destinationAccount": "user:bob:wallet",
  "amount": "10000"
}
```

---



## Contributing

This is an educational project. Issues and pull requests for improvements are welcome.

---

