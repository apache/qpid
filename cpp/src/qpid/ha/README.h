// This header file provides an overview of the ha module for doxygen.

namespace qpid {
namespace ha { // Auto-links for names in the ha namespace.

/** \defgroup ha-module
    \brief High Availability Module

This is a brief overview of how HA replication works, intended as a starting
point to understand the code. You should _first_ read the HA chapter of the "C++
broker book" at http://qpid.apache.org/documentation.html for a general
understanding of the active/passive, hot-standby strategy.

Class names without a prefix are in the qpid::ha namespace. See the
documentation of individual classes for more detail.

## Overview of HA classes. ##

HaBroker is the main container for HA code, it holds a Role which can be Primary
or Backup. It is responsible for implementing QMF management functions.

Backup implements the back-up Role. It holds a BrokerReplicator which subscribes
to management events on the primary e.g. create/delete queue/exchange/binding.
It uses a ReplicationTest to check if an object is configured for replication.
For replicated objects it creates corresponding broker::Queue, broker::Exchange
etc. on the local broker.

For replicated queues the BrokerReplicator also creates a QueueReplicator.  The
QueueReplicator subscribes to the primary queue with arguments to tell the
primary to use a ReplicatingSubscription.  See "Queue Replication" below for
more on the ReplicatingSubscription/QueueReplicator interaction.

Primary implements the primary Role. For each replicated queue it creates an
IdSetter to set replication IDs on messages delivered to the queue. Each backup
that connects is tracked by a RemoteBackup.

For each (queue,backup) pair, the Primary creates a ReplicatingSubscription and
a QueueGuard. The QueueGuard delays completion of messages delivered to the
queue until they are replicated to and acknowledged by the backup.

When the primary fails, one of the backups is promoted by an external cluster
resource manager. The other backups fail-over to the new primary. See "Queue
Fail Over" below.

## Locating the Primary ##

Clients connect to the cluster via one of:
- a virtual IP address that is assigned to the primary broker
- a list of addresses for all brokers. 

If the connection fails the client re-tries its address or list of addresses
till it re-connects.  Backup brokers have a ConnectionObserver that rejects
client connections by aborting the connection, causing the client to re-try.

## Message Identifiers ##

Replication IDs are sequence numbers assigned to a message *before* it is
enqueued. We use the IDs to:
- identify messages to dequeue on a backup.
- remove extra messages from backup on failover.
- avoid downloading messages already on backup on failover.

Aside: Originally the queue position number was used as the ID, but that was
insufficient for two reasons:
-  We sometimes need to identify messages that are not yet enqueued, for example messages in an open transaction.
- We don't want to require maintaining identical message sequences on every broker e.g. so transactions can be committed independently by each broker.

## At-least-once and delayed completion. ##

We guarantee no message loss, aka at-least-once delivery. Any message received
_and acknowledged_ by the primary will not be lost. Clients must keep sent
messages until they are acknowledged. In a failure the client re-connects and
re-sends all unacknowledged (or "in-doubt") messages. 

This ensures no messages are lost, but it is possible for in-doubt messages to
be duplicated if the broker did receive them but failed before sending the
acknowledgment. It is up to the application to handle duplicates correctly.

We implement the broker's side of the bargain using _delayed completion_.  When
the primary receives a message it does not complete (acknowledge) the message
until the message is replicated to and acknowledged by all the backups.  In the
case of persistent messages, that includes writing the message to persistent
store.

## Queue Replication ##

Life-cycle of a message on a replicated queue, on the primary:

Record: Received for primary queue.
- IdSetter sets a replication ID.

Enqueue: Published to primary queue.
- QueueGuard delays completion (increments completion count).

Deliver: Backup is ready to receive data.
- ReplicatingSubscription sends message to backup QueueReplicator

Acknowledge: Received from backup QueueReplicator.
- ReplicatingSubscription completes (decrements completion count).

Dequeue: Removed from queue
- QueueGuard completes if not already completed by ReplicatingSubscription.
- ReplicatingSubscription sends dequeued message ID to backup QueueReplicator.

There is a simple protocol between ReplicatingSubscription and QueueReplicator
(see Event sub-classes) so ReplicatingSubscription can send
- replication IDs of messages as they are replicated.
- replication IDs of messages that have been dequeued on the primary.

## Queue Failover ##

On failover, for each queue, the backup gets the QueueSnapshot of messages on
the queue and sends it with the subscription to the primary queue. The primary
responds with its own snapshot.

Both parties use this information to determine:

- Messages that are on the backup and don't need to be replicated again.
- Messages that are dequeued on the primary and can be discarded by the backup.
- Queue position for the QueueGuard to start delaying completion.
- Queue position for the ReplicatingSubscription to start replicating from.

After that handshake queue replication as described above begins.

## Standalone replication ##

The HA module supports ad-hoc replication between standalone brokers that are
not in a cluster, using the same basic queue replication techniques.

## Queue and Backup "ready" ##

A QueueGuard is set on the queue when a backup first subscribes or when a backup
fails over to a new primary. Messages before the _first guarded position_ cannot
be delayed because they may have already been acknowledged to clients.

A backup sends a set of acknowledged message ids when subscribing, messages that
are already on the backup and therefore safe.

A ReplicatingSubscription is _ready_ when all messages are safe or delayed.  We
know this is the case when both the following conditions hold:

- The ReplicatingSubscription has reached the position preceding the first guarded position AND
- All messages prior to the first guarded position are safe.


*/
/** \namespace qpid::ha \brief High Availability \ingroup ha-module */
}} // namespace qpid::ha

