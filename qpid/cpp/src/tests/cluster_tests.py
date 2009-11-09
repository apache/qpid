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

import os, signal, sys, time
from threading import Thread
from brokertest import *
from qpid import datatypes, messaging
from qpid.harness import Skipped
from qpid.messaging import Message


class ClusterTests(BrokerTest):
    """Cluster tests with support for testing with a store plugin."""

    def test_message_replication(self):
        """Test basic cluster message replication."""
        # Start a cluster, send some messages to member 0.
        cluster = self.cluster(2)
        s0 = cluster[0].connect().session()
        s0.sender("q {create:always}").send(messaging.Message("x"))
        s0.sender("q {create:always}").send(messaging.Message("y"))
        s0.connection.close()

        # Verify messages available on member 1.
        s1 = cluster[1].connect().session()
        m = s1.receiver("q", capacity=1).fetch(timeout=1)
        s1.acknowledge()
        self.assertEqual("x", m.content)
        s1.connection.close()

        # Start member 2 and verify messages available.
        s2 = cluster.start().connect().session()
        m = s2.receiver("q", capacity=1).fetch(timeout=1)
        s2.acknowledge()
        self.assertEqual("y", m.content)
        s2.connection.close()

    def test_failover(self):
        """Test fail-over during continuous send-receive"""
        # FIXME aconway 2009-11-09: this test is failing, showing lost messages.
        # Enable when fixed

        # Original cluster will all be killed so expect exit with failure
        cluster = self.cluster(3, expect=EXPECT_EXIT_FAIL)

        # Start sender and receiver threads
        cluster[0].declare_queue("test-queue")
        self.receiver = Receiver(cluster[1])
        self.receiver.start()
        self.sender = Sender(cluster[2])
        self.sender.start()

        # Kill original brokers, start new ones.
        for i in range(3):
            cluster[i].kill()
            cluster.start()
            time.sleep(1)

        self.sender.stop()
        self.receiver.stop(self.sender.sent)


class ClusterStoreTests(BrokerTest):
    """
    Cluster tests that can only be run if there is a store available.
    """
    args = ["--load-module",BrokerTest.store_lib]

    def test_store_loaded(self):
        """Ensure we are indeed loading a working store"""
        broker = self.broker(self.args, name="recoverme", expect=EXPECT_EXIT_FAIL)
        m = messaging.Message("x", durable=True)
        broker.send_message("q", m)
        broker.kill()
        broker = self.broker(self.args, name="recoverme")
        self.assertEqual("x", broker.get_message("q").content)

    def test_kill_restart(self):
        """Verify we can kill/resetart a broker with store in a cluster"""
        cluster = self.cluster(1, self.args)
        cluster.start("restartme", expect=EXPECT_EXIT_FAIL).kill()

        # Send a message, retrieve from the restarted broker
        cluster[0].send_message("q", "x")
        m = cluster.start("restartme").get_message("q")
        self.assertEqual("x", m.content)

    def test_total_shutdown(self):
        """Test we use the correct store to recover after total shutdown"""
        cluster = self.cluster(2, args=self.args, expect=EXPECT_EXIT_FAIL)
        cluster[0].send_message("q", Message("a", durable=True))
        cluster[0].kill()
        self.assertEqual("a", cluster[1].get_message("q").content)
        cluster[1].send_message("q", Message("b", durable=True))
        cluster[1].kill()

        # Start 1 first, we should see its store used.
        cluster.start(name=cluster.name+"-1")
        cluster.start(name=cluster.name+"-0")
        self.assertEqual("b", cluster[2].get_message("q").content)

        
    
