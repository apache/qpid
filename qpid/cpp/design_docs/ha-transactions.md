<!--
    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.
-->

# Design note: HA with Transactions.

Clients can use transactions (TX or DTX) with the current HA module but:

- the results are not guaranteed to be comitted atomically on the backups.
- the backups do not record the transaction in the store for possible recovery.

## Requirements

1. Atomic: Transaction results must be atomic on primary and backups.

2. Consistent: Backups and primary must agree on whether the transaction comitted.

3. Isolated: Concurrent transactions must not interfere with each other.

4. Durable: Transactional state is written to the store.

5. Recovery: If a cluster crashes while a DTX is in progress, it can be
   re-started and participate in DTX recovery with a transaction co-ordinater
   (currently via JMS)

## TX and DTX

Both TX and DTX transactions require a DTX-like `prepare` phase to synchronize
the commit or rollback across multiple brokers. This design note applies to both.

For DTX the transaction is identified by the `xid`. For TX the HA module generates
a unique identifier.

## Intercepting transactional operations

Introduce  2 new interfaces on the Primary to intercept transactional operations:

    TransactionObserverFactory {
      TransactionObserver create(...);
    }

    TransactionObserver {
      publish(queue, msg);
      accept(accumulatedAck, unacked) # NOTE: translate queue positions to replication ids
      prepare()
      commit()
      rollback()
    }

The primary will register a `TransactionObserverFactory` with the broker to hook
into transactions. On the primary, transactional events are processed as normal
and additionally are passed to the `TransactionObserver` which replicates the
events to the backups.

## Replicating transaction content

The primary creates a specially-named queue for each transaction (the tx-queue)

TransactionObserver operations:

- publish(queue, msg): push enqueue event onto tx-queue
- accept(accumulatedAck, unacked) push dequeue event onto tx-queue
  (NOTE: must translate queue positions to replication ids)

The tx-queue is replicated like a normal queue with some extensions for transactions.

- Backups create a `TransactionReplicator` (extends `QueueReplicator`)
  - `QueueReplicator` builds up a `TxBuffer` or `DtxBuffer` of transaction events.
- Primary has `TxReplicatingSubscription` (extends `ReplicatingSubscription`


Using a tx-queue has the following benefits:

- Already have the tools to replicate it.
- Handles async fan-out of transactions to multiple Backups.
- keeps the tx events in linear order even if the tx spans multiple queues.
- Keeps tx data available for new backups until the tx is closed
- By closing the queue (see next section) its easy to establish what backups are
   in/out of the transaction.

## DTX Prepare

Primary receives dtx.prepare:

- "closes" the tx-queue
  - A closed queue rejects any attempt to subscribe.
  - Backups subscribed before the close are in the transaction.
- Puts prepare event on tx-queue, and does local prepare
- Returns ok if outcome of local and all backup prepares is ok, fail otherwise.

*TODO*: this means we need asynchronous completion of the prepare control
so we complete only when all backups have responded (or time out.)

Backups receiving prepare event do a local prepare.  Outcome is communicated to
the TxReplicatingSubscription on the primary as follows:

- ok: Backup acknowledges prepare event message on the tx-queue
- fail: Backup cancels tx-queue subscription

## DTX Commit/Rollback

Primary receives dtx.commit/rollback

- Primary puts commit/rollback event on the tx-queue and does local commit/rollback.
- Backups commit/rollback as instructed and unsubscribe from tx-queue.
- tx-queue auto deletes when last backup unsubscribes.

## TX Commit/Rollback

Primary receives tx.commit/rollback

- Do prepare phase as for DTX.
- Do commit/rollback phase as for DTX.

## De-duplication

When tx commits, each broker (backup & primary) pushes tx messages to the local queue.

On the primary, ReplicatingSubscriptions will see the tx messages on the local
queue. Need to avoid re-sending to backups that already have the messages from
the transaction.

ReplicatingSubscriptions has a "skip set" of messages already on the backup,
add tx messages to the skip set before Primary commits to local queue.

## Failover

Backups abort all open tx if the primary fails.

## Atomicity

Keep tx atomic when backups catch up while a tx is in progress.
A `ready` backup should never contain a proper subset of messages in a transaction.

Scenario:

- Guard placed on Q for an expected backup.
- Transaction with messages A,B,C on Q is prepared, tx-queue is closed.
- Expected backup connects and is declared ready due to guard.
- Transaction commits, primary publishes A, B to Q and crashes before adding C.
- Backup is ready so promoted to new primary with a partial transaction A,B.

*TODO*: Not happy with the following solution.

Solution: Primary delays `ready` status till full tx is replicated.

- When a new backup joins it is considered 'blocked' by any TX in progress.
  - create a TxTracker per queue, per backup at prepare, before local commit.
  - TxTracker holds set of enqueues & dequeues in the tx.
  - ReplicatingSubscription removes enqueus & dequeues from TxTracker as they are replicated.
- Backup starts to catch up as normal but is not granted ready till:
  - catches up on it's guard (as per normal catch-up) AND
  - all TxTrackers are clear.

NOTE: needs thougthful locking to correctly handle

- multiple queues per tx
- multiple tx per queue
- multiple TxTrackers per queue per per backup
- synchronize backups in/out of transaction wrt setting trackers.

*TODO*: A blocking TX eliminates the benefit of the queue guard for expected backups.

- It won't be ready till it has replicated all guarded positions up to the tx.
- Could severely delay backups becoming ready after fail-over if queues are long.

## Recovery

*TODO*

## Testing

New tests:

- TX transaction tests in python.
- TX failover stress test (c.f. `test_failover_send_receive`)

Existing tests:

- JMS transaction tests (DTX & TX)
- `qpid/tests/src/py/qpid_tests/broker_0_10/dtx.py,tx.py` run against HA broker.

