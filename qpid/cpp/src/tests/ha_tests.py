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

import os, signal, sys, time, imp, re, subprocess, glob, random, logging, shutil, math, unittest
import traceback
from qpid.datatypes import uuid4, UUID
from brokertest import *
from ha_test import *
from threading import Thread, Lock, Condition
from logging import getLogger, WARN, ERROR, DEBUG, INFO
from qpidtoollibs import BrokerAgent, EventHelper

log = getLogger(__name__)

class HaBrokerTest(BrokerTest):
    """Base class for HA broker tests"""

class ReplicationTests(HaBrokerTest):
    """Correctness tests for  HA replication."""

    def test_replication(self):
        """Test basic replication of configuration and messages before and
        after backup has connected"""

        def setup(prefix, primary):
            """Create config, send messages on the primary p"""
            a = primary.agent

            def queue(name, replicate):
                a.addQueue(name, options={'qpid.replicate':replicate})
                return name

            def exchange(name, replicate, bindq, key):
                a.addExchange("fanout", name, options={'qpid.replicate':replicate})
                a.bind(name, bindq, key)
                return name

            # Test replication of messages
            p = primary.connect().session()
            s = p.sender(queue(prefix+"q1", "all"))
            for m in ["a", "b", "1"]: s.send(qm.Message(m))
            # Test replication of dequeue
            self.assertEqual(p.receiver(prefix+"q1").fetch(timeout=0).content, "a")
            p.acknowledge()

            p.sender(queue(prefix+"q2", "configuration")).send(qm.Message("2"))
            p.sender(queue(prefix+"q3", "none")).send(qm.Message("3"))
            p.sender(exchange(prefix+"e1", "all", prefix+"q1", "key1")).send(qm.Message("4"))
            p.sender(exchange(prefix+"e2", "configuration", prefix+"q2", "key2")).send(qm.Message("5"))
            # Test  unbind
            p.sender(queue(prefix+"q4", "all")).send(qm.Message("6"))
            s3 = p.sender(exchange(prefix+"e4", "all", prefix+"q4", "key4"))
            s3.send(qm.Message("7"))
            a.unbind(prefix+"e4", prefix+"q4", "key4")
            p.sender(prefix+"e4").send(qm.Message("drop1")) # Should be dropped

            # Test replication of deletes
            queue(prefix+"dq", "all")
            exchange(prefix+"de", "all", prefix+"dq", "")
            a.delQueue(prefix+"dq")
            a.delExchange(prefix+"de")

            # Need a marker so we can wait till sync is done.
            queue(prefix+"x", "configuration")

        def verify(b, prefix, p):
            """Verify setup was replicated to backup b"""
            # Wait for configuration to replicate.
            wait_address(b.connection, prefix+"x");
            self.assert_browse_retry(b, prefix+"q1", ["b", "1", "4"])

            self.assertEqual(p.receiver(prefix+"q1").fetch(timeout=0).content, "b")
            p.acknowledge()
            self.assert_browse_retry(b, prefix+"q1", ["1", "4"])

            self.assert_browse_retry(b, prefix+"q2", []) # configuration only
            assert not valid_address(b.connection, prefix+"q3")

            # Verify exchange with replicate=all
            b.sender(prefix+"e1/key1").send(qm.Message(prefix+"e1"))
            self.assert_browse_retry(b, prefix+"q1", ["1", "4", prefix+"e1"])

            # Verify exchange with replicate=configuration
            b.sender(prefix+"e2/key2").send(qm.Message(prefix+"e2"))
            self.assert_browse_retry(b, prefix+"q2", [prefix+"e2"])

            b.sender(prefix+"e4/key4").send(qm.Message("drop2")) # Verify unbind.
            self.assert_browse_retry(b, prefix+"q4", ["6","7"])

            # Verify deletes
            assert not valid_address(b.connection, prefix+"dq")
            assert not valid_address(b.connection, prefix+"de")

        l = LogLevel(ERROR) # Hide expected WARNING log messages from failover.
        try:
            cluster = HaCluster(self, 2)
            primary = cluster[0]
            backup  = cluster[1]

            # Send messages before re-starting the backup, test catch-up replication.
            cluster.kill(1, promote_next=False, final=False)
            setup("1", primary)
            cluster.restart(1)

            # Send messages after re-starting the backup, to test steady-state replication.
            setup("2", primary)

            p = primary.connect().session()

            # Verify the data on the backup
            b = backup.connect_admin().session()
            verify(b, "1", p)
            verify(b, "2", p)
            # Test a series of messages, enqueue all then dequeue all.
            primary.agent.addQueue("foo")
            s = p.sender("foo")
            wait_address(b.connection, "foo")
            msgs = [str(i) for i in range(10)]
            for m in msgs: s.send(qm.Message(m))
            self.assert_browse_retry(p, "foo", msgs)
            self.assert_browse_retry(b, "foo", msgs)
            r = p.receiver("foo")
            for m in msgs: self.assertEqual(m, r.fetch(timeout=0).content)
            p.acknowledge()
            self.assert_browse_retry(p, "foo", [])
            self.assert_browse_retry(b, "foo", [])

            # Another series, this time verify each dequeue individually.
            for m in msgs: s.send(qm.Message(m))
            self.assert_browse_retry(p, "foo", msgs)
            self.assert_browse_retry(b, "foo", msgs)
            for i in range(len(msgs)):
                self.assertEqual(msgs[i], r.fetch(timeout=0).content)
                p.acknowledge()
                self.assert_browse_retry(p, "foo", msgs[i+1:])
                self.assert_browse_retry(b, "foo", msgs[i+1:])
        finally: l.restore()

    def test_sync(self):
        primary = HaBroker(self, name="primary")
        primary.promote()
        p = primary.connect().session()
        s = p.sender("q;{create:always}")
        for m in [str(i) for i in range(0,10)]: s.send(m)
        s.sync()
        backup1 = HaBroker(self, name="backup1", brokers_url=primary.host_port())
        for m in [str(i) for i in range(10,20)]: s.send(m)
        s.sync()
        backup2 = HaBroker(self, name="backup2", brokers_url=primary.host_port())
        for m in [str(i) for i in range(20,30)]: s.send(m)
        s.sync()

        msgs = [str(i) for i in range(30)]
        b1 = backup1.connect_admin().session()
        backup1.assert_browse_backup("q", msgs)
        backup2.assert_browse_backup("q", msgs)

    def test_send_receive(self):
        """Verify sequence numbers of messages sent by qpid-send"""
        l = LogLevel(ERROR) # Hide expected WARNING log messages from failover.
        try:
            brokers = HaCluster(self, 3)
            sender = self.popen(
                ["qpid-send",
                 "--broker", brokers[0].host_port(),
                 "--address", "q;{create:always}",
                 "--messages=1000",
                 "--content-string=x",
                 "--connection-options={%s}"%self.protocol_option()
                 ])
            receiver = self.popen(
                ["qpid-receive",
                 "--broker", brokers[0].host_port(),
                 "--address", "q;{create:always}",
                 "--messages=990",
                 "--timeout=10",
                 "--connection-options={%s}"%self.protocol_option()
                 ])
            self.assertEqual(sender.wait(), 0)
            self.assertEqual(receiver.wait(), 0)
            expect = [long(i) for i in range(991, 1001)]
            sn = lambda m: m.properties["sn"]
            brokers[1].assert_browse_backup("q", expect, transform=sn)
            brokers[2].assert_browse_backup("q", expect, transform=sn)
        finally: l.restore()

    def test_failover_python(self):
        """Verify that backups rejects connections and that fail-over works in python client"""
        l = LogLevel(ERROR) # Hide expected WARNING log messages from failover.
        try:
            primary  = HaBroker(self, name="primary")
            primary.promote()
            backup = HaBroker(self, name="backup", brokers_url=primary.host_port())
            # Check that backup rejects normal connections
            try:
                backup.connect().session()
                self.fail("Expected connection to backup to fail")
            except qm.ConnectionError: pass
            # Check that admin connections are allowed to backup.
            backup.connect_admin().close()

            # Test discovery: should connect to primary after reject by backup
            c = backup.connect(reconnect_urls=[primary.host_port(), backup.host_port()],
                               reconnect=True)
            s = c.session()
            sender = s.sender("q;{create:always}")
            sender.send("foo", sync=True)
            s.sync()
            primary.kill()
            assert retry(lambda: not is_running(primary.pid))
            backup.promote()
            sender.send("bar")
            self.assert_browse_retry(s, "q", ["foo", "bar"])
            c.close()
        finally: l.restore()


    def test_heartbeat_python(self):
        """Verify that a python client with a heartbeat specified disconnects
        from a stalled broker and does not hang indefinitely."""

        broker = Broker(self)
        broker_addr = broker.host_port()

        # Case 1: Connect before stalling the broker, use the connection after stalling.
        c = qm.Connection(broker_addr, heartbeat=1)
        c.open()
        os.kill(broker.pid, signal.SIGSTOP) # Stall the broker

        def make_sender(): c.session().sender("foo")
        self.assertRaises(qm.ConnectionError, make_sender)

        # Case 2: Connect to a stalled broker
        c = qm.Connection(broker_addr, heartbeat=1)
        self.assertRaises(qm.ConnectionError, c.open)

        # Case 3: Re-connect to a stalled broker.
        broker2 = Broker(self)
        c = qm.Connection(broker2.host_port(), heartbeat=1, reconnect_limit=1,
                          reconnect=True, reconnect_urls=[broker_addr],
                          reconnect_log=False) # Hide expected warnings
        c.open()
        broker2.kill()          # Cause re-connection to broker
        self.assertRaises(qm.ConnectionError, make_sender)

    def test_failover_cpp(self):
        """Verify that failover works in the C++ client."""
        cluster = HaCluster(self, 2)
        cluster[0].connect().session().sender("q;{create:always}")
        cluster[1].wait_backup("q")
        # FIXME aconway 2014-02-21: using 0-10, there is a failover problem with 1.0
        sender = NumberedSender(cluster[0], url=cluster.url, queue="q",
                                connection_options="reconnect:true,protocol:'amqp0-10'")
        receiver = NumberedReceiver(cluster[0], url=cluster.url, queue="q",
                                    connection_options="reconnect:true,protocol:'amqp0-10'")
        receiver.start()
        sender.start()
        assert retry(lambda: receiver.received > 10) # Wait for some messages to get thru
        cluster.kill(0)
        n = receiver.received
        assert retry(lambda: receiver.received > n + 10) # Verify we are still going
        sender.stop()
        receiver.stop()

    def test_backup_failover(self):
        """Verify that a backup broker fails over and recovers queue state"""
        brokers = HaCluster(self, 3)
        brokers[0].connect().session().sender("q;{create:always}").send("a")
        brokers.kill(0)
        brokers[1].connect().session().sender("q").send("b")
        brokers[2].assert_browse_backup("q", ["a","b"])
        s = brokers[1].connect().session()
        self.assertEqual("a", s.receiver("q").fetch().content)
        s.acknowledge()
        brokers[2].assert_browse_backup("q", ["b"])

    def test_empty_backup_failover(self):
        """Verify that a new primary becomes active with no queues.
        Regression test for QPID-5430"""
        brokers = HaCluster(self, 3)
        brokers.kill(0)
        brokers[1].wait_status("active")

    def test_qpid_config_replication(self):
        """Set up replication via qpid-config"""
        brokers = HaCluster(self,2)
        brokers[0].config_declare("q","all")
        brokers[0].connect().session().sender("q").send("foo")
        brokers[1].assert_browse_backup("q", ["foo"])

    def test_standalone_queue_replica(self):
        """Test replication of individual queues outside of cluster mode"""
        primary = HaBroker(self, name="primary", ha_cluster=False,
                           args=["--ha-queue-replication=yes"]);
        pc = primary.connect()
        ps = pc.session().sender("q;{create:always}")
        pr = pc.session().receiver("q;{create:always}")
        backup = HaBroker(self, name="backup", ha_cluster=False,
                          args=["--ha-queue-replication=yes"])
        bs = backup.connect().session()
        br = bs.receiver("q;{create:always}")

        def srange(*args): return [str(i) for i in xrange(*args)]

        for m in srange(3): ps.send(m)
        # Set up replication with qpid-ha
        backup.replicate(primary.host_port(), "q")
        backup.assert_browse_backup("q", srange(3))
        for m in srange(3,6): ps.send(str(m))
        backup.assert_browse_backup("q", srange(6))
        self.assertEqual("0", pr.fetch().content)
        pr.session.acknowledge()
        backup.assert_browse_backup("q", srange(1,6))

        # Set up replication with qpid-config
        ps2 = pc.session().sender("q2;{create:always}")
        backup.config_replicate(primary.host_port(), "q2");
        ps2.send("x")
        backup.assert_browse_backup("q2", ["x"])


    def test_standalone_queue_replica_failover(self):
        """Test individual queue replication from a cluster to a standalone
        backup broker, verify it fails over."""
        l = LogLevel(ERROR) # Hide expected WARNING log messages from failover.
        try:
            cluster = HaCluster(self, 2)
            primary = cluster[0]
            pc = cluster.connect(0)
            ps = pc.session().sender("q;{create:always}")
            pr = pc.session().receiver("q;{create:always}")
            backup = HaBroker(self, name="backup", ha_cluster=False,
                              args=["--ha-queue-replication=yes"])
            br = backup.connect().session().receiver("q;{create:always}")
            backup.replicate(cluster.url, "q")
            ps.send("a")
            ps.sync()
            backup.assert_browse_backup("q", ["a"])
            cluster.bounce(0)
            backup.assert_browse_backup("q", ["a"])
            ps.send("b")
            backup.assert_browse_backup("q", ["a", "b"])
            cluster[0].wait_status("ready")
            cluster.bounce(1)
            # FIXME aconway 2014-02-20: pr does not fail over with 1.0/swig
            if qm == qpid_messaging:
                print "WARNING: Skipping SWIG client failover bug"
                return
            self.assertEqual("a", pr.fetch().content)
            pr.session.acknowledge()
            backup.assert_browse_backup("q", ["b"])
            pc.close()
            br.close()
        finally: l.restore()

    def test_lvq(self):
        """Verify that we replicate to an LVQ correctly"""
        cluster = HaCluster(self, 2)
        s = cluster[0].connect().session().sender("lvq; {create:always, node:{x-declare:{arguments:{'qpid.last_value_queue_key':lvq-key}}}}")

        def send(key,value,expect):
            s.send(qm.Message(content=value,properties={"lvq-key":key}))
            cluster[1].assert_browse_backup("lvq", expect)

        send("a", "a-1", ["a-1"])
        send("b", "b-1", ["a-1", "b-1"])
        send("a", "a-2", ["b-1", "a-2"])
        send("a", "a-3", ["b-1", "a-3"])
        send("c", "c-1", ["b-1", "a-3", "c-1"])
        send("c", "c-2", ["b-1", "a-3", "c-2"])
        send("b", "b-2", ["a-3", "c-2", "b-2"])
        send("c", "c-3", ["a-3", "b-2", "c-3"])
        send("d", "d-1", ["a-3", "b-2", "c-3", "d-1"])

    def test_ring(self):
        """Test replication with the ring queue policy"""
        """Verify that we replicate to an LVQ correctly"""
        cluster = HaCluster(self, 2)
        s = cluster[0].connect().session().sender("q; {create:always, node:{x-declare:{arguments:{'qpid.policy_type':ring, 'qpid.max_count':5}}}}")
        for i in range(10): s.send(qm.Message(str(i)))
        cluster[1].assert_browse_backup("q", [str(i) for i in range(5,10)])

    def test_reject(self):
        """Test replication with the reject queue policy"""
        cluster = HaCluster(self, 2)
        primary, backup = cluster
        s = primary.connect().session().sender("q; {create:always, node:{x-declare:{arguments:{'qpid.policy_type':reject, 'qpid.max_count':5}}}}")
        try:
            for i in range(10): s.send(qm.Message(str(i)), sync=False)
        except qm.LinkError: pass
        backup.assert_browse_backup("q", [str(i) for i in range(0,5)])
        try: s.session.connection.close()
        except: pass            # Expect exception from broken session

    def test_priority(self):
        """Verify priority queues replicate correctly"""
        cluster = HaCluster(self, 2)
        session = cluster[0].connect().session()
        s = session.sender("priority-queue; {create:always, node:{x-declare:{arguments:{'qpid.priorities':10}}}}")
        priorities = [8,9,5,1,2,2,3,4,9,7,8,9,9,2]
        for p in priorities: s.send(qm.Message(priority=p))
        # Can't use browse_backup as browser sees messages in delivery order not priority.
        cluster[1].wait_backup("priority-queue")
        r = cluster[1].connect_admin().session().receiver("priority-queue")
        received = [r.fetch().priority for i in priorities]
        self.assertEqual(sorted(priorities, reverse=True), received)

    def test_priority_fairshare(self):
        """Verify priority queues replicate correctly"""
        cluster = HaCluster(self, 2)
        primary, backup = cluster
        session = primary.connect().session()
        levels = 8
        priorities = [4,5,3,7,8,8,2,8,2,8,8,16,6,6,6,6,6,6,8,3,5,8,3,5,5,3,3,8,8,3,7,3,7,7,7,8,8,8,2,3]
        limits={7:0,6:4,5:3,4:2,3:2,2:2,1:2}
        limit_policy = ",".join(["'qpid.fairshare':5"] + ["'qpid.fairshare-%s':%s"%(i[0],i[1]) for i in limits.iteritems()])
        s = session.sender("priority-queue; {create:always, node:{x-declare:{arguments:{'qpid.priorities':%s, %s}}}}"%(levels,limit_policy))
        messages = [qm.Message(content=str(uuid4()), priority = p) for p in priorities]
        for m in messages: s.send(m)
        backup.wait_backup(s.target)
        r = backup.connect_admin().session().receiver("priority-queue")
        received = [r.fetch().content for i in priorities]
        sort = sorted(messages, key=lambda m: priority_level(m.priority, levels), reverse=True)
        fair = [m.content for m in fairshare(sort, lambda l: limits.get(l,0), levels)]
        self.assertEqual(received, fair)

    def test_priority_ring(self):
        cluster = HaCluster(self, 2)
        primary, backup = cluster
        s = primary.connect().session().sender("q; {create:always, node:{x-declare:{arguments:{'qpid.policy_type':ring, 'qpid.max_count':5, 'qpid.priorities':10}}}}")
        priorities = [8,9,5,1,2,2,3,4,9,7,8,9,9,2]
        for p in priorities: s.send(qm.Message(priority=p))
        expect = sorted(priorities,reverse=True)[0:5]
        primary.assert_browse("q", expect, transform=lambda m: m.priority)
        backup.assert_browse_backup("q", expect, transform=lambda m: m.priority)

    def test_backup_acquired(self):
        """Verify that acquired messages are backed up, for all queue types."""
        class Test:
            def __init__(self, queue, arguments, expect):
                self.queue = queue
                self.address = "%s;{create:always,node:{x-declare:{arguments:{%s}}}}"%(
                    self.queue, ",".join(arguments))
                self.expect = [str(i) for i in expect]

            def send(self, connection):
                """Send messages, then acquire one but don't acknowledge"""
                s = connection.session()
                for m in range(10): s.sender(self.address).send(str(m))
                s.receiver(self.address).fetch()

            def verify(self, brokertest, backup):
                backup.assert_browse_backup(self.queue, self.expect, msg=self.queue)

        tests = [
            Test("plain",[],range(10)),
            Test("ring", ["'qpid.policy_type':ring", "'qpid.max_count':5"], range(5,10)),
            Test("priority",["'qpid.priorities':10"], range(10)),
            Test("fairshare", ["'qpid.priorities':10,'qpid.fairshare':5"], range(10)),
            Test("lvq", ["'qpid.last_value_queue_key':lvq-key"], [9])
            ]

        cluster = HaCluster(self, 3)
        cluster.kill(2, final=False) # restart after messages are sent to test catch-up

        c = cluster[0].connect()
        for t in tests: t.send(c) # Send messages, leave one unacknowledged.

        cluster.restart(2)
        cluster[2].wait_status("ready")

        # Verify acquired message was replicated
        for t in tests: t.verify(self, cluster[1])
        for t in tests: t.verify(self, cluster[2])

    def test_replicate_default(self):
        """Make sure we don't replicate if ha-replicate is unspecified or none"""
        cluster1 = HaCluster(self, 2, ha_replicate=None)
        cluster1[1].wait_status("ready")
        c1 = cluster1[0].connect().session().sender("q;{create:always}")
        cluster2 = HaCluster(self, 2, ha_replicate="none")
        cluster2[1].wait_status("ready")
        cluster2[0].connect().session().sender("q;{create:always}")
        time.sleep(.1)               # Give replication a chance.
        # Expect queues not to be found
        self.assertRaises(qm.NotFound, cluster1[1].connect_admin().session().receiver, "q")
        self.assertRaises(qm.NotFound, cluster2[1].connect_admin().session().receiver, "q")

    def test_replicate_binding(self):
        """Verify that binding replication can be disabled"""
        cluster = HaCluster(self, 2)
        primary, backup = cluster[0], cluster[1]
        ps = primary.connect().session()
        a = primary.agent
        a.addExchange("fanout", "ex")
        a.addQueue("q")
        a.bind("ex", "q", options={'qpid.replicate':'none'})
        backup.wait_backup("q")

        primary.kill()
        assert retry(lambda: not is_running(primary.pid)) # Wait for primary to die
        backup.promote()
        bs = backup.connect_admin().session()
        bs.sender("ex").send(qm.Message("msg"))
        self.assert_browse_retry(bs, "q", [])

    def test_invalid_replication(self):
        """Verify that we reject an attempt to declare a queue with invalid replication value."""
        cluster = HaCluster(self, 1, ha_replicate="all")
        self.assertRaises(Exception, cluster[0].connect().session().sender,
                          "q;{create:always, node:{x-declare:{arguments:{'qpid.replicate':XXinvalidXX}}}}")

    def test_exclusive_queue(self):
        """Ensure that we can back-up exclusive queues, i.e. the replicating
        subscriptions are exempt from the exclusivity"""
        cluster = HaCluster(self, 2)
        def test(addr):
            c = cluster[0].connect()
            q = addr.split(";")[0]
            r = c.session().receiver(addr)
            self.assertRaises(qm.LinkError, c.session().receiver, addr)
            s = c.session().sender(q).send(q)
            cluster[1].assert_browse_backup(q, [q])
        test("excl_queue;{create:always, node:{x-declare:{exclusive:True}}}")
        if qm == qpid.messaging: # FIXME aconway 2014-02-20: swig client no exclusive subscribe
            test("excl_sub;{create:always, link:{x-subscribe:{exclusive:True}}}");

    def test_auto_delete_exclusive(self):
        """Verify that we ignore auto-delete, exclusive, non-auto-delete-timeout queues"""
        cluster = HaCluster(self, 2)
        s0 = cluster[0].connect().session()
        s0.receiver("exad;{create:always,node:{x-declare:{exclusive:True,auto-delete:True}}}")
        s0.receiver("ex;{create:always,node:{x-declare:{exclusive:True}}}")
        ad = s0.receiver("ad;{create:always,node:{x-declare:{auto-delete:True}}}")
        s0.receiver("time;{create:always,node:{x-declare:{exclusive:True,auto-delete:True,arguments:{'qpid.auto_delete_timeout':1}}}}")
        s0.receiver("q;{create:always}")

        s1 = cluster[1].connect_admin().session()
        cluster[1].wait_backup("q")
        assert not valid_address(s1.connection, "exad")
        assert valid_address(s1.connection, "ex")
        assert valid_address(s1.connection, "ad")
        assert valid_address(s1.connection, "time")

        # Verify that auto-delete queues are not kept alive by
        # replicating subscriptions
        ad.close()
        s0.sync()
        assert not valid_address(s0.connection, "ad")

    def test_broker_info(self):
        """Check that broker information is correctly published via management"""
        cluster = HaCluster(self, 3)

        def ha_broker(broker):
            ha_broker = broker.agent.getHaBroker();
            ha_broker.update()
            return ha_broker

        for broker in cluster:  # Make sure HA system-id matches broker's
            self.assertEqual(ha_broker(broker).systemId, UUID(broker.agent.getBroker().systemRef))

        # Check that all brokers have the same membership as the cluster
        def check_ids(broker):
            cluster_ids = set([ ha_broker(b).systemId for b in cluster])
            broker_ids = set([m["system-id"] for m in  ha_broker(broker).members])
            assert retry(lambda: cluster_ids == broker_ids, 1), "%s != %s on %s"%(cluster_ids, broker_ids, broker)

        for broker in cluster: check_ids(broker)

        # Add a new broker, check it is updated everywhere
        b = cluster.start()
        for broker in cluster: check_ids(broker)

    def test_auth(self):
        """Verify that authentication does not interfere with replication."""
        # TODO aconway 2012-07-09: generate test sasl config portably for cmake
        sasl_config=os.path.join(self.rootdir, "sasl_config")
        if not os.path.exists(sasl_config):
            print "WARNING: Skipping test, SASL test configuration %s not found."%sasl_config
            return
        acl=os.path.join(os.getcwd(), "policy.acl")
        aclf=file(acl,"w")
        # Minimum set of privileges required for the HA user.
        aclf.write("""
# HA user
acl allow zag@QPID access queue
acl allow zag@QPID create queue
acl allow zag@QPID consume queue
acl allow zag@QPID delete queue
acl allow zag@QPID access exchange
acl allow zag@QPID create exchange
acl allow zag@QPID bind exchange
acl allow zag@QPID publish exchange
acl allow zag@QPID delete exchange
acl allow zag@QPID access method
acl allow zag@QPID create link
acl allow zag@QPID access query
# Normal user
acl allow zig@QPID all all
acl deny all all
 """)
        aclf.close()
        cluster = HaCluster(
            self, 2,
            args=["--auth", "yes", "--sasl-config", sasl_config,
                  "--acl-file", acl,
                  "--ha-username=zag", "--ha-password=zag", "--ha-mechanism=PLAIN"
                  ],
            client_credentials=Credentials("zag", "zag", "PLAIN"))
        c = cluster[0].connect(username="zig", password="zig")
        s0 = c.session();
        a = cluster[0].agent
        a.addQueue("q")
        a.addExchange("fanout", "ex")
        a.bind("ex", "q", "")
        s0.sender("ex").send("foo");

        # Transactions should be done over the tx_protocol
        c = cluster[0].connect(protocol=self.tx_protocol, username="zig", password="zig")
        s1 = c.session(transactional=True)
        s1.sender("ex").send("foo-tx");
        cluster[1].assert_browse_backup("q", ["foo"])
        s1.commit()
        cluster[1].assert_browse_backup("q", ["foo", "foo-tx"])

    def test_alternate_exchange(self):
        """Verify that alternate-exchange on exchanges and queues is propagated
        to new members of a cluster. """
        cluster = HaCluster(self, 2)
        s = cluster[0].connect().session()
        # altex exchange: acts as alternate exchange
        a = cluster[0].agent
        a.addExchange("fanout", "altex")
        # altq queue bound to altex, collect re-routed messages.
        a.addQueue("altq")
        a.bind("altex", "altq", "")
        # ex exchange with alternate-exchange altex and no queues bound
        a.addExchange("direct", "ex", {"alternate-exchange":"altex"})
        # create queue q with alternate-exchange altex
        a.addQueue("q", {"alternate-exchange":"altex"})
        # create a bunch of exchanges to ensure we don't clean up prematurely if the
        # response comes in multiple fragments.
        for i in xrange(200): s.sender("ex.%s;{create:always,node:{type:topic}}"%i)

        def verify(broker):
            c = broker.connect()
            s = c.session()
            # Verify unmatched message goes to ex's alternate.
            s.sender("ex").send("foo")
            altq = s.receiver("altq")
            self.assertEqual("foo", altq.fetch(timeout=0).content)
            s.acknowledge()
            # Verify rejected message goes to q's alternate.
            s.sender("q").send("bar")
            msg = s.receiver("q").fetch(timeout=0)
            self.assertEqual("bar", msg.content)
            s.acknowledge(msg, qm.Disposition(qm.REJECTED)) # Reject the message
            self.assertEqual("bar", altq.fetch(timeout=0).content)
            s.acknowledge()
            s.sync()            # Make sure backups are caught-up.
            c.close()

        # Sanity check: alternate exchanges on original broker
        verify(cluster[0])
        a = cluster[0].agent
        # Altex is in use as an alternate exchange, we should get an exception
        self.assertRaises(Exception, a.delExchange, "altex")
        # Check backup that was connected during setup.
        def wait(broker):
            broker.wait_status("ready")
            for a in ["q", "ex", "altq", "altex"]:
                broker.wait_backup(a)
        wait(cluster[1])
        cluster.bounce(0)
        verify(cluster[1])

        # Check a newly started backup.
        cluster.start()
        wait(cluster[2])
        cluster.bounce(1)
        verify(cluster[2])

        # Check that alt-exchange in-use count is replicated
        a = cluster[2].agent
        self.assertRaises(Exception, a.delExchange, "altex")
        a.delQueue("q")
        self.assertRaises(Exception, a.delExchange, "altex")
        a.delExchange("ex")
        a.delExchange("altex")

    def test_priority_reroute(self):
        """Regression test for QPID-4262, rerouting messages from a priority queue
        to itself causes a crash"""
        cluster = HaCluster(self, 2)
        primary = cluster[0]
        session = primary.connect().session()
        a = primary.agent
        a.addQueue("pq", {'qpid.priorities':10})
        a.bind("amq.fanout", "pq")
        s = session.sender("pq")
        for m in xrange(100): s.send(qm.Message(str(m), priority=m%10))
        pq =  QmfAgent(primary.host_port()).getQueue("pq")
        pq.reroute(request=0, useAltExchange=False, exchange="amq.fanout")
        # Verify that consuming is in priority order
        expect = [str(10*i+p) for p in xrange(9,-1,-1) for i in xrange(0,10) ]
        actual = [m.content for m in primary.get_messages("pq", 100)]
        self.assertEqual(expect, actual)

    def test_delete_missing_response(self):
        """Check that a backup correctly deletes leftover queues and exchanges that are
        missing from the initial reponse set."""
        # This test is a bit contrived, we set up the situation on backup brokers
        # and then promote one.
        cluster = HaCluster(self, 2, promote=False)

        # cluster[0] Will be the primary
        s = cluster[0].connect_admin().session()
        s.sender("q1;{create:always}")
        s.sender("e1;{create:always, node:{type:topic}}")

        # cluster[1] will be the backup, has extra queues/exchanges
        xdecl = "x-declare:{arguments:{'qpid.replicate':'all'}}"
        node = "node:{%s}"%(xdecl)
        s = cluster[1].connect_admin().session()
        s.sender("q1;{create:always, %s}"%(node))
        s.sender("q2;{create:always, %s}"%(node))
        s.sender("e1;{create:always, node:{type:topic, %s}}"%(xdecl))
        s.sender("e2;{create:always, node:{type:topic, %s}}"%(xdecl))
        for a in ["q1", "q2", "e1", "e2"]: cluster[1].wait_backup(a)

        cluster[0].promote()
        # Verify the backup deletes the surplus queue and exchange
        cluster[1].wait_status("ready")
        s = cluster[1].connect_admin().session()
        self.assertRaises(qm.NotFound, s.receiver, ("q2"));
        self.assertRaises(qm.NotFound, s.receiver, ("e2"));


    def test_delete_qpid_4285(self):
        """Regression test for QPID-4285: on deleting a queue it gets stuck in a
        partially deleted state and causes replication errors."""
        cluster = HaCluster(self,2)
        s = cluster[0].connect().session()
        s.receiver("q;{create:always}")
        cluster[1].wait_backup("q")
        cluster.kill(0)       # Make the backup take over.
        s = cluster[1].connect().session()
        cluster[1].agent.delQueue("q") # Delete q on new primary
        self.assertRaises(qm.NotFound, s.receiver, "q")
        assert not cluster[1].agent.getQueue("q") # Should not be in QMF

    def test_auto_delete_failover(self):
        """Test auto-delete queues. Verify that:
        - queues auto-deleted on the primary are deleted on the backup.
        - auto-delete queues with/without timeout are deleted after a failover correctly
        - auto-delete queues never used (subscribe to) to are not deleted
        - messages are correctly routed to the alternate exchange.
        """
        cluster = HaCluster(self, 3)
        s = cluster[0].connect().session()
        a = cluster[0].agent

        def setup(q, timeout=None):
            # Create alternate exchange, auto-delete queue and queue bound to alt. ex.
            a.addExchange("fanout", q+"-altex")
            args = {"auto-delete":True, "alternate-exchange":q+"-altex"}
            if timeout is not None: args['qpid.auto_delete_timeout'] = timeout
            a.addQueue(q, args)
            a.addQueue(q+"-altq")
            a.bind("%s-altex"%q, "%s-altq"%q)

        for args in [["q1"],["q2",0],["q3",1],["q4"],["q5"]]: setup(*args)
        receivers = []
        for i in xrange(1,5):   # Don't use q5
            q = "q%s"%i
            receivers.append(s.receiver(q)) # Subscribe
            qs = s.sender(q); qs.send(q); qs.close() # Send q name as message

        receivers[3].close()    # Trigger auto-delete for q4
        for b in cluster[1:3]: b.wait_no_queue("q4") # Verify deleted on backups

        cluster[0].kill(final=False) # Kill primary
        cluster[2].promote()
        cluster.restart(0)
        cluster[2].wait_queue("q3") # Not yet auto-deleted, 1 sec timeout.
        for b in cluster:
            for q in ["q%s"%i for i in xrange(1,5)]:
                b.wait_no_queue(q,timeout=2, msg=str(b))      # auto-deleted
                b.assert_browse_backup("%s-altq"%q, [q]) # Routed to alternate
        cluster[2].wait_queue("q5") # Not auto-deleted, never subscribed
        cluster[2].connect().session().receiver("q5").close()
        cluster[2].wait_no_queue("q5")

    def test_auto_delete_close(self):
        """Verify auto-delete queues are deleted on backup if auto-deleted
        on primary"""
        cluster=HaCluster(self, 2)

        # Create altex to use as alternate exchange, with altq bound to it
        a = cluster[0].agent
        a.addExchange("fanout", "altex")
        a.addQueue("altq", {"auto-delete":True})
        a.bind("altex", "altq")

        p = cluster[0].connect().session()
        r = p.receiver("adq1;{create:always,node:{x-declare:{auto-delete:True,alternate-exchange:'altex'}}}")
        s = p.sender("adq1")
        for m in ["aa","bb","cc"]: s.send(m)
        s.close()
        cluster[1].wait_queue("adq1")
        r.close()               # trigger auto-delete of adq1
        cluster[1].wait_no_queue("adq1")
        cluster[1].assert_browse_backup("altq", ["aa","bb","cc"])

    def test_expired(self):
        """Regression test for QPID-4379: HA does not properly handle expired messages"""
        # Race between messages expiring and HA replicating consumer.
        cluster = HaCluster(self, 2)
        s = cluster[0].connect().session().sender("q;{create:always}", capacity=2)
        def send_ttl_messages():
            for i in xrange(100): s.send(qm.Message(str(i), ttl=0.001))
        send_ttl_messages()
        cluster.start()
        send_ttl_messages()

    def test_missed_recreate(self):
        """If a queue or exchange is destroyed and one with the same name re-created
        while a backup is disconnected, the backup should also delete/recreate
        the object when it re-connects"""
        cluster = HaCluster(self, 3)
        sn = cluster[0].connect().session()
        # Create a queue with messages
        s = sn.sender("qq;{create:always}")
        msgs = [str(i) for i in xrange(3)]
        for m in msgs: s.send(m)
        cluster[1].assert_browse_backup("qq", msgs)
        cluster[2].assert_browse_backup("qq", msgs)
        # Set up an exchange with a binding.
        a = cluster[0].agent
        a.addExchange("fanout", "xx")
        a.addQueue("xxq")
        a.bind("xx", "xxq", "xxq")
        cluster[1].wait_address("xx")
        self.assertEqual(cluster[1].agent.getExchange("xx").values["bindingCount"], 1)
        cluster[2].wait_address("xx")
        self.assertEqual(cluster[2].agent.getExchange("xx").values["bindingCount"], 1)

        # Simulate the race by re-creating the objects before promoting the new primary
        cluster.kill(0, promote_next=False)
        xdecl = "x-declare:{arguments:{'qpid.replicate':'all'}}"
        node = "node:{%s}"%(xdecl)
        sn = cluster[1].connect_admin().session()
        a = cluster[1].agent
        a.delQueue("qq", if_empty=False)
        s = sn.sender("qq;{create:always, %s}"%(node))
        s.send("foo")
        a.delExchange("xx")
        sn.sender("xx;{create:always,node:{type:topic,%s}}"%(xdecl))
        cluster[1].promote()
        cluster[1].wait_status("active")
        # Verify we are not still using the old objects on cluster[2]
        cluster[2].assert_browse_backup("qq", ["foo"])
        cluster[2].wait_address("xx")
        self.assertEqual(cluster[2].agent.getExchange("xx").values["bindingCount"], 0)

    def test_resource_limit_bug(self):
        """QPID-5666 Regression test: Incorrect resource limit exception for queue creation."""
        cluster = HaCluster(self, 3)
        qs = ["q%s"%i for i in xrange(10)]
        a = cluster[0].agent
        a.addQueue("q")
        cluster[1].wait_backup("q")
        cluster.kill(0)
        cluster[1].promote()
        cluster[1].wait_status("active")
        a = cluster[1].agent
        a.delQueue("q")
        a.addQueue("q")

