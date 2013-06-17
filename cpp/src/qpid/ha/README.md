
Overview of HA replication
==========================

Message Identifiers
-------------------

Replication IDs are sequence numbers assigned to messages *before* a message is
enqueued.  Originally the queue position number was used, but that was
insufficient for two reasons:
-  We sometimes need to identify messages that are not yet enqueued, for example messages in an open transaction.
- We don't want to require maintaining identical message sequences on every broker e.g. so transactions can be committed independently by each broker.

We use the IDs to:
- identify messages to dequeue on a backup.
- remove extra messages from backup on failover.
- avoid downloading messages already on backup on failover.


On the primary
--------------

The main classes on the primary are as follows:

`RemoteBackup`: Represents a remote backup broker. Container for per-queue
information about the broker.

Each (queue,backup) pair has an instance of either or both of the following
classes:

`QueueGuard`: A queue observer that delays completion of messages as they are
enqueued and completes messages when they are acknowledged or dequeued.

`RepicatingSubscription`: A queue browser that sends messages to the backup and
receives acknowledgments. Forwards acknowledgments to the `QueueGuard`

`ReplicatingSubscription` and `QueueGuard` are separate because the guard
can be created before the subscription.

Events intercepted by HA code:

- enqueue: Message published to queue, completion delayed (QueueGuard)
- deliver: Message delivered to ReplicatingSubscription and sent to backup.
- acknowledge: Message acknowledged by backup (ReplicatingSubscription)
- dequeue: Message removed from queue by a consumer (QueueGuard)

Message states:
- new: initial state.
- sent: ReplicatingSubscription has sent message to backup.
- delayed: QueueGuard has delayed completion.
- delayed-sent: Both sent and delayed.
- safe: Replication code is done with the message: it is acknowledged or dequeued.

Events:
- enqueue: message enqueue on queue
- deliver: message delivered to ReplicatingSubscription
- acknowledged: message is acknowledged by backup
- dequeued: message is dequeued by consumer.

State transition diagram:

    (new)--deliver-->(sent)--acknowledged/dequeued---------------->(safe)
      |                 L---dequeued- -------------------------------^
      L-enqueue->(delayed)--dequeued---------------------------------|
                  |                                                  |
                  L--deliver->(delayed-sent)--acknowled/dequeued-----|


A QueueGuard is set on the queue when a backup subscribes or when a backup is
promoted. Messages before the _first guarded position_ cannot be delayed
because they may have already been acknowledged to clients.

A backup sends a set of pre-acknowledged messages when subscribing, messages
that are already on the backup and therefore safe.

A `ReplicatingSubscription` is _ready_ when all messages are safe or delayed.  We
know this is the case when all the following conditions hold:

- The `ReplicatingSubscription` has reached the position preceeding the first guarded position AND
- All messages prior to the first guarded position are safe.
