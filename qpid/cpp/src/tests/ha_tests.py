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

    def setup_wiring(self, primary, backup):
        cmd="qpid-route route add %s %s qpid.node-cloner x"%(backup, primary)
        self.assertEqual(0, os.system(cmd))

    def setup_replication(self, primary, backup, queue):
        self.assertEqual(0,os.system("qpid-route --ack 1 queue add %s %s qpid.replicator-%s %s"%(backup, primary, queue, queue)))

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
            p.sender(queue(prefix+"q1", "all")).send(Message("1"))
            p.sender(queue(prefix+"q2", "wiring")).send(Message("2"))
            p.sender(queue(prefix+"q3", "none")).send(Message("3"))
            p.sender(exchange(prefix+"e1", "all", prefix+"q1")).send(Message("4"))
            p.sender(exchange(prefix+"e2", "all", prefix+"q2")).send(Message("5"))
            # FIXME aconway 2011-11-24: need a marker so we can wait till sync is done.
            p.sender(queue(prefix+"x", "wiring"))

        def verify(b, prefix):
            """Verify setup was replicated to backup b"""
            # FIXME aconway 2011-11-21: wait for wiring to replicate.
            self.wait(b, prefix+"x");
            # Verify backup
            # FIXME aconway 2011-11-24: assert_browse_retry to deal with async replication.
            self.assert_browse_retry(b, prefix+"q1", ["1", "4"])
            self.assert_browse_retry(b, prefix+"q2", []) # wiring only
            self.assert_missing(b, prefix+"q3")
            b.sender(prefix+"e1").send(Message(prefix+"e1")) # Verify binds with replicate=all
            self.assert_browse_retry(b, prefix+"q1", ["1", "4", prefix+"e1"])
            b.sender(prefix+"e2").send(Message(prefix+"e2")) # Verify binds with replicate=wiring
            self.assert_browse_retry(b, prefix+"q2", [prefix+"e2"])

        # Create config, send messages before starting the backup, to test catch-up replication.
        primary = self.ha_broker(name="primary")
        p = primary.connect().session()
        setup(p, "1")
        # Start the backup
        backup  = self.ha_broker(name="backup", broker_url=primary.host_port())
        b = backup.connect().session()
        verify(b, "1")

        # Create config, send messages after starting the backup, to test steady-state replication.
        setup(p, "2")
        verify(b, "2")

if __name__ == "__main__":
    shutil.rmtree("brokertest.tmp", True)
    os.execvp("qpid-python-test", ["qpid-python-test", "-m", "ha_tests"] + sys.argv[1:])
