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
                session.receiver(address)
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
        s = primary.connect().session()
        s.sender(queue%("q1", "all")).send(Message("1"))
        s.sender(queue%("q2", "wiring")).send(Message("2"))
        s.sender(queue%("q3", "none")).send(Message("3"))
        s.sender(exchange%("e1", "all", "e1", "q2")).send(Message("4"))

        # Create some after starting backup, test steady-state replication
        backup  = self.ha_broker(name="backup", broker_url=primary.host_port())
        s.sender(queue%("q01", "all")).send(Message("01"))
        s.sender(queue%("q02", "wiring")).send(Message("02"))
        s.sender(queue%("q03", "none")).send(Message("03"))
        s.sender(exchange%("e01", "all", "e01", "q02")).send(Message("04"))

        # Verify replication
        # FIXME aconway 2011-11-18: We should kill primary here and fail over.
        s = backup.connect().session()
        for a in ["q01", "q02", "e01"]: self.wait(s,a)
        # FIXME aconway 2011-11-18: replicate messages
#         self.assert_browse(s, "q01", ["01", "04", "e01"])
#         self.assert_browse(s, "q02", []) # wiring only
#         self.assert_missing(s,"q03")
        s.sender("e01").send(Message("e01")) # Verify bind
        self.assert_browse(s, "q02", ["e01"])

        for a in ["q1", "q2", "e1"]: self.wait(s,a)
        # FIXME aconway 2011-11-18: replicate messages
#         self.assert_browse(s, "q1", ["1", "4", "e1"])
#         self.assert_browse(s, "q2", []) # wiring only
#         self.assert_missing(s,"q3")
        s.sender("e1").send(Message("e1")) # Verify bind
        self.assert_browse(s, "q2", ["e1"])


if __name__ == "__main__":
    shutil.rmtree("brokertest.tmp", True)
    os.execvp("qpid-python-test", ["qpid-python-test", "-m", "ha_tests"] + sys.argv[1:])
