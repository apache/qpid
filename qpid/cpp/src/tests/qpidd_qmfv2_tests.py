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

import qpid.messaging, brokertest
brokertest.qm = qpid.messaging             # TODO aconway 2014-04-04: Tests fail with SWIG client.

class ConsoleTest(BrokerTest):
    """
    Test QMFv2 support using the qmf.console library.
    """
    PUB_INTERVAL=1

    def setUp(self):
        BrokerTest.setUp(self)

    def _startBroker(self, QMFv1=False ):
        self._broker_is_v1 = QMFv1
        if self._broker_is_v1:
            args = ["--mgmt-qmf1=yes", "--mgmt-qmf2=no"]
        else:
            args = ["--mgmt-qmf1=no", "--mgmt-qmf2=yes"]

        args.append("--mgmt-pub-interval=%d" % self.PUB_INTERVAL)
        self.broker = BrokerTest.broker(self, args)


    def _myStartQmf(self, broker, console=None):
        # I manually set up the QMF session here rather than call the startQmf
        # method from BrokerTest as I can guarantee the console library is used
        # (assuming BrokerTest's implementation of startQmf could change)
        self.qmf_session = qmf.console.Session(console)
        self.qmf_broker = self.qmf_session.addBroker("%s:%s" % (broker.host(),
                                                                broker.port()))

    def _create_queue( self, q_name, args={} ):
        broker = self.qmf_session.getObjects(_class="broker")[0]
        result = broker.create("queue", q_name, args, False)
        self.assertEqual(result.status, 0, result)


    def _test_method_call(self):
        """ Verify method calls work, and check the behavior of getObjects()
        call
        """
        self._myStartQmf( self.broker )
        self._create_queue( "fleabag", {"auto-delete":True} )

        qObj = None
        queues = self.qmf_session.getObjects(_class="queue")
        for q in queues:
            if q.name == "fleabag":
                qObj = q
                break
        self.assertNotEqual(qObj, None, "Failed to get queue object")
        #print qObj

    def _test_unsolicited_updates(self):
        """ Verify that the Console callbacks work
        """

        class Handler(qmf.console.Console):
            def __init__(self):
                self.v1_oids = 0
                self.v1_events = 0
                self.v2_oids = 0
                self.v2_events = 0
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
                if event.isV2:
                    self.v2_events += 1
                else:
                    self.v1_events += 1

            def heartbeat(self, agent, timestamp):
                #print "Heartbeat %s" % agent
                self.heartbeats.append( (agent, timestamp) )

            # generic handler for objectProps and objectStats
            def _handle_obj_update(self, record):
                oid = record.getObjectId()
                if oid.isV2:
                    self.v2_oids += 1
                else:
                    self.v1_oids += 1

                if oid not in self.updates:
                    self.updates[oid] = record
                else:
                    self.updates[oid].mergeUpdate( record )

            def objectProps(self, broker, record):
                assert len(record.getProperties()), "objectProps() invoked with no properties?"
                self._handle_obj_update(record)

            def objectStats(self, broker, record):
                assert len(record.getStatistics()), "objectStats() invoked with no properties?"
                self._handle_obj_update(record)

        handler = Handler()
        self._myStartQmf( self.broker, handler )
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

        # verify that the published objects were of the correct QMF version
        if self._broker_is_v1:
            assert handler.v1_oids and handler.v2_oids == 0, "QMFv2 updates received while in V1-only mode!"
            assert handler.v1_events and handler.v2_events == 0, "QMFv2 events received while in V1-only mode!"
        else:
            assert handler.v2_oids and handler.v1_oids == 0, "QMFv1 updates received while in V2-only mode!"
            assert handler.v2_events and handler.v1_events == 0, "QMFv1 events received while in V2-only mode!"

    def _test_async_method(self):
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
        self._myStartQmf(self.broker, handler)
        broker = self.qmf_session.getObjects(_class="broker")[0]
        handler.request(broker, 20)
        sleep(1)
        self.assertEqual(handler.check(), "pass")

    def test_method_call(self):
        self._startBroker()
        self._test_method_call()

    def test_unsolicited_updates(self):
        self._startBroker()
        self._test_unsolicited_updates()

    def test_async_method(self):
        self._startBroker()
        self._test_async_method()

    # For now, include "QMFv1 only" tests.  Once QMFv1 is deprecated, these can
    # be removed

    def test_method_call_v1(self):
        self._startBroker(QMFv1=True)
        self._test_method_call()

    def test_unsolicited_updates_v1(self):
        self._startBroker(QMFv1=True)
        self._test_unsolicited_updates()

    def test_async_method_v1(self):
        self._startBroker(QMFv1=True)
        self._test_async_method()



if __name__ == "__main__":
    shutil.rmtree("brokertest.tmp", True)
    os.execvp("qpid-python-test",
              ["qpid-python-test", "-m", "qpidd_qmfv2_tests"] + sys.argv[1:])

