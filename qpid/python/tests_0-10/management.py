#
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

from qpid.datatypes import Message, RangedSet
from qpid.testlib import TestBase010
from qpid.management import managementChannel, managementClient

class ManagementTest (TestBase010):
    """
    Tests for the management hooks
    """

    def test_broker_connectivity_oldAPI (self):
        """
        Call the "echo" method on the broker to verify it is alive and talking.
        """
        session = self.session
 
        mc  = managementClient (session.spec)
        mch = mc.addChannel (session)

        mc.syncWaitForStable (mch)
        brokers = mc.syncGetObjects (mch, "broker")
        self.assertEqual (len (brokers), 1)
        broker = brokers[0]
        args = {}
        body = "Echo Message Body"
        args["body"] = body

        for seq in range (1, 5):
            args["sequence"] = seq
            res = mc.syncCallMethod (mch, broker.id, broker.classKey, "echo", args)
            self.assertEqual (res.status,     0)
            self.assertEqual (res.statusText, "OK")
            self.assertEqual (res.sequence,   seq)
            self.assertEqual (res.body,       body)
        mc.removeChannel (mch)

    def test_broker_connectivity (self):
        """
        Call the "echo" method on the broker to verify it is alive and talking.
        """
        session = self.session
        self.startQmf()
 
        brokers = self.qmf.getObjects(_class="broker")
        self.assertEqual (len(brokers), 1)
        broker = brokers[0]

        body = "Echo Message Body"
        for seq in range (1, 10):
            res = broker.echo(seq, body)
            self.assertEqual (res.status,   0)
            self.assertEqual (res.text,     "OK")
            self.assertEqual (res.sequence, seq)
            self.assertEqual (res.body,     body)

    def test_get_objects(self):
        self.startQmf()

        # get the package list, verify that the qpid broker package is there
        packages = self.qmf.getPackages()
        assert 'org.apache.qpid.broker' in packages

        # get the schema class keys for the broker, verify the broker table and link-down event
        keys = self.qmf.getClasses('org.apache.qpid.broker')
        broker = None
        linkDown = None
        for key in keys:
            if key.getClassName() == "broker":  broker = key
            if key.getClassName() == "brokerLinkDown" : linkDown = key
        assert broker
        assert linkDown

        brokerObjs = self.qmf.getObjects(_class="broker")
        assert len(brokerObjs) == 1
        brokerObjs = self.qmf.getObjects(_key=broker)
        assert len(brokerObjs) == 1

    def test_self_session_id (self):
        self.startQmf()
        sessionId = self.qmf_broker.getSessionId()
        brokerSessions = self.qmf.getObjects(_class="session")

        found = False
        for bs in brokerSessions:
            if bs.name == sessionId:
                found = True
        self.assertEqual (found, True)

    def test_standard_exchanges (self):
        self.startQmf()

        exchanges = self.qmf.getObjects(_class="exchange")
        exchange = self.findExchange (exchanges, "")
        self.assertEqual (exchange.type, "direct")
        exchange = self.findExchange (exchanges, "amq.direct")
        self.assertEqual (exchange.type, "direct")
        exchange = self.findExchange (exchanges, "amq.topic")
        self.assertEqual (exchange.type, "topic")
        exchange = self.findExchange (exchanges, "amq.fanout")
        self.assertEqual (exchange.type, "fanout")
        exchange = self.findExchange (exchanges, "amq.match")
        self.assertEqual (exchange.type, "headers")
        exchange = self.findExchange (exchanges, "qpid.management")
        self.assertEqual (exchange.type, "topic")

    def findExchange (self, exchanges, name):
        for exchange in exchanges:
            if exchange.name == name:
                return exchange
        return None

    def test_move_queued_messages(self):
        """
        Test ability to move messages from the head of one queue to another.
        Need to test moveing all and N messages.
        """
        self.startQmf()
        session = self.session
        "Set up source queue"
        session.queue_declare(queue="src-queue", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="src-queue", exchange="amq.direct", binding_key="routing_key")

        twenty = range(1,21)
        props = session.delivery_properties(routing_key="routing_key")
        for count in twenty:
            body = "Move Message %d" % count
            src_msg = Message(props, body)
            session.message_transfer(destination="amq.direct", message=src_msg)

        "Set up destination queue"
        session.queue_declare(queue="dest-queue", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="dest-queue", exchange="amq.direct")

        queues = self.qmf.getObjects(_class="queue")

        "Move 10 messages from src-queue to dest-queue"
        result = self.qmf.getObjects(_class="broker")[0].queueMoveMessages("src-queue", "dest-queue", 10)
        self.assertEqual (result.status, 0) 

        sq = self.qmf.getObjects(_class="queue", name="src-queue")[0]
        dq = self.qmf.getObjects(_class="queue", name="dest-queue")[0]

        self.assertEqual (sq.msgDepth,10)
        self.assertEqual (dq.msgDepth,10)

        "Move all remaining messages to destination"
        result = self.qmf.getObjects(_class="broker")[0].queueMoveMessages("src-queue", "dest-queue", 0)
        self.assertEqual (result.status,0)

        sq = self.qmf.getObjects(_class="queue", name="src-queue")[0]
        dq = self.qmf.getObjects(_class="queue", name="dest-queue")[0]

        self.assertEqual (sq.msgDepth,0)
        self.assertEqual (dq.msgDepth,20)

        "Use a bad source queue name"
        result = self.qmf.getObjects(_class="broker")[0].queueMoveMessages("bad-src-queue", "dest-queue", 0)
        self.assertEqual (result.status,4)

        "Use a bad destination queue name"
        result = self.qmf.getObjects(_class="broker")[0].queueMoveMessages("src-queue", "bad-dest-queue", 0)
        self.assertEqual (result.status,4)

        " Use a large qty (40) to move from dest-queue back to "
        " src-queue- should move all "
        result = self.qmf.getObjects(_class="broker")[0].queueMoveMessages("dest-queue", "src-queue", 40)
        self.assertEqual (result.status,0)

        sq = self.qmf.getObjects(_class="queue", name="src-queue")[0]
        dq = self.qmf.getObjects(_class="queue", name="dest-queue")[0]

        self.assertEqual (sq.msgDepth,20)
        self.assertEqual (dq.msgDepth,0)

        "Consume the messages of the queue and check they are all there in order"
        session.message_subscribe(queue="src-queue", destination="tag")
        session.message_flow(destination="tag", unit=session.credit_unit.message, value=0xFFFFFFFF)
        session.message_flow(destination="tag", unit=session.credit_unit.byte, value=0xFFFFFFFF)
        queue = session.incoming("tag")
        for count in twenty:
            consumed_msg = queue.get(timeout=1)
            body = "Move Message %d" % count
            self.assertEqual(body, consumed_msg.body)

    def test_purge_queue(self):
        """
        Test ability to purge messages from the head of a queue.
        Need to test moveing all, 1 (top message) and N messages.
        """
        self.startQmf()
        session = self.session
        "Set up purge queue"
        session.queue_declare(queue="purge-queue", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="purge-queue", exchange="amq.direct", binding_key="routing_key")

        twenty = range(1,21)
        props = session.delivery_properties(routing_key="routing_key")
        for count in twenty:
            body = "Purge Message %d" % count
            msg = Message(props, body)
            session.message_transfer(destination="amq.direct", message=msg)

        pq = self.qmf.getObjects(_class="queue", name="purge-queue")[0]

        "Purge top message from purge-queue"
        result = pq.purge(1)
        self.assertEqual (result.status, 0) 
        pq = self.qmf.getObjects(_class="queue", name="purge-queue")[0]
        self.assertEqual (pq.msgDepth,19)

        "Purge top 9 messages from purge-queue"
        result = pq.purge(9)
        self.assertEqual (result.status, 0) 
        pq = self.qmf.getObjects(_class="queue", name="purge-queue")[0]
        self.assertEqual (pq.msgDepth,10)

        "Purge all messages from purge-queue"
        result = pq.purge(0)
        self.assertEqual (result.status, 0) 
        pq = self.qmf.getObjects(_class="queue", name="purge-queue")[0]
        self.assertEqual (pq.msgDepth,0)

