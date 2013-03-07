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

"""
This module contains tests for HA functionality that requires a store.
It will only be run if the STORE_LIB environment variable is defined.
"""

import os, signal, sys, time, imp, re, subprocess, glob, random, logging, shutil, math, unittest, random
import traceback
from qpid.messaging import Message, NotFound, ConnectionError, ReceiverError, Connection, Timeout, Disposition, REJECTED, Empty
from qpid.datatypes import uuid4
from brokertest import *
from ha_test import *
from threading import Thread, Lock, Condition
from logging import getLogger, WARN, ERROR, DEBUG, INFO
from qpidtoollibs import BrokerAgent
from uuid import UUID


class StoreTests(BrokerTest):
    """Test for HA with persistence."""

    def test_store_recovery(self):
        """Verify basic store and recover functionality"""
        cluster = HaCluster(self, 1)
        sn = cluster[0].connect().session()
        # Create queue qq, exchange exx and binding between them
        s = sn.sender("qq;{create:always,node:{durable:true}}")
        sk = sn.sender("exx/k;{create:always,node:{type:topic, durable:true, x-declare:{type:'direct'}, x-bindings:[{exchange:exx,key:k,queue:qq}]}}")
        for m in ["foo", "bar", "baz"]: s.send(Message(m, durable=True))
        r = cluster[0].connect().session().receiver("qq")
        self.assertEqual(r.fetch().content, "foo")
        r.session.acknowledge()
        # FIXME aconway 2012-09-21: sending this message is an ugly hack to flush
        # the dequeue operation on qq.
        s.send(Message("flush", durable=True))

        def verify(broker, x_count):
            sn = broker.connect().session()
            assert_browse(sn, "qq", [ "bar", "baz", "flush" ]+ (x_count)*["x"])
            sn.sender("exx/k").send(Message("x", durable=True))
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
        cluster = HaCluster(self, 2)
        sn = cluster[0].connect().session()
        s1 = sn.sender("q1;{create:always,node:{durable:true}}")
        for m in ["foo","bar"]: s1.send(Message(m, durable=True))
        s2 = sn.sender("q2;{create:always,node:{durable:true}}")
        sk2 = sn.sender("ex/k2;{create:always,node:{type:topic, durable:true, x-declare:{type:'direct'}, x-bindings:[{exchange:ex,key:k2,queue:q2}]}}")
        sk2.send(Message("hello", durable=True))
        # Wait for backup to catch up.
        cluster[1].assert_browse_backup("q1", ["foo","bar"]) 
        cluster[1].assert_browse_backup("q2", ["hello"])

        # Make changes that the backup doesn't see
        cluster.kill(1, promote_next=False)
        r1 = cluster[0].connect().session().receiver("q1")
        for m in ["foo", "bar"]: self.assertEqual(r1.fetch().content, m)
        r1.session.acknowledge()
        for m in ["x","y","z"]: s1.send(Message(m, durable=True))
        # Use old connection to unbind
        us = cluster[0].connect_old().session(str(uuid4()))
        us.exchange_unbind(exchange="ex", binding_key="k2", queue="q2")
        us.exchange_bind(exchange="ex", binding_key="k1", queue="q1")
        # Restart both brokers from store to get inconsistent sequence numbering.
        cluster.bounce(0, promote_next=False)
        cluster[0].promote()
        cluster[0].wait_status("active")
        cluster.restart(1)
        cluster[1].wait_status("ready")

        # Verify state
        cluster[0].assert_browse("q1",  ["x","y","z"])
        cluster[1].assert_browse_backup("q1",  ["x","y","z"])
        sn = cluster[0].connect().session() # FIXME aconway 2012-09-25: should fail over!
        sn.sender("ex/k1").send("boo")
        cluster[0].assert_browse_backup("q1", ["x","y","z", "boo"])
        cluster[1].assert_browse_backup("q1", ["x","y","z", "boo"])
        sn.sender("ex/k2").send("hoo") # q2 was unbound so this should be dropped.
        sn.sender("q2").send("end")    # mark the end of the queue for assert_browse
        cluster[0].assert_browse("q2", ["hello", "end"])
        cluster[1].assert_browse_backup("q2", ["hello", "end"])

if __name__ == "__main__":
    shutil.rmtree("brokertest.tmp", True)
    qpid_ha = os.getenv("QPID_HA_EXEC")
    if  qpid_ha and os.path.exists(qpid_ha):
        os.execvp("qpid-python-test",
                  ["qpid-python-test", "-m", "ha_store_tests"] + sys.argv[1:])
    else:
        print "Skipping ha_store_tests, %s not available"%(qpid_ha)
