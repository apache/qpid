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

import os, signal, sys, time, imp, re
from qpid import datatypes, messaging
from qpid.brokertest import *
from qpid.harness import Skipped
from qpid.messaging import Message
from threading import Thread
from logging import getLogger

log = getLogger("qpid.cluster_tests")

# Import scripts as modules
qpid_cluster=import_script(checkenv("QPID_CLUSTER_EXEC"))

def readfile(filename):
    """Returns te content of file named filename as a string"""
    f = file(filename)
    try: return f.read()
    finally: f.close()

class ShortTests(BrokerTest):
    """Short cluster functionality tests."""

    def test_message_replication(self):
        """Test basic cluster message replication."""
        # Start a cluster, send some messages to member 0.
        cluster = self.cluster(2)
        s0 = cluster[0].connect().session()
        s0.sender("q; {create:always}").send(Message("x"))
        s0.sender("q; {create:always}").send(Message("y"))
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

    def test_store_direct_update_match(self):
        """Verify that brokers stores an identical message whether they receive it
        direct from clients or during an update, no header or other differences"""
        cluster = self.cluster(0, args=["--load-module", self.test_store_lib])
        cluster.start(args=["--test-store-dump", "direct.dump"])
        # Try messages with various headers
        cluster[0].send_message("q", Message(durable=True, content="foobar",
                                             subject="subject",
                                             reply_to="reply_to",
                                             properties={"n":10}))
        # Try messages of different sizes
        for size in range(0,10000,100):
            cluster[0].send_message("q", Message(content="x"*size, durable=True))
        # Try sending via named exchange
        c = cluster[0].connect_old()
        s = c.session(str(qpid.datatypes.uuid4()))
        s.exchange_bind(exchange="amq.direct", binding_key="foo", queue="q")
        props = s.delivery_properties(routing_key="foo", delivery_mode=2)
        s.message_transfer(
            destination="amq.direct",
            message=qpid.datatypes.Message(props, "content"))

        # Now update a new member and compare their dumps.
        cluster.start(args=["--test-store-dump", "updatee.dump"])
        assert readfile("direct.dump") == readfile("updatee.dump")
        os.remove("direct.dump")
        os.remove("updatee.dump")
        
    def test_config_change_seq(self):
        """Check that cluster members have the correct config change sequence numbers"""
        cluster = self.cluster(0)
        cluster.start()
        cluster.start(expect=EXPECT_EXIT_OK)
        cluster[1].terminate(); cluster[1].wait()
        cluster.start()

        update_re = re.compile(r"member update: (.*) frameSeq=[0-9]+ configSeq=([0-9]+)")
        matches = [ update_re.search(readfile(b.log)) for b in cluster ]
        sequences = [ m.group(2) for m in matches]
        self.assertEqual(sequences, ["0", "1", "3"])

        # Check that configurations with same seq. number match
        configs={}
        for b in cluster:
            matches = update_re.findall(readfile(b.log))
            for m in matches:
                seq=m[1]
                config=re.sub("\((member|unknown)\)", "", m[0])
                if not seq in configs: configs[seq] = config
                else: self.assertEqual(configs[seq], config)

