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
            session.receiver(a)
            self.fail("Should not have been replicated: %s"%(address))
        except NotFound: pass

    def test_replicate_wiring(self):
        queue="%s;{create:always,node:{x-declare:{arguments:{'qpid.replicate':%s}}}}"
        exchange="%s;{create:always,node:{type:topic,x-declare:{arguments:{'qpid.replicate':%s}, type:'fanout'},x-bindings:[{exchange:'%s',queue:'%s'}]}}"

        # Create some wiring before starting the backup, to test catch-up
        primary = self.ha_broker(name="primary")
        p = primary.connect().session()
        p.sender(queue%("q1", "all")).send(Message("1"))
        p.sender(queue%("q2", "wiring")).send(Message("2"))
        p.sender(queue%("q3", "none")).send(Message("3"))
        p.sender(exchange%("e1", "all", "e1", "q2")).send(Message("4"))

        # Create some after starting backup, test steady-state replication
        backup  = self.ha_broker(name="backup", broker_url=primary.host_port())
        b = backup.connect().session()
        # FIXME aconway 2011-11-21: need to wait for backup to be ready to test event replication
        for a in ["q1", "q2", "e1"]: self.wait(b,a)
        p.sender(queue%("q11", "all")).send(Message("11"))
        p.sender(queue%("q12", "wiring")).send(Message("12"))
        p.sender(queue%("q13", "none")).send(Message("13"))
        p.sender(exchange%("e11", "all", "e11", "q12")).send(Message("14"))

        # Verify replication
        # FIXME aconway 2011-11-18: We should kill primary here and fail over.
        for a in ["q11", "q12", "e11"]: self.wait(b,a)
        # FIXME aconway 2011-11-18: replicate messages
#         self.assert_browse(b, "q11", ["11", "14", "e11"])
#         self.assert_browse(b, "q12", []) # wiring only
#         self.assert_missing(b,"q13")
        b.sender("e11").send(Message("e11")) # Verify bind
        self.assert_browse(b, "q12", ["e11"])

        for a in ["q1", "q2", "e1"]: self.wait(b,a)
        # FIXME aconway 2011-11-18: replicate messages
#         self.assert_browse(b, "q1", ["1", "4", "e1"])
#         self.assert_browse(b, "q2", []) # wiring only
#         self.assert_missing(b,"q3")
        b.sender("e1").send(Message("e1")) # Verify bind
        self.assert_browse(b, "q2", ["e1"])


if __name__ == "__main__":
    shutil.rmtree("brokertest.tmp", True)
    os.execvp("qpid-python-test", ["qpid-python-test", "-m", "ha_tests"] + sys.argv[1:])
