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

import os, signal, sys, time, imp, re, subprocess, glob, random, logging, shutil
from qpid.messaging import Message, NotFound, ConnectionError, Connection
from brokertest import *
from threading import Thread, Lock, Condition
from logging import getLogger, WARN, ERROR, DEBUG


log = getLogger("qpid.ha-tests")

class HaBroker(Broker):
    def __init__(self, test, args=[], broker_url=None, **kwargs):
        assert BrokerTest.ha_lib, "Cannot locate HA plug-in"
        Broker.__init__(self, test,
                        args=["--load-module", BrokerTest.ha_lib,
                              "--ha-enable=yes",
                              "--ha-broker-url", broker_url ],
                        **kwargs)

    def promote(self):
        assert os.system("qpid-ha-tool --promote %s"%(self.host_port())) == 0

    def set_client_url(self, url):
        assert os.system(
            "qpid-ha-tool --client-addresses=%s %s"%(url,self.host_port())) == 0

    def set_broker_url(self, url):
        assert os.system(
            "qpid-ha-tool --broker-addresses=%s %s"%(url, self.host_port())) == 0


class ShortTests(BrokerTest):
    """Short HA functionality tests."""

    # FIXME aconway 2011-11-15: work around async configuration replication.
    # Wait for an address to become valid.
    def wait(self, session, address):
        def check():
            try:
                session.sender(address)
                return True
            except NotFound: return False
        assert retry(check), "Timed out waiting for %s"%(address)

    # FIXME aconway 2012-01-23: work around async configuration replication.
    # Wait for address to become valid on a backup broker.
    def wait_backup(self, backup, address):
        bs = self.connect_admin(backup).session()
        self.wait(bs, address)
        bs.connection.close()

    # Combines wait_backup and assert_browse_retry
    def assert_browse_backup(self, backup, queue, expected, **kwargs):
        bs = self.connect_admin(backup).session()
        self.wait(bs, queue)
        self.assert_browse_retry(bs, queue, expected, **kwargs)
        bs.connection.close()

    def assert_missing(self, session, address):
        try:
            session.receiver(address)
            self.fail("Should not have been replicated: %s"%(address))
        except NotFound: pass

    def connect_admin(self, backup, **kwargs):
        """Connect to a backup broker as an admin connection"""
        return backup.connect(client_properties={"qpid.ha-admin":1}, **kwargs)

    def test_replication(self):
        """Test basic replication of configuration and messages before and
        after backup has connected"""

        def queue(name, replicate):
            return "%s;{create:always,node:{x-declare:{arguments:{'qpid.replicate':%s}}}}"%(name, replicate)

        def exchange(name, replicate, bindq):
            return"%s;{create:always,node:{type:topic,x-declare:{arguments:{'qpid.replicate':%s}, type:'fanout'},x-bindings:[{exchange:'%s',queue:'%s'}]}}"%(name, replicate, name, bindq)
        def setup(p, prefix, primary):
            """Create config, send messages on the primary p"""
            s = p.sender(queue(prefix+"q1", "messages"))
            for m in ["a", "b", "1"]: s.send(Message(m))
            # Test replication of dequeue
            self.assertEqual(p.receiver(prefix+"q1").fetch(timeout=0).content, "a")
            p.acknowledge()
            p.sender(queue(prefix+"q2", "configuration")).send(Message("2"))
            p.sender(queue(prefix+"q3", "none")).send(Message("3"))
            p.sender(exchange(prefix+"e1", "messages", prefix+"q1")).send(Message("4"))
            p.sender(exchange(prefix+"e2", "messages", prefix+"q2")).send(Message("5"))
            # Test  unbind
            p.sender(queue(prefix+"q4", "messages")).send(Message("6"))
            s3 = p.sender(exchange(prefix+"e4", "messages", prefix+"q4"))
            s3.send(Message("7"))
            # Use old connection to unbind
            us = primary.connect_old().session(str(qpid.datatypes.uuid4()))
            us.exchange_unbind(exchange=prefix+"e4", binding_key="", queue=prefix+"q4")
            p.sender(prefix+"e4").send(Message("drop1")) # Should be dropped
            # FIXME aconway 2011-11-24: need a marker so we can wait till sync is done.
            p.sender(queue(prefix+"x", "configuration"))

        def verify(b, prefix, p):
            """Verify setup was replicated to backup b"""

            # FIXME aconway 2011-11-21: wait for configuration to replicate.
            self.wait(b, prefix+"x");
            # FIXME aconway 2011-11-24: assert_browse_retry to deal with async replication.
            self.assert_browse_retry(b, prefix+"q1", ["b", "1", "4"])

            self.assertEqual(p.receiver(prefix+"q1").fetch(timeout=0).content, "b")
            p.acknowledge()
            self.assert_browse_retry(b, prefix+"q1", ["1", "4"])

            self.assert_browse_retry(b, prefix+"q2", []) # configuration only
            self.assert_missing(b, prefix+"q3")
            b.sender(prefix+"e1").send(Message(prefix+"e1")) # Verify binds with replicate=all
            self.assert_browse_retry(b, prefix+"q1", ["1", "4", prefix+"e1"])
            b.sender(prefix+"e2").send(Message(prefix+"e2")) # Verify binds with replicate=configuration
            self.assert_browse_retry(b, prefix+"q2", [prefix+"e2"])

            b.sender(prefix+"e4").send(Message("drop2")) # Verify unbind.
            self.assert_browse_retry(b, prefix+"q4", ["6","7"])

        primary = HaBroker(self, name="primary")
        primary.promote()
        p = primary.connect().session()

        # Create config, send messages before starting the backup, to test catch-up replication.
        setup(p, "1", primary)
        backup  = HaBroker(self, name="backup", broker_url=primary.host_port())
        # Create config, send messages after starting the backup, to test steady-state replication.
        setup(p, "2", primary)

        # Verify the data on the backup
        b = self.connect_admin(backup).session()
        verify(b, "1", p)
        verify(b, "2", p)
        # Test a series of messages, enqueue all then dequeue all.
        s = p.sender(queue("foo","messages"))
        self.wait(b, "foo")
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

    def qpid_replicate(self, value="messages"):
        return "node:{x-declare:{arguments:{'qpid.replicate':%s}}}" % value

    def test_sync(self):
        def queue(name, replicate):
            return "%s;{create:always,%s}"%(name, self.qpid_replicate(replicate))
        primary = HaBroker(self, name="primary")
        primary.promote()
        p = primary.connect().session()
        s = p.sender(queue("q","messages"))
        for m in [str(i) for i in range(0,10)]: s.send(m)
        s.sync()
        backup1 = HaBroker(self, name="backup1", broker_url=primary.host_port())
        for m in [str(i) for i in range(10,20)]: s.send(m)
        s.sync()
        backup2 = HaBroker(self, name="backup2", broker_url=primary.host_port())
        for m in [str(i) for i in range(20,30)]: s.send(m)
        s.sync()

        msgs = [str(i) for i in range(30)]
        b1 = self.connect_admin(backup1).session()
        self.wait(b1, "q");
        self.assert_browse_retry(b1, "q", msgs)
        b2 = self.connect_admin(backup2).session()
        self.wait(b2, "q");
        self.assert_browse_retry(b2, "q", msgs)

    def test_send_receive(self):
        """Verify sequence numbers of messages sent by qpid-send"""
        primary = HaBroker(self, name="primary")
        primary.promote()
        backup1 = HaBroker(self, name="backup1", broker_url=primary.host_port())
        backup2 = HaBroker(self, name="backup2", broker_url=primary.host_port())
        sender = self.popen(
            ["qpid-send",
             "--broker", primary.host_port(),
             "--address", "q;{create:always,%s}"%(self.qpid_replicate("messages")),
             "--messages=1000",
             "--content-string=x"
             ])
        receiver = self.popen(
            ["qpid-receive",
             "--broker", primary.host_port(),
             "--address", "q;{create:always,%s}"%(self.qpid_replicate("messages")),
             "--messages=990",
             "--timeout=10"
             ])
        try:
            self.assertEqual(sender.wait(), 0)
            self.assertEqual(receiver.wait(), 0)
            expect = [long(i) for i in range(991, 1001)]
            sn = lambda m: m.properties["sn"]
            self.assert_browse_retry(self.connect_admin(backup1).session(), "q", expect, transform=sn)
            self.assert_browse_retry(self.connect_admin(backup2).session(), "q", expect, transform=sn)
        except:
            print self.browse(primary.connect().session(), "q", transform=sn)
            print self.browse(self.connect_admin(backup1).session(), "q", transform=sn)
            print self.browse(self.connect_admin(backup2).session(), "q", transform=sn)
            raise

    def test_failover(self):
        """Verify that backups rejects connections and that fail-over works in python client"""
        getLogger().setLevel(ERROR) # Disable WARNING log messages due to failover
        primary = HaBroker(self, name="primary", expect=EXPECT_EXIT_FAIL)
        primary.promote()
        backup = HaBroker(self, name="backup", broker_url=primary.host_port())
        # Check that backup rejects normal connections
        try:
            backup.connect()
            self.fail("Expected connection to backup to fail")
        except ConnectionError: pass
        # Check that admin connections are allowed to backup.
        self.connect_admin(backup).close()

        # Test discovery: should connect to primary after reject by backup
        c = backup.connect(reconnect_urls=[primary.host_port(), backup.host_port()], reconnect=True)
        s = c.session()
        sender = s.sender("q;{create:always,%s}"%(self.qpid_replicate()))
        self.wait_backup(backup, "q")
        sender.send("foo")
        primary.kill()
        assert retry(lambda: not is_running(primary.pid))
        backup.promote()
        self.assert_browse_retry(s, "q", ["foo"])
        c.close()

    def test_failover_cpp(self):
        primary = HaBroker(self, name="primary", expect=EXPECT_EXIT_FAIL)
        primary.promote()
        backup = HaBroker(self, name="backup", broker_url=primary.host_port())
        url="%s,%s"%(primary.host_port(), backup.host_port())
        primary.connect().session().sender("q;{create:always,%s}"%(self.qpid_replicate()))
        self.wait_backup(backup, "q")

        sender = NumberedSender(primary, url=url, queue="q", failover_updates = False)
        receiver = NumberedReceiver(primary, url=url, queue="q", failover_updates = False)
        receiver.start()
        sender.start()
        self.wait_backup(backup, "q")
        assert retry(lambda: receiver.received > 10) # Wait for some messages to get thru

        primary.kill()
        assert retry(lambda: not is_running(primary.pid)) # Wait for primary to die
        backup.promote()
        n = receiver.received       # Make sure we are still running
        assert retry(lambda: receiver.received > n + 10)
        sender.stop()
        receiver.stop()

if __name__ == "__main__":
    shutil.rmtree("brokertest.tmp", True)
    os.execvp("qpid-python-test", ["qpid-python-test", "-m", "ha_tests"] + sys.argv[1:])
