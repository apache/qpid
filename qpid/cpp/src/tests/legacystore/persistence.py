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

import sys, re, traceback, socket
from getopt import getopt, GetoptError

from qpid.connection import Connection
from qpid.util import connect
from qpid.datatypes import Message, RangedSet
from qpid.queue import Empty
from qpid.session import SessionException
from qpid.testlib import TestBase010
from time import sleep

class PersistenceTest(TestBase010):

    XA_RBROLLBACK = 1
    XA_RBTIMEOUT = 2
    XA_OK = 0

    def createMessage(self, **kwargs):
        session = self.session
        dp = {}
        dp['delivery_mode'] = 2
        mp = {}
        for k, v in kwargs.iteritems():
            if k in ['routing_key', 'delivery_mode']: dp[k] = v
            if k in ['message_id', 'correlation_id', 'application_headers']: mp[k] = v
        args = []
        args.append(session.delivery_properties(**dp))
        if len(mp):
            args.append(session.message_properties(**mp))
        if kwargs.has_key('body'): args.append(kwargs['body'])
        return Message(*args)

    def phase1(self):
        session = self.session

        session.queue_declare(queue="queue-a", durable=True)
        session.queue_declare(queue="queue-b", durable=True)
        session.exchange_bind(queue="queue-a", exchange="amq.direct", binding_key="a")
        session.exchange_bind(queue="queue-b", exchange="amq.direct", binding_key="b")

        session.message_transfer(destination="amq.direct",
                                 message=self.createMessage(routing_key="a", correlation_id="Msg0001", body="A_Message1"))
        session.message_transfer(destination="amq.direct",
                                 message=self.createMessage(routing_key="b", correlation_id="Msg0002", body="B_Message1"))

#        session.queue_declare(queue="lvq-test", durable=True, arguments={"qpid.last_value_queue":True})
#        session.message_transfer(message=self.createMessage(routing_key="lvq-test", application_headers={"qpid.LVQ_key":"B"}, body="B1"))
#        session.message_transfer(message=self.createMessage(routing_key="lvq-test", application_headers={"qpid.LVQ_key":"A"}, body="A1"))
#        session.message_transfer(message=self.createMessage(routing_key="lvq-test", application_headers={"qpid.LVQ_key":"A"}, body="A2"))
#        session.message_transfer(message=self.createMessage(routing_key="lvq-test", application_headers={"qpid.LVQ_key":"B"}, body="B2"))
#        session.message_transfer(message=self.createMessage(routing_key="lvq-test", application_headers={"qpid.LVQ_key":"B"}, body="B3"))
#        session.message_transfer(message=self.createMessage(routing_key="lvq-test", application_headers={"qpid.LVQ_key":"C"}, body="C1"))



    def phase2(self):
        session = self.session

        #check queues exists
        session.queue_declare(queue="queue-a", durable=True, passive=True)
        session.queue_declare(queue="queue-b", durable=True, passive=True)

        #check they are still bound to amq.direct correctly
        responses = []
        responses.append(session.exchange_bound(queue="queue-a", exchange="amq.direct", binding_key="a"))
        responses.append(session.exchange_bound(queue="queue-b", exchange="amq.direct", binding_key="b"))
        for r in responses:
            self.assert_(not r.exchange_not_found)
            self.assert_(not r.queue_not_found)
            self.assert_(not r.key_not_matched)


        #check expected messages are there
        self.assertMessageOnQueue("queue-a", "Msg0001", "A_Message1")
        self.assertMessageOnQueue("queue-b", "Msg0002", "B_Message1")

        self.assertEmptyQueue("queue-a")
        self.assertEmptyQueue("queue-b")

        session.queue_declare(queue="queue-c", durable=True)

        #send a message to a topic such that it reaches all queues
        session.exchange_bind(queue="queue-a", exchange="amq.topic", binding_key="abc")
        session.exchange_bind(queue="queue-b", exchange="amq.topic", binding_key="abc")
        session.exchange_bind(queue="queue-c", exchange="amq.topic", binding_key="abc")

        session.message_transfer(destination="amq.topic",
                                 message=self.createMessage(routing_key="abc", correlation_id="Msg0003", body="AB_Message2"))

