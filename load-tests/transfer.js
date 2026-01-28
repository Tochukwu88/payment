import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// Metrics
const successfulTransfers = new Counter('successful_transfers');
const failedTransfers = new Counter('failed_transfers');
const insufficientFunds = new Counter('insufficient_funds');
const duplicateDetected = new Counter('duplicate_detected');
const transferDuration = new Trend('transfer_duration');

// Test configuration
export const options = {
    scenarios: {
        // Scenario 1: Multiple users hitting same account simultaneously
        concurrent_same_account: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 20 },
                { duration: '30s', target: 20 },
                { duration: '10s', target: 0 },
            ],
            exec: 'sameAccountTest',
      },
//        // Scenario 2: Same reference sent multiple times (idempotency test)
//        idempotency_test: {
//            executor: 'per-vu-iterations',
//            vus: 10,
//            iterations: 5,
//            exec: 'idempotencyTest',
//            startTime: '1m',
//        },
//        // Scenario 3: Rapid fire transfers to drain account
//        drain_account: {
//            executor: 'constant-vus',
//            vus: 50,
//            duration: '30s',
//            exec: 'drainAccountTest',
//            startTime: '2m',
//        },
    },
    thresholds: {
        http_req_failed: ['rate<0.05'],
    },
};

const BASE_URL = 'http://172.26.0.1:8080';

// Generate unique reference
function generateReference() {
    return `txn-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
}

// Scenario 1: Multiple concurrent transfers from same account
export function sameAccountTest() {
    const payload = JSON.stringify({
        sourceAccountRef: 'user:alice:wallet',
        destinationAccountRef: 'user:bob:wallet',
        amount: 100,
        reference: generateReference(),
        description: 'concurrent transfer test',
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    const startTime = Date.now();
    const response = http.post(`${BASE_URL}/api/v1/transfer`, payload, params);
    const duration = Date.now() - startTime;

    transferDuration.add(duration);

    if (response.status === 201) {
        successfulTransfers.add(1);
    } else if (response.body && response.body.includes('Insufficient funds')) {
        insufficientFunds.add(1);
    } else {
        failedTransfers.add(1);
        console.log(`Failed: ${response.status} - ${response.body}`);
    }

    sleep(0.01);
}

// Scenario 2: Same reference multiple times (should return same result, not duplicate)
//export function idempotencyTest() {
//    // Each VU uses the same reference for all its iterations
//    const vuReference = `txn-idempotency-vu${__VU}`;
//
//    const payload = JSON.stringify({
//        sourceAccountRef: 'user:alice:wallet',
//        destinationAccountRef: 'user:bob:wallet',
//        amount: 50,
//        reference: vuReference,
//        description: 'idempotency test',
//    });
//
//    const params = {
//        headers: {
//            'Content-Type': 'application/json',
//        },
//    };
//
//    const response = http.post(`${BASE_URL}/api/v1/transfer`, payload, params);
//
//    const success = check(response, {
//        'status is 200': (r) => r.status === 200,
//        'returns same transaction': (r) => {
//            try {
//                const body = JSON.parse(r.body);
//                return body.reference === vuReference;
//            } catch {
//                return false;
//            }
//        },
//    });
//
//    if (success) {
//        duplicateDetected.add(1);
//        console.log(`VU ${__VU} iteration ${__ITER}: Idempotency working - same reference returned`);
//    }
//
//    sleep(0.1);
//}

//// Scenario 3: Try to drain account with concurrent requests
//export function drainAccountTest() {
//    // All VUs try to transfer the same large amount simultaneously
//    const payload = JSON.stringify({
//        sourceAccountRef: 'user:alice:wallet',
//        destinationAccountRef: 'user:bob:wallet',
//        amount: 5000,  // Large amount - should fail for most if balance is limited
//        reference: generateReference(),
//        description: 'drain test',
//    });
//
//    const params = {
//        headers: {
//            'Content-Type': 'application/json',
//        },
//    };
//
//    const response = http.post(`${BASE_URL}/api/v1/transfer`, payload, params);
//
//    if (response.status === 200) {
//        successfulTransfers.add(1);
//        console.log(`Transfer succeeded - check for double spend!`);
//    } else if (response.body && response.body.includes('Insufficient funds')) {
//        insufficientFunds.add(1);
//    } else {
//        failedTransfers.add(1);
//    }
//
//    sleep(0.01);
//}