class LongTests(BrokerTest):
    """Tests that can run for a long time if -DDURATION=<minutes> is set"""
    def duration(self):
        d = self.config.defines.get("DURATION")
        if d: return float(d)*60
        else: return 3                  # Default is to be quick

    def test_failover(self):
        """Test fail-over during continuous send-receive with errors"""

        # Original cluster will all be killed so expect exit with failure
        cluster = self.cluster(3, expect=EXPECT_EXIT_FAIL)
        for b in cluster: ErrorGenerator(b)

        # Start sender and receiver threads
        cluster[0].declare_queue("test-queue")
        sender = NumberedSender(cluster[1], 1000) # Max queue depth
        receiver = NumberedReceiver(cluster[2], sender)
        receiver.start()
        sender.start()

        # Kill original brokers, start new ones for the duration.
        endtime = time.time() + self.duration()
        i = 0
        while time.time() < endtime:
            cluster[i].kill()
            i += 1
            b = cluster.start(expect=EXPECT_EXIT_FAIL)
            ErrorGenerator(b)
            time.sleep(1)
        sender.stop()
        receiver.stop(sender.sent)
        for i in range(i, len(cluster)): cluster[i].kill()

    def test_management(self):
        """Run management in conjunction with other traffic."""
        # Publish often to provoke errors
        args=["--mgmt-pub-interval", 1]
        # Use store if present
        if BrokerTest.store_lib: args +=["--load-module", BrokerTest.store_lib]

        class ClientLoop(StoppableThread):
            """Run an infinite client loop."""
            def __init__(self, broker, cmd):
                StoppableThread.__init__(self)
                self.broker=broker
                self.cmd = cmd
                self.lock = Lock()
                self.process = None
                self.stopped = False
                self.start()

            def run(self):
                try:
                    while True:
                        self.lock.acquire()
                        try:
                            if self.stopped: break
                            self.process = self.broker.test.popen(self.cmd,
                                                                  expect=EXPECT_UNKNOWN)
                        finally: self.lock.release()
                        try: exit = self.process.wait()
                        except: exit = 1
                        self.lock.acquire()
                        try:
                            if exit != 0 and not self.stopped:
                                self.process.unexpected("bad exit status in client loop")
                        finally: self.lock.release()
                except Exception, e:
                    error=e

            def stop(self):
                self.lock.acquire()
                try:
                    self.stopped = True
                    try: self.process.terminate()
                    except: pass
                finally: self.lock.release()
                StoppableThread.stop(self)
        cluster = self.cluster(3, args)
        clients = []
        for b in cluster:
            clients.append(ClientLoop(b, ["perftest", "--count", "100", "--port", b.port()]))
            clients.append(ClientLoop(b, ["qpid-queue-stats", "-a", "localhost:%s" %(b.port())]))
        endtime = time.time() + self.duration()
        while time.time() < endtime:
            for b in cluster: b.ready() # Will raise if broker crashed.
            time.sleep(1)
        for c in clients:
            c.stop()