def fairshare(msgs, limit, levels):
    """
    Generator to return prioritised messages in expected order for a given fairshare limit
    """
    count = 0
    last_priority = None
    postponed = []
    while msgs or postponed:
        if not msgs:
            msgs = postponed
            count = 0
            last_priority = None
            postponed = [ ]
        msg = msgs.pop(0)
        if last_priority and priority_level(msg.priority, levels) == last_priority:
            count += 1
        else:
            last_priority = priority_level(msg.priority, levels)
            count = 1
        l = limit(last_priority)
        if (l and count > l):
            postponed.append(msg)
        else:
            yield msg
    return

def priority_level(value, levels):
    """
    Method to determine which of a distinct number of priority levels
    a given value falls into.
    """
    offset = 5-math.ceil(levels/2.0)
    return min(max(value - offset, 0), levels-1)

class LongTests(HaBrokerTest):
    """Tests that can run for a long time if -DDURATION=<minutes> is set"""

    def duration(self):
        d = self.config.defines.get("DURATION")
        if d: return float(d)*60
        else: return 3                  # Default is to be quick

    def test_failover_send_receive(self):
        """Test failover with continuous send-receive"""
        brokers = HaCluster(self, 3)

        # Start sender and receiver threads
        n = 10
        senders = [
            NumberedSender(
                brokers[0], url=brokers.url,max_depth=50,
                queue="test%s"%(i), args=["--capacity=10"]) for i in xrange(n)]

        receivers = [
            NumberedReceiver(
                brokers[0], url=brokers.url, sender=senders[i],
                queue="test%s"%(i), args=["--capacity=10"]) for i in xrange(n)]

        for r in receivers: r.start()
        for s in senders: s.start()

        def wait_passed(r, n):
            """Wait for receiver r to pass n"""
            def check():
                r.check()       # Verify no exceptions
                return r.received > n + 100
            assert retry(check), "Stalled %s waiting for %s, sent %s"%(
                r.queue, n, [s for s in senders if s.queue==r.queue][0].sent)

        for r in receivers: wait_passed(r, 0)

        # Kill and restart brokers in a cycle:
        endtime = time.time() + self.duration()
        i = 0
        primary = 0
        try:
            try:
                while time.time() < endtime or i < 3: # At least 3 iterations
                    # Precondition: All 3 brokers running,
                    # primary = index of promoted primary
                    # one or two backups are running,
                    for s in senders: s.sender.assert_running()
                    for r in receivers: r.receiver.assert_running()
                    checkpoint = [ r.received+10 for r in receivers ]
                    victim = random.choice([0,1,2,primary]) # Give the primary a better chance.
                    if victim == primary:
                        # Don't kill primary till it is active and the next
                        # backup is ready, otherwise we can lose messages.
                        brokers[victim].wait_status("active")
                        next = (victim+1)%3
                        brokers[next].wait_status("ready")
                        brokers.bounce(victim) # Next one is promoted
                        primary = next
                    else:
                        brokers.bounce(victim, promote_next=False)

                    # Make sure we are not stalled
                    map(wait_passed, receivers, checkpoint)
                    # Run another checkpoint to ensure things work in this configuration
                    checkpoint = [ r.received+10 for r in receivers ]
                    map(wait_passed, receivers, checkpoint)
                    i += 1
            except:
                traceback.print_exc()
                raise
        finally:
            for s in senders: s.stop()
            for r in receivers: r.stop()
            dead = filter(lambda b: not b.is_running(), brokers)
            if dead: raise Exception("Brokers not running: %s"%dead)

    def test_tx_send_receive(self):
        brokers = HaCluster(self, 3)
        sender = self.popen(
            ["qpid-send",
             "--broker", brokers[0].host_port(),
             "--address", "q;{create:always}",
             "--messages=1000",
             "--tx=10",
             "--connection-options={protocol:%s}" % self.tx_protocol
             ])
        receiver = self.popen(
            ["qpid-receive",
             "--broker", brokers[0].host_port(),
             "--address", "q;{create:always}",
             "--messages=990",
             "--timeout=10",
             "--tx=10",
             "--connection-options={protocol:%s}" % self.tx_protocol
             ])
        self.assertEqual(sender.wait(), 0)
        self.assertEqual(receiver.wait(), 0)
        expect = [long(i) for i in range(991, 1001)]
        sn = lambda m: m.properties["sn"]
        brokers[0].assert_browse("q", expect, transform=sn)
        brokers[1].assert_browse_backup("q", expect, transform=sn)
        brokers[2].assert_browse_backup("q", expect, transform=sn)


    def test_qmf_order(self):
        """QPID 4402:  HA QMF events can be out of order.
        This test mimics the test described in the JIRA. Two threads repeatedly
        declare the same auto-delete queue and close their connection.
        """
        broker = Broker(self)
        class Receiver(Thread):
            def __init__(self, qname):
                Thread.__init__(self)
                self.qname = qname
                self.stopped = False

            def run(self):
                while not self.stopped:
                    self.connection = broker.connect()
                    try:
                        self.connection.session().receiver(
                            self.qname+";{create:always,node:{x-declare:{auto-delete:True}}}")
                    except qm.NotFound: pass # Can occur occasionally, not an error.
                    try: self.connection.close()
                    except: pass

        class QmfObject(object):
            """Track existance of an object and validate QMF events"""
            def __init__(self, type_name, name_field, name):
                self.type_name, self.name_field, self.name = type_name, name_field, name
                self.exists = False

            def qmf_event(self, event):
                content = event.content[0]
                event_type = content['_schema_id']['_class_name']
                values = content['_values']
                if event_type == self.type_name+"Declare" and values[self.name_field] == self.name:
                    disp = values['disp']
                    log.debug("Event %s: disp=%s exists=%s"%(
                            event_type, values['disp'], self.exists))
                    if self.exists: assert values['disp'] == 'existing'
                    else: assert values['disp'] == 'created'
                    self.exists = True
                elif event_type == self.type_name+"Delete" and values[self.name_field] == self.name:
                    log.debug("Event %s: exists=%s"%(event_type, self.exists))
                    assert self.exists
                    self.exists = False

        # Verify order of QMF events.
        helper = EventHelper()
        r = broker.connect().session().receiver(helper.eventAddress())
        threads = [Receiver("qq"), Receiver("qq")]
        for t in threads: t.start()
        queue = QmfObject("queue", "qName", "qq")
        finish = time.time() + self.duration()
        try:
            while time.time() < finish:
                queue.qmf_event(r.fetch())
        finally:
            for t in threads: t.stopped = True; t.join()

    def test_max_queues(self):
        """Verify that we behave properly if we try to exceed the max number
        of replicated queues - currently limited by the max number of channels
        in the replication link"""
        # This test is very slow (3 mins), skip it unless duration() > 1 minute.
        if self.duration() < 60: return
        # This test is written in C++ for speed, it takes a long time
        # to create 64k queues in python. See ha_test_max_queues.cpp.
        cluster = HaCluster(self, 2)
        test = self.popen(["ha_test_max_queues", cluster[0].host_port()])
        self.assertEqual(test.wait(), 0)

