#!/usr/bin/env python

# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

import os, signal, sys, time, imp, re, subprocess
from qpid import datatypes, messaging
from brokertest import *
from qpid.harness import Skipped
from qpid.messaging import Message
from qpid.messaging.exceptions import *
from threading import Thread, Lock
from logging import getLogger
from itertools import chain

log = getLogger("qpid.cluster_tests")

class Cluster2Tests(BrokerTest):
    """Tests for new cluster code."""

    def queue_exists(self, queue, connection):
        s = connection.session()
        try:
            s.sender(queue)
            return True
        except qpid.messaging.exceptions.NotFound:
            return False

    # FIXME aconway 2011-06-22: needed to compensate for
    # async wiring in early cluster2 prototype
    def wait_for_queue(self, queue, connections, timeout=10):
        deadline = time.time() + timeout
        for c in connections:
            while not self.queue_exists(queue,c):
                if time.time() >  timeout: fail("Time out in wait_for_queue(%s))"%queue)
                time.sleep(0.01)

    # FIXME aconway 2011-05-17: remove, use assert_browse.
    def verify_content(self, expect, receiver):
        actual = [receiver.fetch(1).content for x in expect]
        self.assertEqual(expect, actual)
        self.assertRaises(Empty, receiver.fetch, 0)

    def test_message_enqueue(self):
        """Test basic replication of enqueued messages.
        Verify that fanout messages are replicated correctly.
        """

        cluster = self.cluster(2, cluster2=True)

        sn0 = cluster[0].connect().session()
        r0p = sn0.receiver("p; {mode:browse, create:always, node:{x-bindings:[{exchange:'amq.fanout', queue:p}]}}");
        r0q = sn0.receiver("q; {mode:browse, create:always, node:{x-bindings:[{exchange:'amq.fanout', queue:q}]}}");
        s0 = sn0.sender("amq.fanout");

        sn1 = cluster[1].connect().session()
        r1p = sn1.receiver("p; {mode:browse, create:always, node:{x-bindings:[{exchange:'amq.fanout', queue:p}]}}");
        r1q = sn1.receiver("q; {mode:browse, create:always, node:{x-bindings:[{exchange:'amq.fanout', queue:q}]}}");


        # Send messages on member 0
        content = ["a","b","c"]
        for m in content: s0.send(Message(m))

        # Browse on both members.
        self.verify_content(content, r0p)
        self.verify_content(content, r0q)
        self.verify_content(content, r1p)
        self.verify_content(content, r1q)

        sn1.connection.close()
        sn0.connection.close()

    def test_message_dequeue(self):
        """Test replication of dequeues"""
        cluster = self.cluster(2, cluster2=True)
        sn0 = cluster[0].connect().session()
        s0 = sn0.sender("q;{create:always,delete:always}")
        r0 = sn0.receiver("q")
        sn1 = cluster[1].connect().session()
        r1 = sn1.receiver("q;{create:always}")

        content = ["a","b","c"]
        for m in content: s0.send(Message(m))
        # Verify enqueued on members 0 and 1
        self.verify_content(content, sn0.receiver("q;{mode:browse}"))
        self.verify_content(content, sn1.receiver("q;{mode:browse}"))

        # Dequeue on cluster[0]
        self.assertEqual(r0.fetch(1).content, "a")
        sn0.acknowledge(sync=True)

        # Verify dequeued on cluster[0] and cluster[1]
        self.verify_content(["b", "c"], sn0.receiver("q;{mode:browse}"))
        self.verify_content(["b", "c"], sn1.receiver("q;{mode:browse}"))

    def test_wiring(self):
        """Test replication of wiring"""
        cluster = self.cluster(2, cluster2=True)
        sn0 = cluster[0].connect().session()
        sn1 = cluster[1].connect().session()

        # Test creation of queue, exchange, binding
        r0ex = sn0.receiver("ex; {create:always, delete:always, node:{type:topic, x-declare:{name:ex, type:'direct'}}}")
        r0q  = sn0.receiver("q;  {create:always, delete:always, link:{x-bindings:[{exchange:ex,queue:q,key:k}]}}")

        # Verify objects were created on member 1
        r1 = sn1.receiver("q")   # Queue
        s1ex = sn1.sender("ex/k; {node:{type:topic}}"); # Exchange
        s1ex.send(Message("x"))  # Binding with key k
        self.assertEqual(r1.fetch(1).content, "x")

        # Test destroy.
        r0q.close()                                    # Delete queue q
        self.assertRaises(NotFound, sn1.receiver, "q")
        r0ex.close()            # Delete exchange ex
        # FIXME aconway 2010-11-05: this does not raise NotFound, sn1 is caching "ex"
        # self.assertRaises(NotFound, sn1.sender, "ex")
        # Have to create a new session.
        self.assertRaises(NotFound, cluster[1].connect().session().receiver, "ex")

        # FIXME aconway 2010-10-29: test unbind, may need to use old API.

    def duration(self):
        d = self.config.defines.get("DURATION")
        if d: return float(d)*60
        else: return 3                  # Default is to be quick


    def test_dequeue_mutex(self):
        """Ensure that one and only one consumer receives each dequeued message."""
        class Receiver(Thread):
            def __init__(self, session):
                self.session = session
                self.receiver = session.receiver("q")
                self.messages = []
                self.error = None
                Thread.__init__(self)

            def run(self):
                try:
                    while True:
                        self.messages.append(self.receiver.fetch(1))
                        self.session.acknowledge()
                except Empty: pass
                except Exception,e: self.error = e

        cluster = self.cluster(3, cluster2=True)
        connections = [ b.connect() for  b in cluster]
        sessions = [ c.session() for c in connections ]
        sender = sessions[0].sender("q;{create:always}")
        self.wait_for_queue("q", connections)

        receivers = [ Receiver(s) for s in sessions ]
        for r in receivers: r.start()

        n = 0
        t = time.time() + self.duration()
        while time.time() < t:
            sender.send(str(n))
            n += 1
        for r in receivers:
            r.join();
            if (r.error): self.fail("Receiver failed: %s" % r.error)
        for r in receivers:
            len(r.messages) > n/6 # Fairness test.
        messages = [int(m.content) for r in receivers for m in r.messages ]
        messages.sort()
        self.assertEqual(range(n), messages)