#        #check LVQ exists and has exepected messages:
#        session.queue_declare(queue="lvq-test", durable=True, passive=True)
#        session.message_subscribe(destination="lvq", queue="lvq-test")
#        lvq = session.incoming("lvq")
#        lvq.start()
#        accepted = RangedSet()
#        for m in ["A2", "B3", "C1"]:
#            msg = lvq.get(timeout=1)
#            self.assertEquals(m, msg.body)
#            accepted.add(msg.id)
#        try:
#            extra = lvq.get(timeout=1)
#            self.fail("lvq-test not empty, contains: " + extra.body)
#        except Empty: None
#        #publish some more messages while subscriber is active (no replacement):
#        session.message_transfer(message=self.createMessage(routing_key="lvq-test", application_headers={"qpid.LVQ_key":"C"}, body="C2"))
#        session.message_transfer(message=self.createMessage(routing_key="lvq-test", application_headers={"qpid.LVQ_key":"C"}, body="C3"))
#        session.message_transfer(message=self.createMessage(routing_key="lvq-test", application_headers={"qpid.LVQ_key":"A"}, body="A3"))
#        session.message_transfer(message=self.createMessage(routing_key="lvq-test", application_headers={"qpid.LVQ_key":"A"}, body="A4"))
#        session.message_transfer(message=self.createMessage(routing_key="lvq-test", application_headers={"qpid.LVQ_key":"C"}, body="C4"))
#        #check that accepting replaced messages is safe
#        session.message_accept(accepted)


    def phase3(self):
        session = self.session