class StoreTests(BrokerTest):
    """
    Cluster tests that can only be run if there is a store available.
    """
    def args(self):
        assert BrokerTest.store_lib 
        return ["--load-module", BrokerTest.store_lib]

    def test_store_loaded(self):
        """Ensure we are indeed loading a working store"""
        broker = self.broker(self.args(), name="recoverme", expect=EXPECT_EXIT_FAIL)
        m = Message("x", durable=True)
        broker.send_message("q", m)
        broker.kill()
        broker = self.broker(self.args(), name="recoverme")
        self.assertEqual("x", broker.get_message("q").content)

    def test_kill_restart(self):
        """Verify we can kill/resetart a broker with store in a cluster"""
        cluster = self.cluster(1, self.args())
        cluster.start("restartme", expect=EXPECT_EXIT_FAIL).kill()

        # Send a message, retrieve from the restarted broker
        cluster[0].send_message("q", "x")
        m = cluster.start("restartme").get_message("q")
        self.assertEqual("x", m.content)

    def test_persistent_restart(self):
        """Verify persistent cluster shutdown/restart scenarios"""
        cluster = self.cluster(0, args=self.args() + ["--cluster-size=3"])
        a = cluster.start("a", expect=EXPECT_EXIT_OK, wait=False)
        b = cluster.start("b", expect=EXPECT_EXIT_OK, wait=False)
        c = cluster.start("c", expect=EXPECT_EXIT_FAIL, wait=True)
        a.send_message("q", Message("1", durable=True))
        # Kill & restart one member.
        c.kill()
        self.assertEqual(a.get_message("q").content, "1")
        a.send_message("q", Message("2", durable=True))
        c = cluster.start("c", expect=EXPECT_EXIT_OK)
        self.assertEqual(c.get_message("q").content, "2")
        # Shut down the entire cluster cleanly and bring it back up
        a.send_message("q", Message("3", durable=True))
        self.assertEqual(0, qpid_cluster.main(["qpid-cluster", "-kf", a.host_port()]))
        a = cluster.start("a", wait=False)
        b = cluster.start("b", wait=False)
        c = cluster.start("c", wait=True)
        self.assertEqual(a.get_message("q").content, "3")

    def test_persistent_partial_failure(self):
        # Kill 2 members, shut down the last cleanly then restart
        # Ensure we use the clean database
        cluster = self.cluster(0, args=self.args() + ["--cluster-size=3"])
        a = cluster.start("a", expect=EXPECT_EXIT_FAIL, wait=False)
        b = cluster.start("b", expect=EXPECT_EXIT_FAIL, wait=False)
        c = cluster.start("c", expect=EXPECT_EXIT_OK, wait=True)
        a.send_message("q", Message("4", durable=True))
        a.kill()
        b.kill()
        self.assertEqual(c.get_message("q").content, "4")
        c.send_message("q", Message("clean", durable=True))
        self.assertEqual(0, qpid_cluster.main(["qpid-cluster", "-kf", c.host_port()]))
        a = cluster.start("a", wait=False)
        b = cluster.start("b", wait=False)
        c = cluster.start("c", wait=True)
        self.assertEqual(a.get_message("q").content, "clean")
        
    def test_wrong_cluster_id(self):
        # Start a cluster1 broker, then try to restart in cluster2
        cluster1 = self.cluster(0, args=self.args())
        a = cluster1.start("a", expect=EXPECT_EXIT_OK)
        a.terminate()
        cluster2 = self.cluster(1, args=self.args())
        try:
            a = cluster2.start("a", expect=EXPECT_EXIT_OK)
            a.ready()
            self.fail("Expected exception")
        except: pass

    def test_wrong_shutdown_id(self):
        # Start 2 members and shut down.
        cluster = self.cluster(0, args=self.args()+["--cluster-size=2"])
        a = cluster.start("a", expect=EXPECT_EXIT_OK, wait=False)
        b = cluster.start("b", expect=EXPECT_EXIT_OK, wait=False)
        self.assertEqual(0, qpid_cluster.main(["qpid_cluster", "-kf", a.host_port()]))
        self.assertEqual(a.wait(), 0)
        self.assertEqual(b.wait(), 0)

        # Restart with a different member and shut down.
        a = cluster.start("a", expect=EXPECT_EXIT_OK, wait=False)
        c = cluster.start("c", expect=EXPECT_EXIT_OK, wait=False)
        self.assertEqual(0, qpid_cluster.main(["qpid_cluster", "-kf", a.host_port()]))
        self.assertEqual(a.wait(), 0)
        self.assertEqual(c.wait(), 0)

        # Mix members from both shutdown events, they should fail
        a = cluster.start("a", expect=EXPECT_EXIT_OK, wait=False)
        b = cluster.start("b", expect=EXPECT_EXIT_OK, wait=False)
        self.assertRaises(Exception, lambda: a.ready())
        self.assertRaises(Exception, lambda: b.ready())

    def test_total_failure(self):
        # Verify we abort with sutiable error message if no clean stores.
        cluster = self.cluster(0, args=self.args()+["--cluster-size=2"])
        a = cluster.start("a", expect=EXPECT_EXIT_FAIL, wait=False)
        b = cluster.start("b", expect=EXPECT_EXIT_FAIL, wait=True)
        a.kill()
        b.kill()
        a = cluster.start("a", expect=EXPECT_EXIT_OK, wait=False)
        b = cluster.start("b", expect=EXPECT_EXIT_OK, wait=False)
        self.assertRaises(Exception, lambda: a.ready())
        self.assertRaises(Exception, lambda: b.ready())
        msg = re.compile("critical.*no clean store")
        assert msg.search(readfile(a.log))
        assert msg.search(readfile(b.log))

        # FIXME aconway 2009-12-03: verify manual restore procedure


