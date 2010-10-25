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
from qpid.brokertest import *
from qpid.harness import Skipped
from qpid.messaging import Message
from qpid.messaging.exceptions import Empty
from threading import Thread, Lock
from logging import getLogger
from itertools import chain

log = getLogger("qpid.cluster_tests")

class Cluster2Tests(BrokerTest):
    """Tests for new cluster code."""

    def test_message_enqueue(self):
        """Test basic replication of enqueued messages."""

        cluster = self.cluster(2, cluster2=True, args=["--log-enable=trace+:cluster"])

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
        def check(content, receiver):
            for c in content: self.assertEqual(c, receiver.fetch(1).content)
            self.assertRaises(Empty, receiver.fetch, 0)

        check(content, r0p)
        check(content, r0q)
        check(content, r1p)
        check(content, r1q)

        sn1.connection.close()
        sn0.connection.close()