class RecoveryTests(HaBrokerTest):
    """Tests for recovery after a failure."""

    def test_queue_hold(self):
        """Verify that the broker holds queues without sufficient backup,
        i.e. does not complete messages sent to those queues."""

        l = LogLevel(ERROR) # Hide expected WARNING log messages from failover.
        try:
            # We don't want backups to time out for this test, set long timeout.
            cluster = HaCluster(self, 4, args=["--ha-backup-timeout=120"]);
            # Wait for the primary to be ready
            cluster[0].wait_status("active")
            for b in cluster[1:4]: b.wait_status("ready")
            # Create a queue before the failure.
            # FIXME aconway 2014-02-20: SWIG client doesn't respect sync=False
            s1 = cluster.connect(0, native=True).session().sender("q1;{create:always}")
            for b in cluster: b.wait_backup("q1")
            for i in xrange(10): s1.send(str(i), timeout=0.1)

            # Kill primary and 2 backups
            cluster[3].wait_status("ready")
            for i in [0,1,2]: cluster.kill(i, promote_next=False, final=False)
            cluster[3].promote()    # New primary, backups will be 1 and 2
            cluster[3].wait_status("recovering")

            def assertSyncTimeout(s):
                self.assertRaises(qpid.messaging.Timeout, s.sync, timeout=.01)

            # Create a queue after the failure
            # FIXME aconway 2014-02-20: SWIG client doesn't respect sync=False
            s2 = cluster.connect(3, native=True).session().sender("q2;{create:always}")

            # Verify that messages sent are not completed
            for i in xrange(10,20):
                s1.send(str(i), sync=False, timeout=0.1);
                s2.send(str(i), sync=False, timeout=0.1)

            assertSyncTimeout(s1)
            self.assertEqual(s1.unsettled(), 10)
            assertSyncTimeout(s2)
            self.assertEqual(s2.unsettled(), 10)

            # Verify we can receive even if sending is on hold:
            cluster[3].assert_browse("q1", [str(i) for i in range(10)])

            # Restart backups, verify queues are released only when both backups are up
            cluster.restart(1)
            assertSyncTimeout(s1)
            self.assertEqual(s1.unsettled(), 10)
            assertSyncTimeout(s2)
            self.assertEqual(s2.unsettled(), 10)
            cluster.restart(2)
            cluster.restart(0)

            # Verify everything is up to date and active
            def settled(sender): sender.sync(timeout=1); return sender.unsettled() == 0;
            assert retry(lambda: settled(s1)), "Unsetttled=%s"%(s1.unsettled())
            assert retry(lambda: settled(s2)), "Unsetttled=%s"%(s2.unsettled())
            cluster[1].assert_browse_backup("q1", [str(i) for i in range(10)+range(10,20)])
            cluster[1].assert_browse_backup("q2", [str(i) for i in range(10,20)])
            cluster[3].wait_status("active"),
            s1.session.connection.close()
            s2.session.connection.close()
        finally: l.restore()

    def test_expected_backup_timeout(self):
        """Verify that we time-out expected backups and release held queues
        after a configured interval. Verify backup is demoted to catch-up,
        but can still rejoin.
        """
        cluster = HaCluster(self, 3, args=["--ha-backup-timeout=0.5"]);
        for i in [0,1]: cluster.kill(i, False)
        cluster[2].promote()    # New primary, expected backup will be 1
        # Should not go active till the expected backup connects or times out.
        cluster[2].wait_status("recovering")
        # Messages should be held till expected backup times out
        ss = cluster[2].connect().session()
        s = ss.sender("q;{create:always}")
        s.send("foo", sync=False)
        self.assertEqual(s.unsettled(), 1) # Verify message not settled immediately.
        s.sync(timeout=1)                  # And settled after timeout.
        cluster[2].wait_status("active")

    def test_join_ready_cluster(self):
        """If we join a cluster where the primary is dead, the new primary is
        not yet promoted and there are ready backups then we should refuse
        promotion so that one of the ready backups can be chosen."""
        cluster = HaCluster(self, 2)
        cluster[0].wait_status("active")
        cluster[1].wait_status("ready")
        cluster.bounce(0, promote_next=False)
        self.assertRaises(Exception, cluster[0].promote)
        os.kill(cluster[1].pid, signal.SIGSTOP) # Test for timeout if unresponsive.
        cluster.bounce(0, promote_next=False)
        cluster[0].promote()

    def test_stalled_backup(self):
        """Make sure that a stalled backup broker does not stall the primary"""
        cluster = HaCluster(self, 3, args=["--link-heartbeat-interval=1"])
        os.kill(cluster[1].pid, signal.SIGSTOP)
        s = cluster[0].connect().session()
        s.sender("q;{create:always}").send("x")
        self.assertEqual("x", s.receiver("q").fetch(0).content)

