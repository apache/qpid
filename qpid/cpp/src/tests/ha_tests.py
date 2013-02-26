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

import os, signal, sys, time, imp, re, subprocess, glob, random, logging, shutil, math, unittest, random
import traceback
from qpid.messaging import Message, SessionError, NotFound, ConnectionError, ReceiverError, Connection, Timeout, Disposition, REJECTED, Empty
from qpid.datatypes import uuid4
from brokertest import *
from ha_test import *
from threading import Thread, Lock, Condition
from logging import getLogger, WARN, ERROR, DEBUG, INFO
from qpidtoollibs import BrokerAgent, EventHelper
from uuid import UUID

log = getLogger(__name__)

def grep(filename, regexp):
    for line in open(filename).readlines():
        if (regexp.search(line)): return True
    return False

class HaBrokerTest(BrokerTest):
    """Base class for HA broker tests"""
    def assert_log_no_errors(self, broker):
        log = broker.get_log()
        if grep(log, re.compile("] error|] critical")):
            self.fail("Errors in log file %s"%(log))

class ReplicationTests(HaBrokerTest):
    """Correctness tests for  HA replication."""

    def test_replication(self):
        """Test basic replication of configuration and messages before and
        after backup has connected"""

        def queue(name, replicate):
            return "%s;{create:always,node:{x-declare:{arguments:{'qpid.replicate':%s}}}}"%(name, replicate)

        def exchange(name, replicate, bindq, key):
            return "%s/%s;{create:always,node:{type:topic,x-declare:{arguments:{'qpid.replicate':%s}, type:'topic'},x-bindings:[{exchange:'%s',queue:'%s',key:'%s'}]}}"%(name, key, replicate, name, bindq, key)

        def setup(p, prefix, primary):
            """Create config, send messages on the primary p"""
            s = p.sender(queue(prefix+"q1", "all"))
            for m in ["a", "b", "1"]: s.send(Message(m))
            # Test replication of dequeue
            self.assertEqual(p.receiver(prefix+"q1").fetch(timeout=0).content, "a")
            p.acknowledge()
            p.sender(queue(prefix+"q2", "configuration")).send(Message("2"))
            p.sender(queue(prefix+"q3", "none")).send(Message("3"))
            p.sender(exchange(prefix+"e1", "all", prefix+"q1", "key1")).send(Message("4"))
            p.sender(exchange(prefix+"e2", "configuration", prefix+"q2", "key2")).send(Message("5"))
            # Test  unbind
            p.sender(queue(prefix+"q4", "all")).send(Message("6"))
            s3 = p.sender(exchange(prefix+"e4", "all", prefix+"q4", "key4"))
            s3.send(Message("7"))
            # Use old connection to unbind
            us = primary.connect_old().session(str(uuid4()))
            us.exchange_unbind(exchange=prefix+"e4", binding_key="key4", queue=prefix+"q4")
            p.sender(prefix+"e4").send(Message("drop1")) # Should be dropped
            # Test replication of deletes
            p.sender(queue(prefix+"dq", "all"))
            p.sender(exchange(prefix+"de", "all", prefix+"dq", ""))
            p.sender(prefix+"dq;{delete:always}").close()
            p.sender(prefix+"de;{delete:always}").close()
            # Need a marker so we can wait till sync is done.
            p.sender(queue(prefix+"x", "configuration"))

        def verify(b, prefix, p):
            """Verify setup was replicated to backup b"""
            # Wait for configuration to replicate.
            wait_address(b, prefix+"x");
            self.assert_browse_retry(b, prefix+"q1", ["b", "1", "4"])

            self.assertEqual(p.receiver(prefix+"q1").fetch(timeout=0).content, "b")
            p.acknowledge()
            self.assert_browse_retry(b, prefix+"q1", ["1", "4"])

            self.assert_browse_retry(b, prefix+"q2", []) # configuration only
            assert not valid_address(b, prefix+"q3")

            # Verify exchange with replicate=all
            b.sender(prefix+"e1/key1").send(Message(prefix+"e1"))
            self.assert_browse_retry(b, prefix+"q1", ["1", "4", prefix+"e1"])

            # Verify exchange with replicate=configuration
            b.sender(prefix+"e2/key2").send(Message(prefix+"e2")) 
            self.assert_browse_retry(b, prefix+"q2", [prefix+"e2"])

            b.sender(prefix+"e4/key4").send(Message("drop2")) # Verify unbind.
            self.assert_browse_retry(b, prefix+"q4", ["6","7"])

            # Verify deletes
            assert not valid_address(b, prefix+"dq")
            assert not valid_address(b, prefix+"de")

        l = LogLevel(ERROR) # Hide expected WARNING log messages from failover.
        try:
            primary = HaBroker(self, name="primary")
            primary.promote()
            p = primary.connect().session()

            # Create config, send messages before starting the backup, to test catch-up replication.
            setup(p, "1", primary)
            backup  = HaBroker(self, name="backup", brokers_url=primary.host_port())
            # Create config, send messages after starting the backup, to test steady-state replication.
            setup(p, "2", primary)

            # Verify the data on the backup
            b = backup.connect_admin().session()
            verify(b, "1", p)
            verify(b, "2", p)
            # Test a series of messages, enqueue all then dequeue all.
            s = p.sender(queue("foo","all"))
            wait_address(b, "foo")
            msgs = [str(i) for i in range(10)]
            for m in msgs: s.send(Message(m))
            self.assert_browse_retry(p, "foo", msgs)
            self.assert_browse_retry(b, "foo", msgs)
            r = p.receiver("foo")
            for m in msgs: self.assertEqual(m, r.fetch(timeout=0).content)
            p.acknowledge()
            self.assert_browse_retry(p, "foo", [])
            self.assert_browse_retry(b, "foo", [])

            # Another series, this time verify each dequeue individually.
            for m in msgs: s.send(Message(m))
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
        wait_address(b1, "q");
        self.assert_browse_retry(b1, "q", msgs)
        b2 = backup2.connect_admin().session()
        wait_address(b2, "q");
        self.assert_browse_retry(b2, "q", msgs)

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
                 "--content-string=x"
                 ])
            receiver = self.popen(
                ["qpid-receive",
                 "--broker", brokers[0].host_port(),
                 "--address", "q;{create:always}",
                 "--messages=990",
                 "--timeout=10"
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
            primary = HaBroker(self, name="primary", expect=EXPECT_EXIT_FAIL)
            primary.promote()
            backup = HaBroker(self, name="backup", brokers_url=primary.host_port())
            # Check that backup rejects normal connections
            try:
                backup.connect().session()
                self.fail("Expected connection to backup to fail")
            except ConnectionError: pass
            # Check that admin connections are allowed to backup.
            backup.connect_admin().close()

            # Test discovery: should connect to primary after reject by backup
            c = backup.connect(reconnect_urls=[primary.host_port(), backup.host_port()], reconnect=True)
            s = c.session()
            sender = s.sender("q;{create:always}")
            backup.wait_backup("q")
            sender.send("foo")
            primary.kill()
            assert retry(lambda: not is_running(primary.pid))
            backup.promote()
            sender.send("bar")
            self.assert_browse_retry(s, "q", ["foo", "bar"])
            c.close()
        finally: l.restore()

    def test_failover_cpp(self):
        """Verify that failover works in the C++ client."""
        primary = HaBroker(self, name="primary", expect=EXPECT_EXIT_FAIL)
        primary.promote()
        backup = HaBroker(self, name="backup", brokers_url=primary.host_port())
        url="%s,%s"%(primary.host_port(), backup.host_port())
        primary.connect().session().sender("q;{create:always}")
        backup.wait_backup("q")

        sender = NumberedSender(primary, url=url, queue="q", failover_updates = False)
        receiver = NumberedReceiver(primary, url=url, queue="q", failover_updates = False)
        receiver.start()
        sender.start()
        backup.wait_backup("q")
        assert retry(lambda: receiver.received > 10) # Wait for some messages to get thru

        primary.kill()
        assert retry(lambda: not is_running(primary.pid)) # Wait for primary to die
        backup.promote()
        n = receiver.received       # Make sure we are still running
        assert retry(lambda: receiver.received > n + 10)
        sender.stop()
        receiver.stop()

    def test_backup_failover(self):
        """Verify that a backup broker fails over and recovers queue state"""
        brokers = HaCluster(self, 3)
        brokers[0].connect().session().sender("q;{create:always}").send("a")
        for b in brokers[1:]: b.assert_browse_backup("q", ["a"], msg=b)
        brokers[0].expect = EXPECT_EXIT_FAIL
        brokers.kill(0)
        brokers[1].connect().session().sender("q").send("b")
        brokers[2].assert_browse_backup("q", ["a","b"])
        s = brokers[1].connect().session()
        self.assertEqual("a", s.receiver("q").fetch().content)
        s.acknowledge()
        brokers[2].assert_browse_backup("q", ["b"])

    def test_qpid_config_replication(self):
        """Set up replication via qpid-config"""
        brokers = HaCluster(self,2)
        brokers[0].config_declare("q","all")
        brokers[0].connect().session().sender("q").send("foo")
        brokers[1].assert_browse_backup("q", ["foo"])

    def test_standalone_queue_replica(self):
        """Test replication of individual queues outside of cluster mode"""
        l = LogLevel(ERROR) # Hide expected WARNING log messages from failover.
        try:
            primary = HaBroker(self, name="primary", ha_cluster=False,
                               args=["--ha-queue-replication=yes"]);
            pc = primary.connect()
            ps = pc.session().sender("q;{create:always}")
            pr = pc.session().receiver("q;{create:always}")
            backup = HaBroker(self, name="backup", ha_cluster=False,
                              args=["--ha-queue-replication=yes"])
            br = backup.connect().session().receiver("q;{create:always}")

            # Set up replication with qpid-ha
            backup.replicate(primary.host_port(), "q")
            ps.send("a")
            backup.assert_browse_backup("q", ["a"])
            ps.send("b")
            backup.assert_browse_backup("q", ["a", "b"])
            self.assertEqual("a", pr.fetch().content)
            pr.session.acknowledge()
            backup.assert_browse_backup("q", ["b"])

            # Set up replication with qpid-config
            ps2 = pc.session().sender("q2;{create:always}")
            backup.config_replicate(primary.host_port(), "q2");
            ps2.send("x")
            backup.assert_browse_backup("q2", ["x"])
        finally: l.restore()

    def test_queue_replica_failover(self):
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
            backup.assert_browse_backup("q", ["a"])
            cluster.bounce(0)
            backup.assert_browse_backup("q", ["a"])
            ps.send("b")
            backup.assert_browse_backup("q", ["a", "b"])
            cluster.bounce(1)
            self.assertEqual("a", pr.fetch().content)
            pr.session.acknowledge()
            backup.assert_browse_backup("q", ["b"])
            pc.close()
            br.close()
        finally: l.restore()

    def test_lvq(self):
        """Verify that we replicate to an LVQ correctly"""
        primary  = HaBroker(self, name="primary")
        primary.promote()
        backup = HaBroker(self, name="backup", brokers_url=primary.host_port())
        s = primary.connect().session().sender("lvq; {create:always, node:{x-declare:{arguments:{'qpid.last_value_queue_key':lvq-key}}}}")
        def send(key,value): s.send(Message(content=value,properties={"lvq-key":key}))
        for kv in [("a","a-1"),("b","b-1"),("a","a-2"),("a","a-3"),("c","c-1"),("c","c-2")]:
            send(*kv)
        backup.assert_browse_backup("lvq", ["b-1", "a-3", "c-2"])
        send("b","b-2")
        backup.assert_browse_backup("lvq", ["a-3", "c-2", "b-2"])
        send("c","c-3")
        backup.assert_browse_backup("lvq", ["a-3", "b-2", "c-3"])
        send("d","d-1")
        backup.assert_browse_backup("lvq", ["a-3", "b-2", "c-3", "d-1"])

    def test_ring(self):
        """Test replication with the ring queue policy"""
        primary  = HaBroker(self, name="primary")
        primary.promote()
        backup = HaBroker(self, name="backup", brokers_url=primary.host_port())
        s = primary.connect().session().sender("q; {create:always, node:{x-declare:{arguments:{'qpid.policy_type':ring, 'qpid.max_count':5}}}}")
        for i in range(10): s.send(Message(str(i)))
        backup.assert_browse_backup("q", [str(i) for i in range(5,10)])

    def test_reject(self):
        """Test replication with the reject queue policy"""
        primary  = HaBroker(self, name="primary")
        primary.promote()
        backup = HaBroker(self, name="backup", brokers_url=primary.host_port())
        s = primary.connect().session().sender("q; {create:always, node:{x-declare:{arguments:{'qpid.policy_type':reject, 'qpid.max_count':5}}}}")
        try:
            for i in range(10): s.send(Message(str(i)), sync=False)
        except qpid.messaging.exceptions.TargetCapacityExceeded: pass
        backup.assert_browse_backup("q", [str(i) for i in range(0,5)])
        # Detach, don't close as there is a broken session
        s.session.connection.detach()

    def test_priority(self):
        """Verify priority queues replicate correctly"""
        primary  = HaBroker(self, name="primary")
        primary.promote()
        backup = HaBroker(self, name="backup", brokers_url=primary.host_port())
        session = primary.connect().session()
        s = session.sender("priority-queue; {create:always, node:{x-declare:{arguments:{'qpid.priorities':10}}}}")
        priorities = [8,9,5,1,2,2,3,4,9,7,8,9,9,2]
        for p in priorities: s.send(Message(priority=p))
        # Can't use browse_backup as browser sees messages in delivery order not priority.
        backup.wait_backup("priority-queue")
        r = backup.connect_admin().session().receiver("priority-queue")
        received = [r.fetch().priority for i in priorities]
        self.assertEqual(sorted(priorities, reverse=True), received)

    def test_priority_fairshare(self):
        """Verify priority queues replicate correctly"""
        primary  = HaBroker(self, name="primary")
        primary.promote()
        backup = HaBroker(self, name="backup", brokers_url=primary.host_port())
        session = primary.connect().session()
        levels = 8
        priorities = [4,5,3,7,8,8,2,8,2,8,8,16,6,6,6,6,6,6,8,3,5,8,3,5,5,3,3,8,8,3,7,3,7,7,7,8,8,8,2,3]
        limits={7:0,6:4,5:3,4:2,3:2,2:2,1:2}
        limit_policy = ",".join(["'qpid.fairshare':5"] + ["'qpid.fairshare-%s':%s"%(i[0],i[1]) for i in limits.iteritems()])
        s = session.sender("priority-queue; {create:always, node:{x-declare:{arguments:{'qpid.priorities':%s, %s}}}}"%(levels,limit_policy))
        messages = [Message(content=str(uuid4()), priority = p) for p in priorities]
        for m in messages: s.send(m)
        backup.wait_backup(s.target)
        r = backup.connect_admin().session().receiver("priority-queue")
        received = [r.fetch().content for i in priorities]
        sort = sorted(messages, key=lambda m: priority_level(m.priority, levels), reverse=True)
        fair = [m.content for m in fairshare(sort, lambda l: limits.get(l,0), levels)]
        self.assertEqual(received, fair)

    def test_priority_ring(self):
        primary  = HaBroker(self, name="primary")
        primary.promote()
        backup = HaBroker(self, name="backup", brokers_url=primary.host_port())
        s = primary.connect().session().sender("q; {create:always, node:{x-declare:{arguments:{'qpid.policy_type':ring, 'qpid.max_count':5, 'qpid.priorities':10}}}}")
        priorities = [8,9,5,1,2,2,3,4,9,7,8,9,9,2]
        for p in priorities: s.send(Message(priority=p))
        expect = sorted(priorities,reverse=True)[0:5]
        primary.assert_browse("q", expect, transform=lambda m: m.priority)
        backup.assert_browse_backup("q", expect, transform=lambda m: m.priority)

    def test_backup_acquired(self):
        """Verify that acquired messages are backed up, for all queue types."""
        class Test:
            def __init__(self, queue, arguments, expect):
                self.queue = queue
                self.address = "%s;{create:always,node:{x-declare:{arguments:{%s}}}}"%(
                    self.queue, ",".join(arguments + ["'qpid.replicate':all"]))
                self.expect = [str(i) for i in expect]

            def send(self, connection):
                """Send messages, then acquire one but don't acknowledge"""
                s = connection.session()
                for m in range(10): s.sender(self.address).send(str(m))
                s.receiver(self.address).fetch()

            def wait(self, brokertest, backup):
                backup.wait_backup(self.queue)

            def verify(self, brokertest, backup):
                backup.assert_browse_backup(self.queue, self.expect, msg=self.queue)

        tests = [
            Test("plain",[],range(10)),
            Test("ring", ["'qpid.policy_type':ring", "'qpid.max_count':5"], range(5,10)),
            Test("priority",["'qpid.priorities':10"], range(10)),
            Test("fairshare", ["'qpid.priorities':10,'qpid.fairshare':5"], range(10)),
            Test("lvq", ["'qpid.last_value_queue_key':lvq-key"], [9])
            ]

        primary  = HaBroker(self, name="primary")
        primary.promote()
        backup1 = HaBroker(self, name="backup1", brokers_url=primary.host_port())
        c = primary.connect()
        for t in tests: t.send(c) # Send messages, leave one unacknowledged.

        backup2 = HaBroker(self, name="backup2", brokers_url=primary.host_port())
        # Wait for backups to catch up.
        for t in tests:
            t.wait(self, backup1)
            t.wait(self, backup2)
        # Verify acquired message was replicated
        for t in tests: t.verify(self, backup1)
        for t in tests: t.verify(self, backup2)

    def test_replicate_default(self):
        """Make sure we don't replicate if ha-replicate is unspecified or none"""
        cluster1 = HaCluster(self, 2, ha_replicate=None)
        cluster1[1].wait_status("ready")
        c1 = cluster1[0].connect().session().sender("q;{create:always}")
        cluster2 = HaCluster(self, 2, ha_replicate="none")
        cluster2[1].wait_status("ready")
        cluster2[0].connect().session().sender("q;{create:always}")
        time.sleep(.1)               # Give replication a chance.
        try:
            cluster1[1].connect_admin().session().receiver("q")
            self.fail("Excpected no-such-queue exception")
        except NotFound: pass
        try:
            cluster2[1].connect_admin().session().receiver("q")
            self.fail("Excpected no-such-queue exception")
        except NotFound: pass

    def test_replicate_binding(self):
        """Verify that binding replication can be disabled"""
        primary = HaBroker(self, name="primary", expect=EXPECT_EXIT_FAIL)
        primary.promote()
        backup = HaBroker(self, name="backup", brokers_url=primary.host_port())
        ps = primary.connect().session()
        ps.sender("ex;{create:always,node:{type:topic,x-declare:{arguments:{'qpid.replicate':all}, type:'fanout'}}}")
        ps.sender("q;{create:always,node:{type:queue,x-declare:{arguments:{'qpid.replicate':all}},x-bindings:[{exchange:'ex',queue:'q',key:'',arguments:{'qpid.replicate':none}}]}}")
        backup.wait_backup("q")

        primary.kill()
        assert retry(lambda: not is_running(primary.pid)) # Wait for primary to die
        backup.promote()
        bs = backup.connect_admin().session()
        bs.sender("ex").send(Message("msg"))
        self.assert_browse_retry(bs, "q", [])

    def test_invalid_replication(self):
        """Verify that we reject an attempt to declare a queue with invalid replication value."""
        cluster = HaCluster(self, 1, ha_replicate="all")
        try:
            c = cluster[0].connect().session().sender("q;{create:always, node:{x-declare:{arguments:{'qpid.replicate':XXinvalidXX}}}}")
            self.fail("Expected ConnectionError")
        except ConnectionError: pass

    def test_exclusive_queue(self):
        """Ensure that we can back-up exclusive queues, i.e. the replicating
        subscriptions are exempt from the exclusivity"""
        cluster = HaCluster(self, 2)
        def test(addr):
            c = cluster[0].connect()
            q = addr.split(";")[0]
            r = c.session().receiver(addr)
            try: c.session().receiver(addr); self.fail("Expected exclusive exception")
            except ReceiverError: pass
            s = c.session().sender(q).send(q)
            cluster[1].assert_browse_backup(q, [q])
        test("excl_sub;{create:always, link:{x-subscribe:{exclusive:True}}}");
        test("excl_queue;{create:always, node:{x-declare:{exclusive:True}}}")

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
        assert not valid_address(s1, "exad")
        assert valid_address(s1, "ex")
        assert valid_address(s1, "ad")
        assert valid_address(s1, "time")

        # Verify that auto-delete queues are not kept alive by
        # replicating subscriptions
        ad.close()
        s0.sync()
        assert not valid_address(s0, "ad")

    def test_broker_info(self):
        """Check that broker information is correctly published via management"""
        cluster = HaCluster(self, 3)

        for broker in cluster:  # Make sure HA system-id matches broker's
            qmf = broker.agent().getHaBroker()
            self.assertEqual(qmf.systemId, UUID(broker.agent().getBroker().systemRef))

        cluster_ports = map(lambda b: b.port(), cluster)
        cluster_ports.sort()
        def ports(qmf):
            qmf.update()
            return sorted(map(lambda b: b["port"], qmf.members))
        # Check that all brokers have the same membership as the cluster
        for broker in cluster:
            qmf = broker.agent().getHaBroker()
            assert retry(lambda: cluster_ports == ports(qmf), 1), "%s != %s on %s"%(cluster_ports, ports(qmf), broker)
        # Add a new broker, check it is updated everywhere
        b = cluster.start()
        cluster_ports.append(b.port())
        cluster_ports.sort()
        for broker in cluster:
            qmf = broker.agent().getHaBroker()
            assert retry(lambda: cluster_ports == ports(qmf), 1), "%s != %s"%(cluster_ports, ports(qmf))

    def test_auth(self):
        """Verify that authentication does not interfere with replication."""
        # FIXME aconway 2012-07-09: generate test sasl config portably for cmake
        sasl_config=os.path.join(self.rootdir, "sasl_config")
        if not os.path.exists(sasl_config):
            print "WARNING: Skipping test, SASL test configuration %s not found."%sasl_config
            return
        acl=os.path.join(os.getcwd(), "policy.acl")
        aclf=file(acl,"w")
        # Verify that replication works with auth=yes and HA user has at least the following
        # privileges:
        aclf.write("""
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
acl deny all all
 """)
        aclf.close()
        cluster = HaCluster(
            self, 2,
            args=["--auth", "yes", "--sasl-config", sasl_config,
                  "--acl-file", acl, "--load-module", os.getenv("ACL_LIB"),
                  "--ha-username=zag", "--ha-password=zag", "--ha-mechanism=PLAIN"
                  ],
            client_credentials=Credentials("zag", "zag", "PLAIN"))
        s0 = cluster[0].connect(username="zag", password="zag").session();
        s0.receiver("q;{create:always}")
        s0.receiver("ex;{create:always,node:{type:topic,x-declare:{type:'fanout'},x-bindings:[{exchange:'ex',queue:'q'}]}}")
        cluster[1].wait_backup("q")
        cluster[1].wait_backup("ex")
        s1 = cluster[1].connect_admin().session(); # Uses Credentials above.
        s1.sender("ex").send("foo");
        self.assertEqual(s1.receiver("q").fetch().content, "foo")

    def test_alternate_exchange(self):
        """Verify that alternate-exchange on exchanges and queues is propagated
        to new members of a cluster. """
        cluster = HaCluster(self, 2)
        s = cluster[0].connect().session()
        # altex exchange: acts as alternate exchange
        s.sender("altex;{create:always,node:{type:topic,x-declare:{type:'fanout'}}}")
        # altq queue bound to altex, collect re-routed messages.
        s.sender("altq;{create:always,node:{x-bindings:[{exchange:'altex',queue:altq}]}}")
        # ex exchange with alternate-exchange altex and no queues bound
        s.sender("ex;{create:always,node:{type:topic, x-declare:{type:'direct', alternate-exchange:'altex'}}}")
        # create queue q with alternate-exchange altex
        s.sender("q;{create:always,node:{type:queue, x-declare:{alternate-exchange:'altex'}}}")
        # create a bunch of exchanges to ensure we don't clean up prematurely if the
        # response comes in multiple fragments.
        for i in xrange(200): s.sender("ex.%s;{create:always,node:{type:topic}}"%i)

        def verify(broker):
            s = broker.connect().session()
            # Verify unmatched message goes to ex's alternate.
            s.sender("ex").send("foo")
            altq = s.receiver("altq")
            self.assertEqual("foo", altq.fetch(timeout=0).content)
            s.acknowledge()
            # Verify rejected message goes to q's alternate.
            s.sender("q").send("bar")
            msg = s.receiver("q").fetch(timeout=0)
            self.assertEqual("bar", msg.content)
            s.acknowledge(msg, Disposition(REJECTED)) # Reject the message
            self.assertEqual("bar", altq.fetch(timeout=0).content)
            s.acknowledge()

        def ss(n): return cluster[n].connect().session()

        # Sanity check: alternate exchanges on original broker
        verify(cluster[0])
        # Altex is in use as an alternate exchange.
        self.assertRaises(SessionError,
                          lambda:ss(0).sender("altex;{delete:always}").close())
        # Check backup that was connected during setup.
        cluster[1].wait_status("ready")
        cluster[1].wait_backup("ex")
        cluster[1].wait_backup("q")
        cluster.bounce(0)
        verify(cluster[1])

        # Check a newly started backup.
        cluster.start()
        cluster[2].wait_status("ready")
        cluster[2].wait_backup("ex")
        cluster[2].wait_backup("q")
        cluster.bounce(1)
        verify(cluster[2])

        # Check that alt-exchange in-use count is replicated
        s = cluster[2].connect().session();

        self.assertRaises(SessionError,
                          lambda:ss(2).sender("altex;{delete:always}").close())
        s.sender("q;{delete:always}").close()
        self.assertRaises(SessionError,
                          lambda:ss(2).sender("altex;{delete:always}").close())
        s.sender("ex;{delete:always}").close()
        s.sender("altex;{delete:always}").close()

    def test_priority_reroute(self):
        """Regression test for QPID-4262, rerouting messages from a priority queue
        to itself causes a crash"""
        cluster = HaCluster(self, 2)
        primary = cluster[0]
        session = primary.connect().session()
        s = session.sender("pq; {create:always, node:{x-declare:{arguments:{'qpid.priorities':10}},x-bindings:[{exchange:'amq.fanout',queue:pq}]}}")
        for m in xrange(100): s.send(Message(str(m), priority=m%10))
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
        self.assertRaises(NotFound, s.receiver, ("q2"));
        self.assertRaises(NotFound, s.receiver, ("e2"));


    def test_delete_qpid_4285(self):
        """Regression test for QPID-4285: on deleting a queue it gets stuck in a
        partially deleted state and causes replication errors."""
        cluster = HaCluster(self,2)
        s = cluster[0].connect().session()
        s.receiver("q;{create:always}")
        cluster[1].wait_backup("q")
        cluster.kill(0)       # Make the backup take over.
        s = cluster[1].connect().session()
        s.receiver("q;{delete:always}").close() # Delete q on new primary
        try:
            s.receiver("q")
            self.fail("Expected NotFound exception") # Should not be avaliable
        except NotFound: pass
        assert not cluster[1].agent().getQueue("q") # Should not be in QMF

    def alt_setup(self, session, suffix):
        # Create exchange to use as alternate and a queue bound to it.
        # altex exchange: acts as alternate exchange
        session.sender("altex%s;{create:always,node:{type:topic,x-declare:{type:'fanout'}}}"%(suffix))
        # altq queue bound to altex, collect re-routed messages.
        session.sender("altq%s;{create:always,node:{x-bindings:[{exchange:'altex%s',queue:altq%s}]}}"%(suffix,suffix,suffix))

    def test_auto_delete_close(self):
        """Verify auto-delete queues are deleted on backup if auto-deleted
        on primary"""
        cluster=HaCluster(self, 2)
        p = cluster[0].connect().session()
        self.alt_setup(p, "1")
        r = p.receiver("adq1;{create:always,node:{x-declare:{auto-delete:True,alternate-exchange:'altex1'}}}", capacity=1)
        s = p.sender("adq1")
        for m in ["aa","bb","cc"]: s.send(m)
        p.sender("adq2;{create:always,node:{x-declare:{auto-delete:True}}}")
        cluster[1].wait_queue("adq1")
        cluster[1].wait_queue("adq2")
        r.close()               # trigger auto-delete of adq1
        cluster[1].wait_no_queue("adq1")
        cluster[1].assert_browse_backup("altq1", ["aa","bb","cc"])
        cluster[1].wait_queue("adq2")

    def test_auto_delete_crash(self):
        """Verify auto-delete queues are deleted on backup if the primary crashes"""
        cluster=HaCluster(self, 2)
        p = cluster[0].connect().session()
        self.alt_setup(p,"1")

        # adq1 is subscribed so will be auto-deleted.
        r = p.receiver("adq1;{create:always,node:{x-declare:{auto-delete:True,alternate-exchange:'altex1'}}}", capacity=1)
        s = p.sender("adq1")
        for m in ["aa","bb","cc"]: s.send(m)
        # adq2 is subscribed after cluster[2] starts.
        p.sender("adq2;{create:always,node:{x-declare:{auto-delete:True}}}")
        # adq3 is never subscribed.
        p.sender("adq3;{create:always,node:{x-declare:{auto-delete:True}}}")

        cluster.start()
        cluster[2].wait_status("ready")

        p.receiver("adq2")      # Subscribed after cluster[2] joined

        for q in ["adq1","adq2","adq3","altq1"]: cluster[1].wait_queue(q)
        for q in ["adq1","adq2","adq3","altq1"]: cluster[2].wait_queue(q)
        cluster[0].kill()

        cluster[1].wait_no_queue("adq1")
        cluster[1].wait_no_queue("adq2")
        cluster[1].wait_queue("adq3")

        cluster[2].wait_no_queue("adq1")
        cluster[2].wait_no_queue("adq2")
        cluster[2].wait_queue("adq3")

        cluster[1].assert_browse_backup("altq1", ["aa","bb","cc"])
        cluster[2].assert_browse_backup("altq1", ["aa","bb","cc"])

    def test_auto_delete_timeout(self):
        cluster = HaCluster(self, 2)
        # Test timeout
        r1 = cluster[0].connect().session().receiver("q1;{create:always,node:{x-declare:{auto-delete:True,arguments:{'qpid.auto_delete_timeout':1}}}}")
        # Test special case of timeout = 0
        r0 = cluster[0].connect().session().receiver("q0;{create:always,node:{x-declare:{auto-delete:True,arguments:{'qpid.auto_delete_timeout':0}}}}")
        cluster[1].wait_queue("q0")
        cluster[1].wait_queue("q1")
        cluster[0].kill()
        cluster[1].wait_queue("q1")    # Not timed out yet
        cluster[1].wait_no_queue("q1", timeout=5) # Wait for timeout
        cluster[1].wait_no_queue("q0", timeout=5) # Wait for timeout

    def test_alt_exchange_dup(self):
        """QPID-4349: if a queue has an alterante exchange and is deleted the
        messages appear twice on the alternate, they are rerouted once by the
        primary and again by the backup."""
        cluster = HaCluster(self,2)

        # Set up q with alternate exchange altex bound to altq.
        s = cluster[0].connect().session()
        s.sender("altex;{create:always,node:{type:topic,x-declare:{type:'fanout'}}}")
        s.sender("altq;{create:always,node:{x-bindings:[{exchange:'altex',queue:altq}]}}")
        snd = s.sender("q;{create:always,node:{x-declare:{alternate-exchange:'altex'}}}")
        messages = [ str(n) for n in xrange(10) ]
        for m in messages: snd.send(m)
        cluster[1].assert_browse_backup("q", messages)
        s.sender("q;{delete:always}").close()
        cluster[1].assert_browse_backup("altq", messages)

    def test_expired(self):
        """Regression test for QPID-4379: HA does not properly handle expired messages"""
        # Race between messages expiring and HA replicating consumer.
        cluster = HaCluster(self, 2)
        s = cluster[0].connect().session().sender("q;{create:always}", capacity=2)
        def send_ttl_messages():
            for i in xrange(100): s.send(Message(str(i), ttl=0.001), timeout=1)
        send_ttl_messages()
        cluster.start()
        send_ttl_messages()

    def test_stale_response(self):
        """Check for race condition where a stale response is processed after an
        event for the same queue/exchange """
        cluster = HaCluster(self, 2)
        s = cluster[0].connect().session()
        s.sender("keep;{create:always}") # Leave this queue in place.
        for i in xrange(1000):
            s.sender("deleteme%s;{create:always,delete:always}"%(i)).close()
        # It is possible for the backup to attempt to subscribe after the queue
        # is deleted. This is not an error, but is logged as an error on the primary.
        # The backup does not log this as an error so we only check the backup log for errors.
        self.assert_log_no_errors(cluster[1])

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
        sn.sender("xx;{create:always,node:{type:topic}}")
        sn.sender("xxq;{create:always,node:{x-bindings:[{exchange:'xx',queue:'xxq',key:xxq}]}}")
        cluster[1].wait_address("xx")
        self.assertEqual(cluster[1].agent().getExchange("xx").values["bindingCount"], 1)
        cluster[2].wait_address("xx")
        self.assertEqual(cluster[2].agent().getExchange("xx").values["bindingCount"], 1)

        # Simulate the race by re-creating the objects before promoting the new primary
        cluster.kill(0, False)
        xdecl = "x-declare:{arguments:{'qpid.replicate':'all'}}"
        node = "node:{%s}"%(xdecl)
        sn = cluster[1].connect_admin().session()
        sn.sender("qq;{delete:always}").close()
        s = sn.sender("qq;{create:always, %s}"%(node))
        s.send("foo")
        sn.sender("xx;{delete:always}").close()
        sn.sender("xx;{create:always,node:{type:topic,%s}}"%(xdecl))
        cluster[1].promote()
        cluster[1].wait_status("active")
        # Verify we are not still using the old objects on cluster[2]
        cluster[2].assert_browse_backup("qq", ["foo"])
        cluster[2].wait_address("xx")
        self.assertEqual(cluster[2].agent().getExchange("xx").values["bindingCount"], 0)

    def test_redeclare_exchange(self):
        """Ensure that re-declaring an exchange is an HA no-op"""
        cluster = HaCluster(self, 2)
        ps = cluster[0].connect().session()
        ps.sender("ex1;{create:always,node:{type:topic,x-declare:{arguments:{'qpid.replicate':all}, type:'fanout'}}}")
        ps.sender("ex2;{create:always,node:{type:topic,x-declare:{arguments:{'qpid.replicate':all}, type:'fanout', alternate-exchange:'ex1'}}}")
        cluster[1].wait_backup("ex1")
        cluster[1].wait_backup("ex2")

        # Use old API to re-declare the exchange
        old_conn = cluster[0].connect_old()
        old_sess = old_conn.session(str(qpid.datatypes.uuid4()))
        old_sess.exchange_declare(exchange='ex1', type='fanout')
        cluster[1].wait_backup("ex1")

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
        senders = [NumberedSender(brokers[0], max_depth=1024, failover_updates=False,
                                 queue="test%s"%(i)) for i in xrange(n)]
        receivers = [NumberedReceiver(brokers[0], sender=senders[i],
                                      failover_updates=False,
                                      queue="test%s"%(i)) for i in xrange(n)]
        for r in receivers: r.start()
        for s in senders: s.start()

        def wait_passed(r, n):
            """Wait for receiver r to pass n"""
            def check():
                r.check()       # Verify no exceptions
                return r.received > n + 100
            assert retry(check), "Stalled %s at %s"%(r.queue, n)

        for r in receivers: wait_passed(r, 0)

        # Kill and restart brokers in a cycle:
        endtime = time.time() + self.duration()
        i = 0
        primary = 0
        try:
            while time.time() < endtime or i < 3: # At least 3 iterations
                # Precondition: All 3 brokers running,
                # primary = index of promoted primary
                # one or two backups are running,
                for s in senders: s.sender.assert_running()
                for r in receivers: r.receiver.assert_running()
                checkpoint = [ r.received+100 for r in receivers ]
                dead = None
                victim = random.randint(0,2)
                if victim == primary:
                    # Don't kill primary till it is active and the next
                    # backup is ready, otherwise we can lose messages.
                    brokers[victim].wait_status("active")
                    next = (victim+1)%3
                    brokers[next].wait_status("ready")
                    brokers.bounce(victim) # Next one is promoted
                    primary = next
                else:
                    brokers.kill(victim, False)
                    dead = victim

                # At this point the primary is running with 1 or 2 backups
                # Make sure we are not stalled
                map(wait_passed, receivers, checkpoint)
                # Run another checkpoint to ensure things work in this configuration
                checkpoint = [ r.received+100 for r in receivers ]
                map(wait_passed, receivers, checkpoint)

                if dead is not None:
                    brokers.restart(dead) # Restart backup
                    brokers[dead].ready()
                    dead = None
                i += 1
        except:
            traceback.print_exc()
            raise
        finally:
            for s in senders: s.stop()
            for r in receivers: r.stop()
            unexpected_dead = []
            for i in xrange(3):
                if not brokers[i].is_running() and i != dead:
                    unexpected_dead.append(i)
                if brokers[i].is_running(): brokers.kill(i, False)
            if unexpected_dead:
                raise Exception("Brokers not running: %s"%unexpected_dead)

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
                    except NotFound: pass # Can occur occasionally, not an error.
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
            s1 = cluster.connect(0).session().sender("q1;{create:always}")
            for b in cluster: b.wait_backup("q1")
            for i in xrange(100): s1.send(str(i))

            # Kill primary and 2 backups
            cluster[3].wait_status("ready")
            for i in [0,1,2]: cluster.kill(i, False)
            cluster[3].promote()    # New primary, backups will be 1 and 2
            cluster[3].wait_status("recovering")

            def assertSyncTimeout(s):
                try:
                    s.sync(timeout=.01)
                    self.fail("Expected Timeout exception")
                except Timeout: pass

            # Create a queue after the failure
            s2 = cluster.connect(3).session().sender("q2;{create:always}")

            # Verify that messages sent are not completed
            for i in xrange(100,200):
                s1.send(str(i), sync=False);
                s2.send(str(i), sync=False)
            assertSyncTimeout(s1)
            self.assertEqual(s1.unsettled(), 100)
            assertSyncTimeout(s2)
            self.assertEqual(s2.unsettled(), 100)

            # Verify we can receive even if sending is on hold:
            cluster[3].assert_browse("q1", [str(i) for i in range(200)])

            # Restart backups, verify queues are released only when both backups are up
            cluster.restart(1)
            assertSyncTimeout(s1)
            self.assertEqual(s1.unsettled(), 100)
            assertSyncTimeout(s2)
            self.assertEqual(s2.unsettled(), 100)
            cluster.restart(2)

            # Verify everything is up to date and active
            def settled(sender): sender.sync(timeout=1); return sender.unsettled() == 0;
            assert retry(lambda: settled(s1)), "Unsetttled=%s"%(s1.unsettled())
            assert retry(lambda: settled(s2)), "Unsetttled=%s"%(s2.unsettled())
            cluster[1].assert_browse_backup("q1", [str(i) for i in range(100)+range(100,200)])
            cluster[1].assert_browse_backup("q2", [str(i) for i in range(100,200)])
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
        cluster[0].wait_status("active") # Primary ready
        for b in cluster[1:3]: b.wait_status("ready") # Backups ready
        for i in [0,1]: cluster.kill(i, False)
        cluster[2].promote()    # New primary, backups will be 1 and 2
        cluster[2].wait_status("recovering")
        # Should not go active till the expected backup connects or times out.
        self.assertEqual(cluster[2].ha_status(), "recovering")
        # Messages should be held expected backup times out
        s = cluster[2].connect().session().sender("q;{create:always}")
        for i in xrange(100): s.send(str(i), sync=False)
        # Verify message held initially.
        try: s.sync(timeout=.01); self.fail("Expected Timeout exception")
        except Timeout: pass
        s.sync(timeout=1)      # And released after the timeout.
        self.assertEqual(cluster[2].ha_status(), "active")

    def test_join_ready_cluster(self):
        """If we join a cluster where the primary is dead, the new primary is
        not yet promoted and there are ready backups then we should refuse
        promotion so that one of the ready backups can be chosen."""
        # FIXME aconway 2012-10-05: smaller timeout
        cluster = HaCluster(self, 2, args=["--link-heartbeat-interval", 1])
        cluster[0].wait_status("active")
        cluster[1].wait_status("ready")
        cluster.bounce(0, promote_next=False)
        self.assertRaises(Exception, cluster[0].promote)
        os.kill(cluster[1].pid, signal.SIGSTOP) # Test for timeout if unresponsive.
        cluster.bounce(0, promote_next=False)
        cluster[0].promote()


class ConfigurationTests(HaBrokerTest):
    """Tests for configuration settings."""

    def test_client_broker_url(self):
        """Check that setting of broker and public URLs obeys correct defaulting
        and precedence"""

        def check(broker, brokers, public):
            qmf = broker.qmf()
            self.assertEqual(brokers, qmf.brokersUrl)
            self.assertEqual(public, qmf.publicUrl)

        def start(brokers, public, known=None):
            args=[]
            if brokers: args.append("--ha-brokers-url="+brokers)
            if public: args.append("--ha-public-url="+public)
            if known: args.append("--known-hosts-url="+known)
            return HaBroker(self, args=args)

        # Both set explictily, no defaulting
        b = start("foo:123", "bar:456")
        check(b, "amqp:tcp:foo:123", "amqp:tcp:bar:456")
        b.set_brokers_url("foo:999")
        check(b, "amqp:tcp:foo:999", "amqp:tcp:bar:456")
        b.set_public_url("bar:999")
        check(b, "amqp:tcp:foo:999", "amqp:tcp:bar:999")

        # Allow "none" to mean "not set"
        b = start("none", "none")
        check(b, "", "")

if __name__ == "__main__":
    shutil.rmtree("brokertest.tmp", True)
    qpid_ha = os.getenv("QPID_HA_EXEC")
    if  qpid_ha and os.path.exists(qpid_ha):
        os.execvp("qpid-python-test",
                  ["qpid-python-test", "-m", "ha_tests"] + sys.argv[1:])
    else:
        print "Skipping ha_tests, %s not available"%(qpid_ha)

