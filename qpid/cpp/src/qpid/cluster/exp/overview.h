// This file is documentation in doxygen format.
/**

<h1>New Cluster Implementation Overview</h>

<h2>Terms and Naming Conventions</h2>

Deliver thread: Thread that is dispatching delivered CPG events.

Broker thread: Thread that is serving a Broker connection.

Local/remote: For a given action the "local" broker is the one
directly connected to the client in question. The others are "remote".

Contended: a queue is contended if it has subscribes on two different
brokers. It is uncontended if all subscribers are on the same broker.

There are 3 types of classes indicated by a suffix on class names:

- *Handler: Implements a class from cluster.xml. Called only in the
   CPG deliver thread to dispatch an XML controls. Calls on *Replica
   or *Context objects.

- *Replica: Holds state that is replicated to the entire cluster.
  Called only *Handler in the CPG deliver thread. May call on *Context
  objects.

- *Context: Holds state private to this member and associated with a
  local object such as the Broker or a Queue. Can be called in CPG
  deliver and broker threads.

f<h2>Overview of Message Lifecycle</h2>

BrokerContext implements the broker::Cluster interface, it is the
point of contact with the Broker.  When a message is delivered locally
the broker calls BrokerContext::enqueue.  This multicasts the message
and delays delivery on the local broker by returning false.

On self-delivery the local broker does the enqueue. This is
synchronized with delivery on the other brokers so that all message
have the same sequence numbers on their queues. We use queue+sequence
no. to identify messages in the cluster.

QueueReplica and QueueContext track the subscriptions for a queue. An
uncontended queue can serve local consumers using the standard broker
scheme. A queue can be "stopped" meaning no consumers can consume from
it, or "active" meaning consumers can consume as on a stand-alone
broker.

An uncontended queue is active. A contended queue is active on only
one broker which is said to hold the "lock" for that queue. It is
stopped on all others. The lock is passed on a timeout.

A broker with an active queue multicasts acquire and dequeue events
for that queue so other brokers can stay in sync. In the case of a
contended queue the broker sends acquire/dequeue events for its
activity before passing the lock.

We use multipe CPG groups to make better use of CPUs. Events about a
given queue always uses the same CPG group, different queues may use
different groups. There is a fixed set of groups, queue names are
hashed to pick the gruop for each queue.

// FIXME aconway 2011-10-31: other schemes than hashing?

**/
