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

# Runs QMF tests against a broker running with QMFv1 disabled.  This forces the
# broker to use QMFv2 only.  This is necessary as if there is a bug in V2, some
# V1 operations may hide that (esp. asynchonous notifications)


import sys, shutil, os
from time import sleep
from brokertest import *
from qpid.messaging import Message
try: import qmf.console
except: print "Cannot import module qmf.console, skipping tests"; exit(0);


class ConsoleTest(BrokerTest):
    """
    Test QMFv2 support using the qmf.console library.
    """
    PUB_INTERVAL=1

    def setUp(self):
        BrokerTest.setUp(self)
        args = ["--mgmt-qmf1=no",
                "--mgmt-pub-interval=%d" % self.PUB_INTERVAL]
        self.broker = BrokerTest.broker(self, args)

    def _startQmfV2(self, broker, console=None):
        # I manually set up the QMF session here rather than call the startQmf
        # method from BrokerTest as I can guarantee the console library is used
        # (assuming BrokerTest's implementation of startQmf could change)
        self.qmf_session = qmf.console.Session(console)
        self.qmf_broker = self.qmf_session.addBroker("%s:%s" % (broker.host(),
                                                                broker.port()))
        self.assertEqual(self.qmf_broker.getBrokerAgent().isV2, True,
                         "Expected broker agent to support QMF V2")


    def _create_queue( self, q_name, args={} ):
        broker = self.qmf_session.getObjects(_class="broker")[0]
        result = broker.create("queue", q_name, args, False)
        self.assertEqual(result.status, 0, result)


    def test_method_call(self):
        """ Verify method calls work, and check the behavior of getObjects()
        call
        """
        self._startQmfV2( self.broker )
        self._create_queue( "fleabag", {"auto-delete":True} )

        qObj = None
        queues = self.qmf_session.getObjects(_class="queue")
        for q in queues:
            if q.name == "fleabag":
                qObj = q
                break
        self.assertNotEqual(qObj, None, "Failed to get queue object")
        #print qObj

    def test_unsolicited_updates(self):
        """ Verify that the Console callbacks work
        """

        class Handler(qmf.console.Console):
            def __init__(self):
                self.broker_info = []
                self.broker_conn = []
                self.newpackage = []
                self.newclass = []
                self.agents = []
                self.events = []
                self.updates = {}  # holds the objects by OID
                self.heartbeats = []

            def brokerInfo(self, broker):
                #print "brokerInfo:", broker
                self.broker_info.append(broker)
            def brokerConnected(self, broker):
                #print "brokerConnected:", broker
                self.broker_conn.append(broker)
            def newPackage(self, name):
                #print "newPackage:", name
                self.newpackage.append(name)
            def newClass(self, kind, classKey):
                #print "newClass:", kind, classKey
                self.newclass.append( (kind, classKey) )
            def newAgent(self, agent):
                #print "newAgent:", agent
                self.agents.append( agent )
            def event(self, broker, event):
                #print "EVENT %s" % event
                self.events.append(event)
            def objectProps(self, broker, record):
                #print "ObjProps %s" % record
                assert len(record.getProperties()), "objectProps() invoked with no properties?"
                oid = record.getObjectId()
                if oid not in self.updates:
                    self.updates[oid] = record
                else:
                    self.updates[oid].mergeUpdate( record )
            def objectStats(self, broker, record):
                #print "ObjStats %s" % record
                assert len(record.getStatistics()), "objectStats() invoked with no properties?"
                oid = record.getObjectId()
                if oid not in self.updates:
                    self.updates[oid] = record
                else:
                    self.updates[oid].mergeUpdate( record )
            def heartbeat(self, agent, timestamp):
                #print "Heartbeat %s" % agent
                self.heartbeats.append( (agent, timestamp) )

        handler = Handler()
        self._startQmfV2( self.broker, handler )
        # this should force objectProps, queueDeclare Event callbacks
        self._create_queue( "fleabag", {"auto-delete":True} )
        # this should force objectStats callback
        self.broker.send_message( "fleabag", Message("Hi") )
        # and we should get a few heartbeats
        sleep(self.PUB_INTERVAL)
        self.broker.send_message( "fleabag", Message("Hi") )
        sleep(self.PUB_INTERVAL)
        self.broker.send_message( "fleabag", Message("Hi") )
        sleep(self.PUB_INTERVAL * 2)

        assert handler.broker_info, "No BrokerInfo callbacks received"
        assert handler.broker_conn, "No BrokerConnected callbacks received"
        assert handler.newpackage, "No NewPackage callbacks received"
        assert handler.newclass, "No NewClass callbacks received"
        assert handler.agents, "No NewAgent callbacks received"
        assert handler.events, "No event callbacks received"
        assert handler.updates, "No updates received"
        assert handler.heartbeats, "No heartbeat callbacks received"

        # now verify updates for queue "fleabag" were received, and the
        # msgDepth statistic is correct

        msgs = 0
        for o in handler.updates.itervalues():
            key = o.getClassKey()
            if key and key.getClassName() == "queue" and o.name == "fleabag":
                assert o.msgDepth, "No update to msgDepth statistic!"
                msgs = o.msgDepth
                break
        assert msgs == 3, "msgDepth statistics not accurate!"

    def test_async_method(self):
        class Handler (qmf.console.Console):
            def __init__(self):
                self.cv = Condition()
                self.xmtList = {}
                self.rcvList = {}

            def methodResponse(self, broker, seq, response):
                self.cv.acquire()
                try:
                    self.rcvList[seq] = response
                finally:
                    self.cv.release()

            def request(self, broker, count):
                self.count = count
                for idx in range(count):
                    self.cv.acquire()
                    try:
                        seq = broker.echo(idx, "Echo Message", _async = True)
                        self.xmtList[seq] = idx
                    finally:
                        self.cv.release()

            def check(self):
                if self.count != len(self.xmtList):
                    return "fail (attempted send=%d, actual sent=%d)" % (self.count, len(self.xmtList))
                lost = 0
                mismatched = 0
                for seq in self.xmtList:
                    value = self.xmtList[seq]
                    if seq in self.rcvList:
                        result = self.rcvList.pop(seq)
                        if result.sequence != value:
                            mismatched += 1
                    else:
                        lost += 1
                spurious = len(self.rcvList)
                if lost == 0 and mismatched == 0 and spurious == 0:
                    return "pass"
                else:
                    return "fail (lost=%d, mismatch=%d, spurious=%d)" % (lost, mismatched, spurious)

        handler = Handler()
        self._startQmfV2(self.broker, handler)
        broker = self.qmf_session.getObjects(_class="broker")[0]
        handler.request(broker, 20)
        sleep(1)
        self.assertEqual(handler.check(), "pass")


if __name__ == "__main__":
    shutil.rmtree("brokertest.tmp", True)
    os.execvp("qpid-python-test",
              ["qpid-python-test", "-m", "qpidd_qmfv2_tests"] + sys.argv[1:])

