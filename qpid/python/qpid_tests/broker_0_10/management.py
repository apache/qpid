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
from threading import Condition
from time import sleep
import qmf.console
import qpid.messaging
from qpidtoollibs import BrokerAgent

class ManagementTest (TestBase010):

    def setup_access(self):
        if 'broker_agent' not in self.__dict__:
            self.conn2 = qpid.messaging.Connection(self.broker)
            self.conn2.open()
            self.broker_agent = BrokerAgent(self.conn2)
        return self.broker_agent

    """
    Tests for the management hooks
    """

    def test_broker_connectivity_oldAPI (self):
        """
        Call the "echo" method on the broker to verify it is alive and talking.
        """
        session = self.session
 
        mc  = managementClient ()
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

    def test_methods_sync (self):
        """
        Call the "echo" method on the broker to verify it is alive and talking.
        """
        session = self.session
        self.startQmf()
 
        brokers = self.qmf.getObjects(_class="broker")
        self.assertEqual(len(brokers), 1)
        broker = brokers[0]

        body = "Echo Message Body"
        for seq in range(1, 20):
            res = broker.echo(seq, body)
            self.assertEqual(res.status, 0)
            self.assertEqual(res.text, "OK")
            self.assertEqual(res.sequence, seq)
            self.assertEqual(res.body, body)

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
            if bs.name.endswith(sessionId):
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

    def test_move_queued_messages_empty(self):
        """
        Test that moving messages from an empty queue does not cause an error.
        """
        self.startQmf()
        session = self.session
        "Set up source queue"
        session.queue_declare(queue="src-queue-empty", exclusive=True, auto_delete=True)

        "Set up destination queue"
        session.queue_declare(queue="dest-queue-empty", exclusive=True, auto_delete=True)

        queues = self.qmf.getObjects(_class="queue")

        "Move all messages from src-queue-empty to dest-queue-empty"
        result = self.qmf.getObjects(_class="broker")[0].queueMoveMessages("src-queue-empty", "dest-queue-empty", 0, {})
        self.assertEqual (result.status, 0) 

        sq = self.qmf.getObjects(_class="queue", name="src-queue-empty")[0]
        dq = self.qmf.getObjects(_class="queue", name="dest-queue-empty")[0]

        self.assertEqual (sq.msgDepth,0)
        self.assertEqual (dq.msgDepth,0)

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
        result = self.qmf.getObjects(_class="broker")[0].queueMoveMessages("src-queue", "dest-queue", 10, {})
        self.assertEqual (result.status, 0) 

        sq = self.qmf.getObjects(_class="queue", name="src-queue")[0]
        dq = self.qmf.getObjects(_class="queue", name="dest-queue")[0]

        self.assertEqual (sq.msgDepth,10)
        self.assertEqual (dq.msgDepth,10)

        "Move all remaining messages to destination"
        result = self.qmf.getObjects(_class="broker")[0].queueMoveMessages("src-queue", "dest-queue", 0, {})
        self.assertEqual (result.status,0)

        sq = self.qmf.getObjects(_class="queue", name="src-queue")[0]
        dq = self.qmf.getObjects(_class="queue", name="dest-queue")[0]

        self.assertEqual (sq.msgDepth,0)
        self.assertEqual (dq.msgDepth,20)

        "Use a bad source queue name"
        result = self.qmf.getObjects(_class="broker")[0].queueMoveMessages("bad-src-queue", "dest-queue", 0, {})
        self.assertEqual (result.status,4)

        "Use a bad destination queue name"
        result = self.qmf.getObjects(_class="broker")[0].queueMoveMessages("src-queue", "bad-dest-queue", 0, {})
        self.assertEqual (result.status,4)

        " Use a large qty (40) to move from dest-queue back to "
        " src-queue- should move all "
        result = self.qmf.getObjects(_class="broker")[0].queueMoveMessages("dest-queue", "src-queue", 40, {})
        self.assertEqual (result.status,0)

        sq = self.qmf.getObjects(_class="queue", name="src-queue")[0]
        dq = self.qmf.getObjects(_class="queue", name="dest-queue")[0]

        self.assertEqual (sq.msgDepth,20)
        self.assertEqual (dq.msgDepth,0)

        "Consume the messages of the queue and check they are all there in order"
        session.message_subscribe(queue="src-queue", destination="tag")
        session.message_flow(destination="tag", unit=session.credit_unit.message, value=0xFFFFFFFFL)
        session.message_flow(destination="tag", unit=session.credit_unit.byte, value=0xFFFFFFFFL)
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
        result = pq.purge(1, {})
        self.assertEqual (result.status, 0) 
        pq = self.qmf.getObjects(_class="queue", name="purge-queue")[0]
        self.assertEqual (pq.msgDepth,19)

        "Purge top 9 messages from purge-queue"
        result = pq.purge(9, {})
        self.assertEqual (result.status, 0) 
        pq = self.qmf.getObjects(_class="queue", name="purge-queue")[0]
        self.assertEqual (pq.msgDepth,10)

        "Purge all messages from purge-queue"
        result = pq.purge(0, {})
        self.assertEqual (result.status, 0) 
        pq = self.qmf.getObjects(_class="queue", name="purge-queue")[0]
        self.assertEqual (pq.msgDepth,0)

    def test_reroute_priority_queue(self):
        self.startQmf()
        session = self.session

        #setup test queue supporting multiple priority levels
        session.queue_declare(queue="test-queue", exclusive=True, auto_delete=True, arguments={'x-qpid-priorities':10})

        #send some messages of varying priority to that queue:
        for i in range(0, 5):
            deliveryProps = session.delivery_properties(routing_key="test-queue", priority=i+5)
            session.message_transfer(message=Message(deliveryProps, "Message %d" % (i+1)))


        #declare and bind a queue to amq.fanout through which rerouted
        #messages can be verified:
        session.queue_declare(queue="rerouted", exclusive=True, auto_delete=True, arguments={'x-qpid-priorities':10})
        session.exchange_bind(queue="rerouted", exchange="amq.fanout")

        #reroute messages from test queue to amq.fanout (and hence to
        #rerouted queue):
        pq = self.qmf.getObjects(_class="queue", name="test-queue")[0]
        result = pq.reroute(0, False, "amq.fanout", {})
        self.assertEqual(result.status, 0) 

        #verify messages are all rerouted:
        self.subscribe(destination="incoming", queue="rerouted")
        incoming = session.incoming("incoming")
        for i in range(0, 5):
            msg = incoming.get(timeout=1)
            self.assertEqual("Message %d" % (5-i), msg.body)


    def test_reroute_queue(self):
        """
        Test ability to reroute messages from the head of a queue.
        Need to test moving all, 1 (top message) and N messages.
        """
        self.startQmf()
        session = self.session
        "Set up test queue"
        session.exchange_declare(exchange="alt.direct1", type="direct")
        session.queue_declare(queue="alt-queue1", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="alt-queue1", exchange="alt.direct1", binding_key="routing_key")
        session.exchange_declare(exchange="alt.direct2", type="direct")
        session.queue_declare(queue="alt-queue2", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="alt-queue2", exchange="alt.direct2", binding_key="routing_key")
        session.queue_declare(queue="reroute-queue", exclusive=True, auto_delete=True, alternate_exchange="alt.direct1")
        session.exchange_bind(queue="reroute-queue", exchange="amq.direct", binding_key="routing_key")

        twenty = range(1,21)
        props = session.delivery_properties(routing_key="routing_key")
        mp    = session.message_properties(application_headers={'x-qpid.trace' : 'A,B,C'})
        for count in twenty:
            body = "Reroute Message %d" % count
            msg = Message(props, mp, body)
            session.message_transfer(destination="amq.direct", message=msg)

        pq = self.qmf.getObjects(_class="queue", name="reroute-queue")[0]

        "Reroute top message from reroute-queue to alternate exchange"
        result = pq.reroute(1, True, "", {})
        self.assertEqual(result.status, 0) 
        pq.update()
        aq = self.qmf.getObjects(_class="queue", name="alt-queue1")[0]
        self.assertEqual(pq.msgDepth,19)
        self.assertEqual(aq.msgDepth,1)

        "Verify that the trace was cleared on the rerouted message"
        url = "%s://%s:%d" % (self.broker.scheme or "amqp", self.broker.host, self.broker.port)
        conn = qpid.messaging.Connection(url)
        conn.open()
        sess = conn.session()
        rx = sess.receiver("alt-queue1;{mode:browse}")
        rm = rx.fetch(1)
        self.assertEqual(rm.properties['x-qpid.trace'], '')
        conn.close()

        "Reroute top 9 messages from reroute-queue to alt.direct2"
        result = pq.reroute(9, False, "alt.direct2", {})
        self.assertEqual(result.status, 0) 
        pq.update()
        aq = self.qmf.getObjects(_class="queue", name="alt-queue2")[0]
        self.assertEqual(pq.msgDepth,10)
        self.assertEqual(aq.msgDepth,9)

        "Reroute using a non-existent exchange"
        result = pq.reroute(0, False, "amq.nosuchexchange", {})
        self.assertEqual(result.status, 4)

        "Reroute all messages from reroute-queue"
        result = pq.reroute(0, False, "alt.direct2", {})
        self.assertEqual(result.status, 0) 
        pq.update()
        aq = self.qmf.getObjects(_class="queue", name="alt-queue2")[0]
        self.assertEqual(pq.msgDepth,0)
        self.assertEqual(aq.msgDepth,19)

        "Make more messages"
        twenty = range(1,21)
        props = session.delivery_properties(routing_key="routing_key")
        for count in twenty:
            body = "Reroute Message %d" % count
            msg = Message(props, body)
            session.message_transfer(destination="amq.direct", message=msg)

        "Reroute onto the same queue"
        result = pq.reroute(0, False, "amq.direct", {})
        self.assertEqual(result.status, 0) 
        pq.update()
        self.assertEqual(pq.msgDepth,20)

    def test_reroute_alternate_exchange(self):
        """
        Test that when rerouting, the alternate-exchange is considered if relevant
        """
        self.startQmf()
        session = self.session
        # 1. Create 2 exchanges A and B (fanout) where B is the
        # alternate exchange for A
        session.exchange_declare(exchange="B", type="fanout")
        session.exchange_declare(exchange="A", type="fanout", alternate_exchange="B")

        # 2. Bind queue X to B
        session.queue_declare(queue="X", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="X", exchange="B")

        # 3. Send 1 message to queue Y
        session.queue_declare(queue="Y", exclusive=True, auto_delete=True)
        props = session.delivery_properties(routing_key="Y")
        session.message_transfer(message=Message(props, "reroute me!"))

        # 4. Call reroute on queue Y and specify that messages should
        # be sent to exchange A
        y = self.qmf.getObjects(_class="queue", name="Y")[0]
        result = y.reroute(1, False, "A", {})
        self.assertEqual(result.status, 0)

        # 5. verify that the message is rerouted through B (as A has
        # no matching bindings) to X
        self.subscribe(destination="x", queue="X")
        self.assertEqual("reroute me!", session.incoming("x").get(timeout=1).body)

        # Cleanup
        for e in ["A", "B"]: session.exchange_delete(exchange=e)

    def test_reroute_invalid_alt_exchange(self):
        """
        Test that an error is returned for an attempt to reroute to
        alternate exchange on a queue for which no such exchange has
        been defined.
        """
        self.startQmf()
        session = self.session
        # create queue with no alt-exchange, and send a message to it
        session.queue_declare(queue="q", exclusive=True, auto_delete=True)
        props = session.delivery_properties(routing_key="q")
        session.message_transfer(message=Message(props, "don't reroute me!"))

        # attempt to reroute the message to alt-exchange
        q = self.qmf.getObjects(_class="queue", name="q")[0]
        result = q.reroute(1, True, "", {})
        # verify the attempt fails...
        self.assertEqual(result.status, 4) #invalid parameter

        # ...and message is still on the queue
        self.subscribe(destination="d", queue="q")
        self.assertEqual("don't reroute me!", session.incoming("d").get(timeout=1).body)


    def test_methods_async (self):
        """
        """
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
        self.startQmf(handler)
        brokers = self.qmf.getObjects(_class="broker")
        self.assertEqual(len(brokers), 1)
        broker = brokers[0]
        handler.request(broker, 20)
        sleep(1)
        self.assertEqual(handler.check(), "pass")

    def test_connection_close(self):
        """
        Test management method for closing connection
        """
        self.startQmf()
        conn = self.connect()
        session = conn.session("my-named-session")

        #using qmf find named session and close the corresponding connection:
        qmf_ssn_object = [s for s in self.qmf.getObjects(_class="session") if s.name.endswith("my-named-session")][0]
        qmf_ssn_object._connectionRef_.close()

        #check that connection is closed
        try:
            conn.session("another-session")
            self.fail("Expected failure from closed connection")
        except: None

        #make sure that the named session has been closed and the name can be re-used
        conn = self.connect()
        session = conn.session("my-named-session")
        session.queue_declare(queue="whatever", exclusive=True, auto_delete=True)

    def test_immediate_method(self):
        url = "%s://%s:%d" % (self.broker.scheme or "amqp", self.broker.host or "localhost", self.broker.port or 5672)
        conn = qpid.messaging.Connection(url)
        conn.open()
        sess = conn.session()
        replyTo = "qmf.default.direct/reply_immediate_method_test;{node:{type:topic}}"
        agent_sender   = sess.sender("qmf.default.direct/broker")
        agent_receiver = sess.receiver(replyTo)
        queue_create = sess.sender("test-queue-imm-method;{create:always,delete:always,node:{type:queue,durable:False,x-declare:{auto-delete:True}}}")

        method_request = {'_method_name':'reroute','_object_id':{'_object_name':'org.apache.qpid.broker:queue:test-queue-imm-method'}}
        method_request['_arguments'] = {'request':0, 'useAltExchange':False, 'exchange':'amq.fanout'}

        reroute_call = qpid.messaging.Message(method_request)
        reroute_call.properties['qmf.opcode'] = '_method_request'
        reroute_call.properties['x-amqp-0-10.app-id'] = 'qmf2'
        reroute_call.reply_to = replyTo

        agent_sender.send(reroute_call)
        result = agent_receiver.fetch(3)
        self.assertEqual(result.properties['qmf.opcode'], '_method_response')

        conn.close()

    def test_binding_count_on_queue(self):
        self.startQmf()
        conn = self.connect()
        session = self.session

        QUEUE = "binding_test_queue"
        EX_DIR = "binding_test_exchange_direct"
        EX_FAN = "binding_test_exchange_fanout"
        EX_TOPIC = "binding_test_exchange_topic"
        EX_HDR = "binding_test_exchange_headers"

        #
        # Create a test queue
        #
        session.queue_declare(queue=QUEUE, exclusive=True, auto_delete=True)
        queue = self.qmf.getObjects(_class="queue", name=QUEUE)[0]
        if not queue:
            self.fail("Queue not found")
        self.assertEqual(queue.bindingCount, 1, "wrong initial binding count")

        #
        # Create an exchange of each supported type
        #
        session.exchange_declare(exchange=EX_DIR, type="direct")
        session.exchange_declare(exchange=EX_FAN, type="fanout")
        session.exchange_declare(exchange=EX_TOPIC, type="topic")
        session.exchange_declare(exchange=EX_HDR, type="headers")

        #
        # Bind each exchange to the test queue
        #
        match = {}
        match['x-match'] = "all"
        match['key'] = "value"
        session.exchange_bind(exchange=EX_DIR, queue=QUEUE, binding_key="key1")
        session.exchange_bind(exchange=EX_DIR, queue=QUEUE, binding_key="key2")
        session.exchange_bind(exchange=EX_FAN, queue=QUEUE)
        session.exchange_bind(exchange=EX_TOPIC, queue=QUEUE, binding_key="key1.#")
        session.exchange_bind(exchange=EX_TOPIC, queue=QUEUE, binding_key="key2.#")
        session.exchange_bind(exchange=EX_HDR, queue=QUEUE, binding_key="key1", arguments=match)
        match['key2'] = "value2"
        session.exchange_bind(exchange=EX_HDR, queue=QUEUE, binding_key="key2", arguments=match)

        #
        # Verify that the queue's binding count accounts for the new bindings
        #
        queue.update()
        self.assertEqual(queue.bindingCount, 8,
                         "added bindings not accounted for (expected 8, got %d)" % queue.bindingCount)

        #
        # Remove some of the bindings
        #
        session.exchange_unbind(exchange=EX_DIR, queue=QUEUE, binding_key="key2")
        session.exchange_unbind(exchange=EX_TOPIC, queue=QUEUE, binding_key="key2.#")
        session.exchange_unbind(exchange=EX_HDR, queue=QUEUE, binding_key="key2")

        #
        # Verify that the queue's binding count accounts for the deleted bindings
        #
        queue.update()
        self.assertEqual(queue.bindingCount, 5,
                         "deleted bindings not accounted for (expected 5, got %d)" % queue.bindingCount)
        #
        # Delete the exchanges
        #
        session.exchange_delete(exchange=EX_DIR)
        session.exchange_delete(exchange=EX_FAN)
        session.exchange_delete(exchange=EX_TOPIC)
        session.exchange_delete(exchange=EX_HDR)

        #
        # Verify that the queue's binding count accounts for the lost bindings
        #
        queue.update()
        self.assertEqual(queue.bindingCount, 1,
                         "deleted bindings not accounted for (expected 1, got %d)" % queue.bindingCount)

    def test_connection_stats(self):
        """
        Test message in/out stats for connection
        """
        agent = self.setup_access()
        conn = self.connect()
        session = conn.session("stats-session")

        #using qmf find named session and the corresponding connection:
        conn_qmf = None
        sessions = agent.getAllSessions()
        for s in sessions:
            if s.name.endswith("stats-session"):
                conn_qmf = agent.getConnection(s.connectionRef)

        assert(conn_qmf)
        
        #send a message to a queue
        session.queue_declare(queue="stats-q", exclusive=True, auto_delete=True)
        session.message_transfer(message=Message(session.delivery_properties(routing_key="stats-q"), "abc"))
        
        #check the 'msgs sent from' stat for this connection
        conn_qmf.update()
        self.assertEqual(conn_qmf.msgsFromClient, 1)

        #receive message from queue
        session.message_subscribe(destination="d", queue="stats-q")
        incoming = session.incoming("d")
        incoming.start()
        self.assertEqual("abc", incoming.get(timeout=1).body)

        #check the 'msgs sent to' stat for this connection
        conn_qmf.update()
        self.assertEqual(conn_qmf.msgsToClient, 1)

    def test_timestamp_config(self):
        """
        Test message timestamping control.
        """
        self.startQmf()
        conn = self.connect()
        session = conn.session("timestamp-session")

        #verify that receive message timestamping is OFF by default
        broker = self.qmf.getObjects(_class="broker")[0]
        rc = broker.getTimestampConfig()
        self.assertEqual(rc.status, 0)
        self.assertEqual(rc.text, "OK")

        #try to enable it
        rc = broker.setTimestampConfig(True)
        self.assertEqual(rc.status, 0)
        self.assertEqual(rc.text, "OK")

        rc = broker.getTimestampConfig()
        self.assertEqual(rc.status, 0)
        self.assertEqual(rc.text, "OK")
        self.assertEqual(rc.receive, True)

        # setup a connection & session to the broker
        url = "%s://%s:%d" % (self.broker.scheme or "amqp", self.broker.host or "localhost", self.broker.port or 5672)
        conn = qpid.messaging.Connection(url)
        conn.open()
        sess = conn.session()

        #send a message to a queue
        sender = sess.sender("ts-q; {create:sender, delete:receiver}")
        sender.send( qpid.messaging.Message(content="abc") )

        #receive message from queue, and verify timestamp is present
        receiver = sess.receiver("ts-q")
        try:
            msg = receiver.fetch(timeout=1)
        except Empty:
            assert(False)
        self.assertEqual("abc", msg.content)
        self.assertEqual(True, "x-amqp-0-10.timestamp" in msg.properties)
        assert(msg.properties["x-amqp-0-10.timestamp"])

        #try to disable it
        rc = broker.setTimestampConfig(False)
        self.assertEqual(rc.status, 0)
        self.assertEqual(rc.text, "OK")

        rc = broker.getTimestampConfig()
        self.assertEqual(rc.status, 0)
        self.assertEqual(rc.text, "OK")
        self.assertEqual(rc.receive, False)

        #send another message to the queue
        sender.send( qpid.messaging.Message(content="def") )

        #receive message from queue, and verify timestamp is NOT PRESENT
        receiver = sess.receiver("ts-q")
        try:
            msg = receiver.fetch(timeout=1)
        except Empty:
            assert(False)
        self.assertEqual("def", msg.content)
        self.assertEqual(False, "x-amqp-0-10.timestamp" in msg.properties)

