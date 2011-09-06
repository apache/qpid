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

    def verify_content(self, content, receiver):
        for c in content: self.assertEqual(c, receiver.fetch(1).content)
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
        r1 = sn1.receiver("q;{create:always}") # Not yet replicating wiring.

        content = ["a","b","c"]
        for m in content: s0.send(Message(m))
         # Verify enqueued on cluster[1]
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
