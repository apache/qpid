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
from qpid.messaging import Message, NotFound
from brokertest import *
from threading import Thread, Lock, Condition
from logging import getLogger


log = getLogger("qpid.ha-tests")

class ShortTests(BrokerTest):
    """Short HA functionality tests."""

    def ha_broker(self, args=[], client_url="dummy", broker_url="dummy", **kwargs):
        assert BrokerTest.ha_lib, "Cannot locate HA plug-in"
        return Broker(self, args=["--load-module", BrokerTest.ha_lib,
                                  "--ha-enable=yes",
                                  "--ha-client-url", client_url,
                                  "--ha-broker-url", broker_url,
                                  ] + args,
                      **kwargs)

    # FIXME aconway 2011-11-15: work around async replication.
    def wait(self, session, address):
        def check():
            try:
                session.sender(address)
                return True
            except NotFound: return False
        assert retry(check), "Timed out waiting for %s"%(address)

    def assert_missing(self,session, address):
        try:
            session.receiver(address)
            self.fail("Should not have been replicated: %s"%(address))
        except NotFound: pass

    def test_replication(self):
        def queue(name, replicate):
            return "%s;{create:always,node:{x-declare:{arguments:{'qpid.replicate':%s}}}}"%(name, replicate)

        def exchange(name, replicate, bindq):
            return"%s;{create:always,node:{type:topic,x-declare:{arguments:{'qpid.replicate':%s}, type:'fanout'},x-bindings:[{exchange:'%s',queue:'%s'}]}}"%(name, replicate, name, bindq)
        def setup(p, prefix):
            """Create config, send messages on the primary p"""
            s = p.sender(queue(prefix+"q1", "all"))
            for m in ["a", "b", "1"]: s.send(Message(m))
            # Test replication of dequeue
            self.assertEqual(p.receiver(prefix+"q1").fetch(timeout=0).content, "a")
            p.acknowledge()
            p.sender(queue(prefix+"q2", "wiring")).send(Message("2"))
            p.sender(queue(prefix+"q3", "none")).send(Message("3"))
            p.sender(exchange(prefix+"e1", "all", prefix+"q1")).send(Message("4"))
            p.sender(exchange(prefix+"e2", "all", prefix+"q2")).send(Message("5"))
            # FIXME aconway 2011-11-24: need a marker so we can wait till sync is done.
            p.sender(queue(prefix+"x", "wiring"))

        def verify(b, prefix, p):
            """Verify setup was replicated to backup b"""

            # FIXME aconway 2011-11-21: wait for wiring to replicate.
            self.wait(b, prefix+"x");
            # FIXME aconway 2011-11-24: assert_browse_retry to deal with async replication.
            self.assert_browse_retry(b, prefix+"q1", ["b", "1", "4"])

            self.assertEqual(p.receiver(prefix+"q1").fetch(timeout=0).content, "b")
            p.acknowledge()
            self.assert_browse_retry(b, prefix+"q1", ["1", "4"])

            self.assert_browse_retry(b, prefix+"q2", []) # wiring only
            self.assert_missing(b, prefix+"q3")
            b.sender(prefix+"e1").send(Message(prefix+"e1")) # Verify binds with replicate=all
            self.assert_browse_retry(b, prefix+"q1", ["1", "4", prefix+"e1"])
            b.sender(prefix+"e2").send(Message(prefix+"e2")) # Verify binds with replicate=wiring
            self.assert_browse_retry(b, prefix+"q2", [prefix+"e2"])

        primary = self.ha_broker(name="primary", broker_url="primary") # Temp hack to identify primary
        p = primary.connect().session()
        # Create config, send messages before starting the backup, to test catch-up replication.
        setup(p, "1")
        backup  = self.ha_broker(name="backup", broker_url=primary.host_port())
        # Create config, send messages after starting the backup, to test steady-state replication.
        setup(p, "2")

        # Verify the data on the backup
        b = backup.connect().session()
        verify(b, "1", p)
        verify(b, "2", p)

        # Test a series of messages, enqueue and dequeue.
        s = p.sender(queue("foo","all"))
        msgs = [str(i) for i in range(10)]
        for m in msgs: s.send(Message(m))
        self.assert_browse_retry(b, "foo", msgs)
        self.assert_browse_retry(p, "foo", msgs)
        r = p.receiver("foo")
        for m in msgs: self.assertEqual(m, r.fetch(timeout=0).content)
        p.acknowledge()
        self.assert_browse_retry(p, "foo", [])
        self.assert_browse_retry(b, "foo", [])

    def qpid_replicate(self, value="all"):
        return "node:{x-declare:{arguments:{'qpid.replicate':%s}}}" % value

    def test_sync(self):
        def queue(name, replicate):
            return "%s;{create:always,%s}"%(name, self.qpid_replicate(replicate))
        primary = self.ha_broker(name="primary", broker_url="primary") # Temp hack to identify primary
        p = primary.connect().session()
        s = p.sender(queue("q","all"))
        for m in [str(i) for i in range(0,10)]: s.send(m)
        s.sync()
        backup1 = self.ha_broker(name="backup1", broker_url=primary.host_port())
        for m in [str(i) for i in range(10,20)]: s.send(m)
        s.sync()
        backup2 = self.ha_broker(name="backup2", broker_url=primary.host_port())
        for m in [str(i) for i in range(20,30)]: s.send(m)
        s.sync()

        msgs = [str(i) for i in range(30)]
        b1 = backup1.connect().session()
        self.wait(b1, "q");
        self.assert_browse_retry(b1, "q", msgs)
        b2 = backup2.connect().session()
        self.wait(b2, "q");
        self.assert_browse_retry(b2, "q", msgs)

    def test_send_receive(self):
        primary = self.ha_broker(name="primary", broker_url="primary")
        backup1 = self.ha_broker(name="backup1", broker_url=primary.host_port())
        backup2 = self.ha_broker(name="backup2", broker_url=primary.host_port())
        sender = self.popen(
            ["qpid-send",
             "--broker", primary.host_port(),
             "--address", "q;{create:always,%s}"%(self.qpid_replicate("all")),
             "--messages=1000",
             "--content-string=x"
             ])
        receiver = self.popen(
            ["qpid-receive",
             "--broker", primary.host_port(),
             "--address", "q;{create:always,%s}"%(self.qpid_replicate("all")),
             "--messages=990",
             "--timeout=10"
             ])
        try:
            self.assertEqual(sender.wait(), 0)
            self.assertEqual(receiver.wait(), 0)
            expect = [long(i) for i in range(991, 1001)]
            sn = lambda m: m.properties["sn"]
            self.assert_browse_retry(backup1.connect().session(), "q", expect, transform=sn)
            self.assert_browse_retry(backup2.connect().session(), "q", expect, transform=sn)
        except:
            print self.browse(primary.connect().session(), "q", transform=sn)
            print self.browse(backup1.connect().session(), "q", transform=sn)
            raise

if __name__ == "__main__":
    shutil.rmtree("brokertest.tmp", True)
    os.execvp("qpid-python-test", ["qpid-python-test", "-m", "ha_tests"] + sys.argv[1:])