#        #lvq recovery validation
#        session.queue_declare(queue="lvq-test", durable=True, passive=True)
#        session.message_subscribe(destination="lvq", queue="lvq-test")
#        lvq = session.incoming("lvq")
#        lvq.start()
#        accepted = RangedSet()
#        lvq.start()
#        for m in ["C4", "A4"]:
#            msg = lvq.get(timeout=1)
#            self.assertEquals(m, msg.body)
#            accepted.add(msg.id)
#        session.message_accept(accepted)
#        try:
#            extra = lvq.get(timeout=1)
#            self.fail("lvq-test not empty, contains: " + extra.body)
#        except Empty: None
#        session.message_cancel(destination="lvq")
#        session.queue_delete(queue="lvq-test")


        #check queues exists
        session.queue_declare(queue="queue-a", durable=True, passive=True)
        session.queue_declare(queue="queue-b", durable=True, passive=True)
        session.queue_declare(queue="queue-c", durable=True, passive=True)

        session.tx_select()
        #check expected messages are there
        self.assertMessageOnQueue("queue-a", "Msg0003", "AB_Message2")
        self.assertMessageOnQueue("queue-b", "Msg0003", "AB_Message2")
        self.assertMessageOnQueue("queue-c", "Msg0003", "AB_Message2")

        self.assertEmptyQueue("queue-a")
        self.assertEmptyQueue("queue-b")
        self.assertEmptyQueue("queue-c")

        #note: default bindings must be restored for this to work
        session.message_transfer(message=self.createMessage(
            routing_key="queue-a", correlation_id="Msg0004", body="A_Message3"))
        session.message_transfer(message=self.createMessage(
            routing_key="queue-a", correlation_id="Msg0005", body="A_Message4"))
        session.message_transfer(message=self.createMessage(
            routing_key="queue-a", correlation_id="Msg0006", body="A_Message5"))

        session.tx_commit()


        #delete a queue
        session.queue_delete(queue="queue-c")

        session.message_subscribe(destination="ctag", queue="queue-a", accept_mode=0)
        session.message_flow(destination="ctag", unit=0, value=0xFFFFFFFF)
        session.message_flow(destination="ctag", unit=1, value=0xFFFFFFFF)
        included = session.incoming("ctag")
        msg1 = included.get(timeout=1)
        self.assertExpectedContent(msg1, "Msg0004", "A_Message3")
        msg2 = included.get(timeout=1)
        self.assertExpectedContent(msg2, "Msg0005", "A_Message4")
        msg3 = included.get(timeout=1)
        self.assertExpectedContent(msg3, "Msg0006", "A_Message5")
        self.ack(msg1, msg2, msg3)

        session.message_transfer(destination="amq.direct", message=self.createMessage(
            routing_key="queue-b", correlation_id="Msg0007", body="B_Message3"))

        session.tx_rollback()


    def phase4(self):
        session = self.session

        #check queues exists
        session.queue_declare(queue="queue-a", durable=True, passive=True)
        session.queue_declare(queue="queue-b", durable=True, passive=True)

        self.assertMessageOnQueue("queue-a", "Msg0004", "A_Message3")
        self.assertMessageOnQueue("queue-a", "Msg0005", "A_Message4")
        self.assertMessageOnQueue("queue-a", "Msg0006", "A_Message5")

        self.assertEmptyQueue("queue-a")
        self.assertEmptyQueue("queue-b")

        #check this queue doesn't exist
        try:
            session.queue_declare(queue="queue-c", durable=True, passive=True)
            raise Exception("Expected queue-c to have been deleted")
        except SessionException, e:
            self.assertEquals(404, e.args[0].error_code)

    def phase5(self):

        session = self.session
        queues = ["queue-a1", "queue-a2", "queue-b1", "queue-b2", "queue-c1", "queue-c2", "queue-d1", "queue-d2"]

        for q in queues:
            session.queue_declare(queue=q, durable=True)
            session.queue_purge(queue=q)

        session.message_transfer(message=self.createMessage(
            routing_key="queue-a1", correlation_id="MsgA", body="MessageA"))
        session.message_transfer(message=self.createMessage(
            routing_key="queue-b1", correlation_id="MsgB", body="MessageB"))
        session.message_transfer(message=self.createMessage(
            routing_key="queue-c1", correlation_id="MsgC", body="MessageC"))
        session.message_transfer(message=self.createMessage(
            routing_key="queue-d1", correlation_id="MsgD", body="MessageD"))

        session.dtx_select()
        txa = self.xid('a')
        txb = self.xid('b')
        txc = self.xid('c')
        txd = self.xid('d')

        self.txswap("queue-a1", "queue-a2", txa)
        self.txswap("queue-b1", "queue-b2", txb)
        self.txswap("queue-c1", "queue-c2", txc)
        self.txswap("queue-d1", "queue-d2", txd)

        #no queue should have any messages accessible
        for q in queues:
            self.assertEqual(0, session.queue_query(queue=q).message_count, "Bad count for %s" % (q))

        self.assertEqual(self.XA_OK, session.dtx_commit(xid=txa, one_phase=True).status)
        self.assertEqual(self.XA_OK, session.dtx_rollback(xid=txb).status)
        self.assertEqual(self.XA_OK, session.dtx_prepare(xid=txc).status)
        self.assertEqual(self.XA_OK, session.dtx_prepare(xid=txd).status)

        #further checks
        not_empty = ["queue-a2", "queue-b1"]
        for q in queues:
            if q in not_empty:
                self.assertEqual(1, session.queue_query(queue=q).message_count, "Bad count for %s" % (q))
            else:
                self.assertEqual(0, session.queue_query(queue=q).message_count, "Bad count for %s" % (q))


    def phase6(self):
        session = self.session

        #check prepared transaction are reported correctly by recover
        txc = self.xid('c')
        txd = self.xid('d')

        xids = session.dtx_recover().in_doubt
        ids = [x.global_id for x in xids] #TODO: come up with nicer way to test these

        if txc.global_id not in ids:
            self.fail("Recovered xids not as expected. missing: %s" % (txc))
        if txd.global_id not in ids:
            self.fail("Recovered xids not as expected. missing: %s" % (txd))
        self.assertEqual(2, len(xids))


        queues = ["queue-a1", "queue-a2", "queue-b1", "queue-b2", "queue-c1", "queue-c2", "queue-d1", "queue-d2"]
        not_empty = ["queue-a2", "queue-b1"]

        #re-check
        not_empty = ["queue-a2", "queue-b1"]
        for q in queues:
            if q in not_empty:
                self.assertEqual(1, session.queue_query(queue=q).message_count, "Bad count for %s" % (q))
            else:
                self.assertEqual(0, session.queue_query(queue=q).message_count, "Bad count for %s" % (q))

        #complete the prepared transactions
        self.assertEqual(self.XA_OK, session.dtx_commit(xid=txc).status)
        self.assertEqual(self.XA_OK, session.dtx_rollback(xid=txd).status)
        not_empty.append("queue-c2")
        not_empty.append("queue-d1")

        for q in queues:
            if q in not_empty:
                self.assertEqual(1, session.queue_query(queue=q).message_count)
            else:
                self.assertEqual(0, session.queue_query(queue=q).message_count)

    def phase7(self):
        session = self.session
        session.synchronous = False

        # check xids from phase 6 are gone
        txc = self.xid('c')
        txd = self.xid('d')

        xids = session.dtx_recover().in_doubt
        ids = [x.global_id for x in xids] #TODO: come up with nicer way to test these

        if txc.global_id in ids:
            self.fail("Xid still present : %s" % (txc))
        if txd.global_id in ids:
            self.fail("Xid still present : %s" % (txc))
        self.assertEqual(0, len(xids))

        #test deletion of queue after publish
        #create queue
        session.queue_declare(queue = "q", auto_delete=True, durable=True)

        #send message
        for i in range(1, 10):
            session.message_transfer(message=self.createMessage(routing_key = "q", body = "my-message"))

        session.synchronous = True
        #explicitly delete queue
        session.queue_delete(queue = "q")

        #test acking of message from auto-deleted queue
        #create queue
        session.queue_declare(queue = "q", auto_delete=True, durable=True)

        #send message
        session.message_transfer(message=self.createMessage(routing_key = "q", body = "my-message"))

        #create consumer
        session.message_subscribe(queue = "q", destination = "a", accept_mode=0, acquire_mode=0)
        session.message_flow(unit = 1, value = 0xFFFFFFFF, destination = "a")
        session.message_flow(unit = 0, value = 10, destination = "a")
        queue = session.incoming("a")

        #consume the message, cancel subscription (triggering auto-delete), then ack it
        msg = queue.get(timeout = 5)
        session.message_cancel(destination = "a")
        self.ack(msg)

        #test implicit deletion of bindings when queue is deleted
        session.queue_declare(queue = "durable-subscriber-queue", exclusive=True, durable=True)
        session.exchange_bind(exchange="amq.topic", queue="durable-subscriber-queue", binding_key="xyz")
        session.message_transfer(destination= "amq.topic", message=self.createMessage(routing_key = "xyz", body = "my-message"))
        session.queue_delete(queue = "durable-subscriber-queue")

        #test unbind:
        #create a series of bindings to a queue
        session.queue_declare(queue = "binding-test-queue", durable=True)
        session.exchange_bind(exchange="amq.direct", queue="binding-test-queue", binding_key="abc")
        session.exchange_bind(exchange="amq.direct", queue="binding-test-queue", binding_key="pqr")
        session.exchange_bind(exchange="amq.direct", queue="binding-test-queue", binding_key="xyz")
        session.exchange_bind(exchange="amq.match", queue="binding-test-queue", binding_key="a", arguments={"x-match":"all", "p":"a"})
        session.exchange_bind(exchange="amq.match", queue="binding-test-queue", binding_key="b", arguments={"x-match":"all", "p":"b"})
        session.exchange_bind(exchange="amq.match", queue="binding-test-queue", binding_key="c", arguments={"x-match":"all", "p":"c"})
        #then restart broker...


    def phase8(self):
        session = self.session

        #continue testing unbind:
        #send messages to the queue via each of the bindings
        for k in ["abc", "pqr", "xyz"]:
            data = "first %s" % (k)
            session.message_transfer(destination= "amq.direct", message=self.createMessage(routing_key=k, body=data))
        for a in [{"p":"a"}, {"p":"b"}, {"p":"c"}]:
            data = "first %s" % (a["p"])
            session.message_transfer(destination="amq.match", message=self.createMessage(application_headers=a, body=data))
        #unbind some bindings (using final 0-10 semantics)
        session.exchange_unbind(exchange="amq.direct", queue="binding-test-queue", binding_key="pqr")
        session.exchange_unbind(exchange="amq.match", queue="binding-test-queue", binding_key="b")
        #send messages again
        for k in ["abc", "pqr", "xyz"]:
            data = "second %s" % (k)
            session.message_transfer(destination= "amq.direct", message=self.createMessage(routing_key=k, body=data))
        for a in [{"p":"a"}, {"p":"b"}, {"p":"c"}]:
            data = "second %s" % (a["p"])
            session.message_transfer(destination="amq.match", message=self.createMessage(application_headers=a, body=data))

        #check that only the correct messages are received
        expected = []
        for k in ["abc", "pqr", "xyz"]:
            expected.append("first %s" % (k))
        for a in [{"p":"a"}, {"p":"b"}, {"p":"c"}]:
            expected.append("first %s" % (a["p"]))
        for k in ["abc", "xyz"]:
            expected.append("second %s" % (k))
        for a in [{"p":"a"}, {"p":"c"}]:
            expected.append("second %s" % (a["p"]))

        session.message_subscribe(queue = "binding-test-queue", destination = "binding-test")
        session.message_flow(unit = 1, value = 0xFFFFFFFF, destination = "binding-test")
        session.message_flow(unit = 0, value = 10, destination = "binding-test")
        queue = session.incoming("binding-test")

        while len(expected):
            msg = queue.get(timeout=1)
            if msg.body not in expected:
                self.fail("Missing message: %s" % msg.body)
            expected.remove(msg.body)
        try:
            msg = queue.get(timeout=1)
            self.fail("Got extra message: %s" % msg.body)
        except Empty: pass



        session.queue_declare(queue = "durable-subscriber-queue", exclusive=True, durable=True)
        session.exchange_bind(exchange="amq.topic", queue="durable-subscriber-queue", binding_key="xyz")
        session.message_transfer(destination= "amq.topic", message=self.createMessage(routing_key = "xyz", body = "my-message"))
        session.queue_delete(queue = "durable-subscriber-queue")


    def xid(self, txid, branchqual = ''):
        return self.session.xid(format=0, global_id=txid, branch_id=branchqual)

    def txswap(self, src, dest, tx):
        self.assertEqual(self.XA_OK, self.session.dtx_start(xid=tx).status)
        self.session.message_subscribe(destination="temp-swap", queue=src, accept_mode=0)
        self.session.message_flow(destination="temp-swap", unit=0, value=1)
        self.session.message_flow(destination="temp-swap", unit=1, value=0xFFFFFFFF)
        msg = self.session.incoming("temp-swap").get(timeout=1)
        self.session.message_cancel(destination="temp-swap")
        self.session.message_transfer(message=self.createMessage(routing_key=dest, correlation_id=self.getProperty(msg, 'correlation_id'),
                                                      body=msg.body))
        self.ack(msg)
        self.assertEqual(self.XA_OK, self.session.dtx_end(xid=tx).status)

    def assertEmptyQueue(self, name):
        self.assertEqual(0, self.session.queue_query(queue=name).message_count)

    def assertConnectionException(self, expectedCode, message):
        self.assertEqual("connection", message.method.klass.name)
        self.assertEqual("close", message.method.name)
        self.assertEqual(expectedCode, message.reply_code)

    def assertExpectedMethod(self, reply, klass, method):
        self.assertEqual(klass, reply.method.klass.name)
        self.assertEqual(method, reply.method.name)

    def assertExpectedContent(self, msg, id, body):
        self.assertEqual(id, self.getProperty(msg, 'correlation_id'))
        self.assertEqual(body, msg.body)
        return msg

    def getProperty(self, msg, name):
        for h in msg.headers:
            if hasattr(h, name): return getattr(h, name)
        return None

    def ack(self, *msgs):
        session = self.session
        set = RangedSet()
        for m in msgs:
            set.add(m.id)
            #TODO: tidy up completion
            session.receiver._completed.add(m.id)
        session.message_accept(set)
        session.channel.session_completed(session.receiver._completed)

    def assertExpectedGetResult(self, id, body):
        return self.assertExpectedContent(session.incoming("incoming-gets").get(timeout=1), id, body)

    def assertEqual(self, expected, actual, msg=''):
        if expected != actual: raise Exception("%s expected: %s actual: %s" % (msg, expected, actual))

    def assertMessageOnQueue(self, queue, id, body):
        self.session.message_subscribe(destination="incoming-gets", queue=queue, accept_mode=0)
        self.session.message_flow(destination="incoming-gets", unit=0, value=1)
        self.session.message_flow(destination="incoming-gets", unit=1, value=0xFFFFFFFF)
        msg = self.session.incoming("incoming-gets").get(timeout=1)
        self.assertExpectedContent(msg, id, body)
        self.ack(msg)
        self.session.message_cancel(destination="incoming-gets")


    def __init__(self):
        TestBase010.__init__(self, "run")
        self.setBroker("localhost")
        self.errata = []

    def connect(self):
        """ Connects to the broker  """
        self.conn = Connection(connect(self.host, self.port))
        self.conn.start(timeout=10)
        self.session = self.conn.session("test-session", timeout=10)

    def run(self, args=sys.argv[1:]):
        try:
            opts, extra = getopt(args, "r:s:e:b:p:h", ["retry=", "spec=", "errata=", "broker=", "phase=", "help"])
        except GetoptError, e:
            self._die(str(e))
        phase = 0
        retry = 0;
        for opt, value in opts:
            if opt in ("-h", "--help"): self._die()
            if opt in ("-s", "--spec"): self.spec = value
            if opt in ("-e", "--errata"): self.errata.append(value)
            if opt in ("-b", "--broker"): self.setBroker(value)
            if opt in ("-p", "--phase"): phase = int(value)
            if opt in ("-r", "--retry"): retry = int(value)

        if not phase: self._die("please specify the phase to run")
        phase = "phase%d" % phase
        self.connect()

        try:
            getattr(self, phase)()
            print phase, "succeeded"
            res = True;
        except Exception, e:
            print phase, "failed: ", e
            traceback.print_exc()
            res = False


        if not self.session.error(): self.session.close(timeout=10)
        self.conn.close(timeout=10)

         # Crude fix to wait for thread in client to exit after return from session_close()
         # Reduces occurrences of "Unhandled exception in thread" messages after each test
        import time
        time.sleep(1)

        return res


    def setBroker(self, broker):
        rex = re.compile(r"""
        # [   <user>  [   / <password> ] @]  <host>  [   :<port>   ]
        ^ (?: ([^/]*) (?: / ([^@]*)   )? @)? ([^:]+) (?: :([0-9]+))?$""", re.X)
        match = rex.match(broker)
        if not match: self._die("'%s' is not a valid broker" % (broker))
        self.user, self.password, self.host, self.port = match.groups()
        self.port = int(default(self.port, 5672))
        self.user = default(self.user, "guest")
        self.password = default(self.password, "guest")

    def _die(self, message = None):
        if message: print message
        print     """
Options:
  -h/--help               : this message
  -s/--spec <spec.xml>    : file containing amqp XML spec
  -p/--phase              : test phase to run
  -b/--broker [<user>[/<password>]@]<host>[:<port>] : broker to connect to
  """
        sys.exit(1)

def default(value, default):
    if (value == None): return default
    else: return value

if __name__ == "__main__":
    test = PersistenceTest()
    if not test.run(): sys.exit(1)