class StoreTests(HaBrokerTest):
    """Test for HA with persistence."""

    def check_skip(self):
        if not BrokerTest.store_lib:
            print "WARNING: skipping HA+store tests, no store lib found."
        return not BrokerTest.store_lib

    def test_store_recovery(self):
        """Verify basic store and recover functionality"""
        if self.check_skip(): return
        cluster = HaCluster(self, 1)
        sn = cluster[0].connect().session()
        # Create queue qq, exchange exx and binding between them
        s = sn.sender("qq;{create:always,node:{durable:true}}")
        sk = sn.sender("exx/k;{create:always,node:{type:topic, durable:true, x-declare:{type:'direct'}}}")
        cluster[0].agent.bind("exx", "qq", "k")
        for m in ["foo", "bar", "baz"]: s.send(qm.Message(m, durable=True))
        r = cluster[0].connect().session().receiver("qq")
        self.assertEqual(r.fetch().content, "foo")
        r.session.acknowledge()
        # Sending this message is a hack to flush the dequeue operation on qq.
        s.send(qm.Message("flush", durable=True))

        def verify(broker, x_count):
            sn = broker.connect().session()
            assert_browse(sn, "qq", [ "bar", "baz", "flush" ]+ (x_count)*["x"])
            sn.sender("exx/k").send(qm.Message("x", durable=True))
            assert_browse(sn, "qq", [ "bar", "baz", "flush" ]+ (x_count+1)*["x"])

        verify(cluster[0], 0)   # Sanity check
        cluster.bounce(0)
        cluster[0].wait_status("active")
        verify(cluster[0], 1)   # Loaded from store
        cluster.start()
        cluster[1].wait_status("ready")
        cluster.kill(0)
        cluster[1].wait_status("active")
        verify(cluster[1], 2)
        cluster.bounce(1, promote_next=False)
        cluster[1].promote()
        cluster[1].wait_status("active")
        verify(cluster[1], 3)

    def test_catchup_store(self):
        """Verify that a backup erases queue data from store recovery before
        doing catch-up from the primary."""
        if self.check_skip(): return
        cluster = HaCluster(self, 2)
        sn = cluster[0].connect(heartbeat=HaBroker.heartbeat).session()
        s1 = sn.sender("q1;{create:always,node:{durable:true}}")
        for m in ["foo","bar"]: s1.send(qm.Message(m, durable=True))
        s2 = sn.sender("q2;{create:always,node:{durable:true}}")
        sk2 = sn.sender("ex/k2;{create:always,node:{type:topic, durable:true, x-declare:{type:'direct'}}}")
        cluster[0].agent.bind("ex", "q2", "k2")
        sk2.send(qm.Message("hello", durable=True))
        # Wait for backup to catch up.
        cluster[1].assert_browse_backup("q1", ["foo","bar"])
        cluster[1].assert_browse_backup("q2", ["hello"])
        # Make changes that the backup doesn't see
        cluster.kill(1, promote_next=False, final=False)
        r1 = cluster[0].connect(heartbeat=HaBroker.heartbeat).session().receiver("q1")
        for m in ["foo", "bar"]: self.assertEqual(r1.fetch().content, m)
        r1.session.acknowledge()
        for m in ["x","y","z"]: s1.send(qm.Message(m, durable=True))
        cluster[0].agent.unbind("ex", "q2", "k2")
        cluster[0].agent.bind("ex", "q1", "k1")
        # Restart both brokers from store to get inconsistent sequence numbering.
        cluster.bounce(0, promote_next=False)
        cluster[0].promote()
        cluster[0].wait_status("active")
        cluster.restart(1)
        cluster[1].wait_status("ready")

        # Verify state
        cluster[0].assert_browse("q1",  ["x","y","z"])
        cluster[1].assert_browse_backup("q1",  ["x","y","z"])

        sn = cluster[0].connect(heartbeat=HaBroker.heartbeat).session()
        sn.sender("ex/k1").send("boo")
        cluster[0].assert_browse_backup("q1", ["x","y","z", "boo"])
        cluster[1].assert_browse_backup("q1", ["x","y","z", "boo"])
        sn.sender("ex/k2").send("hoo") # q2 was unbound so this should be dropped.
        sn.sender("q2").send("end")    # mark the end of the queue for assert_browse
        cluster[0].assert_browse("q2", ["hello", "end"])
        cluster[1].assert_browse_backup("q2", ["hello", "end"])

