<!--*-markdown-*-->
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

Migrating to new HA
===================

Up to version 0.20, Qpid provided the `cluster` module to support active-active
clustering for Qpid C++ brokers. There were some issues with this module. It
relied on synchronization of too much broker state, so that unrelated changes to
the broker often required additional work on the cluster. It also had a design
that could not take advantage of multiple CPUs - it was effectively single
threaded.

In version 0.20 the `ha` module was introduced supporting active-passive HA
clustering that does not suffer these problems. From version 0.22 the older
`cluster` module is no longer available so users will have to migrate to the new
`ha` module.

This note summarizes the differences between the old and new HA modules and the
steps to migrate to new HA. It assumes you have read the
[HA chapter of the C++ Broker Book][chapter-ha]

Client connections and fail-over
--------------------------------

The old cluster module was active-active: clients could connect to any broker in
the cluster. The new ha module is active-passive. Exactly 1 broker acts as
*primary* the other brokers act as *backup*. Only the primary accepts client
connections. If a client attempts to connect to a *backup* broker, the
connection will be aborted and the client will fail-over until it connects to
the primary. This failover is transparent to the client. See
["Client Connections and Failover"][ha-failover].

The new cluster module also supports a [virtual IP address][ha-virtual-ip].
Clients can be configured with a single IP address that is automatically routed
to the primary broker. This is the recommended configuration.

Migrating configuration options for the old cluster module
----------------------------------------------------------

`cluster-name`: Not needed.

`cluster-size`: Not needed but see "Using a message store in a cluster" below

`cluster-url`: Use `ha-public-url`, which is recommended to use a [virtual IP address][ha-virtual-ip]

`cluster-cman`: Not needed

`cluster-username, cluster-password, cluster-mechanism`: use `ha-username,
ha-password, ha-mechanism`

Configuration options for new HA module
----------------------------------------

`ha-cluster`: set to `yes` to enable clustering.

`ha-brokers-url`: A URL listing each node in the cluster. For example
`amqp:node1.example.com,node2.example.com,node3.example.com`. If you have separate
client and broker networks, the addresses in `ha-brokers-url` should be on the
broker network. Note this URL must list all nodes in the cluster, you cannot use
a [Virtual IP address][ha-virtual-ip] here.

`ha-public-url`: URL used by clients to connect to the cluster. 
This can be one of:

- A [virtual IP address][ha-virtual-ip] configured for your client network.
- A list of broker addresses on the client network. If you don't have a separate
  client network then this is the same as the `ha-brokers-url`

`ha-replicate`: Set to `all` to replicate everything like the old cluster does.
New HA provides more flexibility over what is replicated, see ["Controlling replication of queues and exchanges"][ha-replicate-values].

`ha-username, ha-password, ha-mechanism`: Same as old `cluster-username,
cluster-password, cluster-mechanism`

`ha-backup-timeout`: Maximum time that a recovering primary will wait for an
expected backup to connect and become ready.

`link-maintenance-interval`: Interval in seconds for the broker to check link
health and re-connect links if need be. If you want brokers to fail over quickly
you can set this to a fraction of a second, for example: 0.1.

`link-heartbeat-interval` Heartbeat interval for replication links. The link
will be assumed broken if there is no heartbeat for twice the interval.

Configuring rgmanager
---------------------

The new HA module requires an external cluster resource manager.  Initially it
supports `rgmanager` provided by the `cman` package.  `rgmanager` is responsible
for stopping and starting brokers and determining which broker is promoted to
primary. It is also responsible for detecting primary failures and promoting a
new primary.  See ["Configuring rgmanager as resource manager"][ha-rm-config]

It is relatively easy to integrate with a new resource manager, see
["Integrating with other Cluster Resource Managers"][ha-other-rm]

Broker Administration Tools
---------------------------

 Normally, clients are not allowed to connect to a backup broker. However
 management tools are allowed to connect to a backup brokers. If you use these
 tools you must not add or remove messages from replicated queues, nor create or
 delete replicated queues or exchanges as this will disrupt the replication
 process and may cause message loss or other unpredictable behavior.

qpid-ha allows you to view and change HA configuration settings.

The tools qpid-config, qpid-route and qpid-stat will connect to a backup if you
pass the flag ha-admin on the command line.

Fail-over exchange
------------------

The fail-over exchange is not supported in new HA, use a
[virtual IP address][ha-virtual-ip] instead.

Using a message store in a cluster
----------------------------------

If you use a persistent store for your messages then each broker in a cluster
will have its own store. If the entire cluster fails, when restarting the
*first* broker that becomes primary will recover from its store. All the other
brokers will clear their stores and get an update from the primary to ensure
consistency. See ["Using a message store in a cluster"][ha-store].

Replacing Queue State Replication
---------------------------------

The queue state replication mechanism implemented by the modules `replicating_listener` and `replication_exchange` is no longer available. Instead you should use the queue replication mechanism provided by the `ha`  module as described in the [HA Queue Replication chapter of the C++ Broker Book][ha-queue-replication]




[chapter-ha]: http://qpid.apache.org/books/0.22/AMQP-Messaging-Broker-CPP-Book/html/chapter-ha.html
[ha-failover]: http://qpid.apache.org/books/0.22/AMQP-Messaging-Broker-CPP-Book/html/chapter-ha.html#ha-failover
[ha-virtual-ip]: http://qpid.apache.org/books/0.22/AMQP-Messaging-Broker-CPP-Book/html/chapter-ha.html#ha-virtual-ip
[ha-replicate-values]: http://qpid.apache.org/books/0.22/AMQP-Messaging-Broker-CPP-Book/html/chapter-ha.html#ha-replicate-values
[ha-rm-config]: http://qpid.apache.org/books/0.22/AMQP-Messaging-Broker-CPP-Book/html/chapter-ha.html#ha-rm-config
[ha-queue-replication]: http://qpid.apache.org/releases/qpid-0.22/cpp-broker/book/ha-queue-replication.html