def open_read(name):
    try:
        f = open(name)
        return f.read()
    finally: f.close()

class TransactionTests(HaBrokerTest):

    def tx_simple_setup(self, cluster, broker=0):
        """Start a transaction, remove messages from queue a, add messages to queue b"""
        c = cluster.connect(broker, protocol=self.tx_protocol)
        # Send messages to a, no transaction.
        sa = c.session().sender("a;{create:always,node:{durable:true}}")
        tx_msgs =  ["x","y","z"]
        for m in tx_msgs: sa.send(qm.Message(content=m, durable=True))
        sa.close()

        # Receive messages from a, in transaction.
        tx = c.session(transactional=True)
        txr = tx.receiver("a")
        tx_msgs2 = [txr.fetch(1).content for i in xrange(3)]
        self.assertEqual(tx_msgs, tx_msgs2)

        # Send messages to b, transactional, mixed with non-transactional.
        sb = c.session().sender("b;{create:always,node:{durable:true}}")
        txs = tx.sender("b")
        msgs = [str(i) for i in xrange(3)]
        for tx_m,m in zip(tx_msgs2, msgs):
            txs.send(tx_m);
            sb.send(m)
        sb.close()
        return tx

    def test_tx_simple_commit(self):
        cluster = HaCluster(self, 2, test_store=True, wait=True)
        tx = self.tx_simple_setup(cluster)
        tx.sync()
        tx.acknowledge()
        # Pre transaction - messages are acquired on primary but not yet dequeued
        # so still there on backup.
        cluster[0].assert_browse_backup("a", [])
        cluster[1].assert_browse_backup("a", ['x', 'y', 'z'])
        for b in cluster:
            b.assert_browse_backup("b", ['0', '1', '2'])
        tx.commit()
        tx.sync()
        tx.close()

        # Post transaction: all synced.
        for b in cluster:
            b.assert_browse_backup("a", [])
            b.assert_browse_backup("b", ['0', '1', '2', "x","y","z"])

        # Verify non-tx dequeue is replicated correctly
        c = cluster.connect(0, protocol=self.tx_protocol)
        r = c.session().receiver("b")
        ri = receiver_iter(r, timeout=1)
        self.assertEqual(['0', '1', '2', 'x', 'y', 'z'], [m.content for m in ri])
        r.session.acknowledge()
        for b in cluster: b.assert_browse_backup("b", [], msg=b)
        c.close()
        tx.connection.close()

    def test_tx_simple_rollback(self):
        cluster = HaCluster(self, 2, test_store=True)
        tx = self.tx_simple_setup(cluster)
        tx.sync()
        tx.acknowledge()
        tx.rollback()

        for b in cluster:
            b.assert_browse_backup("a", ["x","y","z"])
            b.assert_browse_backup("b", ['0', '1', '2'])

        tx.close()
        tx.connection.close()


    def test_tx_simple_failure(self):
        """Verify we throw TransactionAborted if there is a fail-over during a transaction"""
        cluster = HaCluster(self, 3, test_store=True)
        tx = self.tx_simple_setup(cluster)
        tx.sync()
        tx_queues = cluster[0].agent.tx_queues()
        tx.acknowledge()
        l = LogLevel(ERROR) # Hide expected WARNING log messages from failover.
        try:
            cluster.bounce(0)       # Should cause roll-back
            tx.connection.session() # Wait for reconnect
            self.assertRaises(qm.TransactionAborted, tx.sync)
            self.assertRaises(qm.TransactionAborted, tx.commit)
            try: tx.connection.close()
            except qm.TransactionAborted: pass # Occasionally get exception on close.
            for b in cluster:
                b.assert_browse_backup("a", ["x","y","z"])
                b.assert_browse_backup("b", ['0', '1', '2'])
        finally: l.restore()

    def test_tx_join_leave(self):
        """Test cluster members joining/leaving cluster.
        Also check that tx-queues are cleaned up at end of transaction."""

        cluster = HaCluster(self, 3)

        # Leaving
        tx = cluster[0].connect(protocol=self.tx_protocol).session(transactional=True)
        s = tx.sender("q;{create:always}")
        s.send("a", sync=True)
        cluster[1].kill(final=False)
        s.send("b")
        tx.commit()
        tx.connection.close()
        for b in [cluster[0],cluster[2]]:
            b.assert_browse_backup("q", ["a","b"], msg=b)
        # Joining
        tx = cluster[0].connect(protocol=self.tx_protocol).session(transactional=True)
        s = tx.sender("q;{create:always}")
        s.send("foo")
        cluster.restart(1)      # Not a part of the current transaction.
        tx.commit()
        tx.connection.close()
        # The new member is not in the tx but  receives the results normal replication.
        for b in cluster: b.assert_browse_backup("q", ["a", "b", "foo"], msg=b)

    def test_tx_block_threads(self):
        """Verify that TXs blocked in commit don't deadlock."""
        cluster = HaCluster(self, 2, args=["--worker-threads=2"], test_store=True)
        n = 10                  # Number of concurrent transactions
        sessions = [cluster.connect(0, protocol=self.tx_protocol).session(transactional=True) for i in xrange(n)]
        # Have the store delay the response for 10s
        for s in sessions:
            sn = s.sender("qq;{create:always,node:{durable:true}}")
            sn.send(qm.Message("foo", durable=True))
        threads = [ Thread(target=s.commit) for s in sessions]
        for t in threads: t.start()
        cluster[0].ready(timeout=1) # Check for deadlock
        for b in cluster: b.assert_browse_backup('qq', ['foo']*n)
        for t in threads: t.join()
        for s in sessions: s.connection.close()

    def test_other_tx_tests(self):
        try:
            import qpid_tests.broker_0_10
        except ImportError:
            raise Skipped("Tests not found")
        cluster = HaCluster(self, 3)
        if "QPID_PORT" in os.environ: del os.environ["QPID_PORT"]
        self.popen(["qpid-txtest2", "--broker", cluster[0].host_port()]).assert_exit_ok()
        print
        self.popen(["qpid-python-test",
                    "-m", "qpid_tests.broker_0_10",
                    "-m", "qpid_tests.broker_1_0",
                    "-b", "localhost:%s"%(cluster[0].port()),
                    "*.tx.*"], stdout=None, stderr=None).assert_exit_ok()

if __name__ == "__main__":
    outdir = "ha_tests.tmp"
    shutil.rmtree(outdir, True)
    os.execvp("qpid-python-test",
            ["qpid-python-test", "-m", "ha_tests", "-DOUTDIR=%s"%outdir]
              + sys.argv[1:])

