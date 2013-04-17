#!/usr/bin/env python
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

import sys
from qpid.testlib import TestBase010
from qpid.datatypes import Message
from qpid.queue import Empty
from qpid.util import URL
import qpid.messaging
from time import sleep, time


class _FedBroker(object):
    """
    A proxy object for a remote broker.  Contains connection and management
    state.
    """
    def __init__(self, host, port, 
                 conn=None, session=None, qmf_broker=None):
        self.host = host
        self.port = port
        self.url = "%s:%d" % (host, port)
        self.client_conn = None
        self.client_session = None
        self.qmf_broker = None
        self.qmf_object = None
        if conn is not None:
            self.client_conn = conn
        if session is not None:
            self.client_session = session
        if qmf_broker is not None:
            self.qmf_broker = qmf_broker


class FederationTests(TestBase010):

    def remote_host(self):
        return self.defines.get("remote-host", "localhost")

    def remote_port(self):
        return int(self.defines["remote-port"])

    def verify_cleanup(self):
        attempts = 0
        total = len(self.qmf.getObjects(_class="bridge")) + len(self.qmf.getObjects(_class="link"))
        while total > 0:
            attempts += 1
            if attempts >= 10:
                self.fail("Bridges and links didn't clean up")
                return
            sleep(1)
            total = len(self.qmf.getObjects(_class="bridge")) + len(self.qmf.getObjects(_class="link"))

    def _setup_brokers(self):
        ports = [self.remote_port()]
        extra = self.defines.get("extra-brokers")
        if extra:
            for p in extra.split():
                ports.append(int(p))

        # broker[0] has already been set up.
        self._brokers = [_FedBroker(self.broker.host,
                                    self.broker.port,
                                    self.conn,
                                    self.session,
                                    self.qmf_broker)]
        self._brokers[0].qmf_object = self.qmf.getObjects(_class="broker")[0]

        # setup remaining brokers
        for _p in ports:
            _b = _FedBroker(self.remote_host(), _p)
            _b.client_conn = self.connect(host=self.remote_host(), port=_p)
            _b.client_session = _b.client_conn.session("Fed_client_session_" + str(_p))
            _b.qmf_broker = self.qmf.addBroker(_b.url)
            for _bo in self.qmf.getObjects(_class="broker"):
                if _bo.getBroker().getUrl() == _b.qmf_broker.getUrl():
                    _b.qmf_object = _bo
                    break
            self._brokers.append(_b)

        # add a new-style messaging connection to each broker
        for _b in self._brokers:
            _b.connection = qpid.messaging.Connection(_b.url)
            _b.connection.open()

    def _teardown_brokers(self):
        """ Un-does _setup_brokers()
        """
        # broker[0] is configured at test setup, so it must remain configured
        for _b in self._brokers[1:]:
            self.qmf.delBroker(_b.qmf_broker)
            if not _b.client_session.error():
                _b.client_session.close(timeout=10)
            _b.client_conn.close(timeout=10)
            _b.connection.close()

    def test_bridge_create_and_close(self):
        self.startQmf();
        qmf = self.qmf

        broker = qmf.getObjects(_class="broker")[0]
        result = broker.connect(self.remote_host(), self.remote_port(), False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0, result)

        link = qmf.getObjects(_class="link")[0]
        result = link.bridge(False, "amq.direct", "amq.direct", "my-key", "",
                             "", False, False, False, 0, 0)
        self.assertEqual(result.status, 0, result)

        bridge = qmf.getObjects(_class="bridge")[0]
        result = bridge.close()
        self.assertEqual(result.status, 0, result)

        result = link.close()
        self.assertEqual(result.status, 0, result)

        self.verify_cleanup()

    def test_pull_from_exchange(self):
        """ This test uses an alternative method to manage links and bridges
        via the broker object.
        """
        session = self.session

        self.startQmf()
        qmf = self.qmf
        broker = qmf.getObjects(_class="broker")[0]

        # create link
        link_args = {"host":self.remote_host(), "port":self.remote_port(), "durable":False,
                     "authMechanism":"PLAIN", "username":"guest", "password":"guest",
                     "transport":"tcp"}
        result = broker.create("link", "test-link-1", link_args, False)
        self.assertEqual(result.status, 0, result)
        link = qmf.getObjects(_class="link")[0]

        # create bridge
        bridge_args = {"link":"test-link-1", "src":"amq.direct", "dest":"amq.fanout",
                       "key":"my-key"}
        result = broker.create("bridge", "test-bridge-1", bridge_args, False);
        self.assertEqual(result.status, 0, result)
        bridge = qmf.getObjects(_class="bridge")[0]

        #setup queue to receive messages from local broker
        session.queue_declare(queue="fed1", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="fed1", exchange="amq.fanout")
        self.subscribe(queue="fed1", destination="f1")
        queue = session.incoming("f1")
        sleep(6)

        #send messages to remote broker and confirm it is routed to local broker
        r_conn = self.connect(host=self.remote_host(), port=self.remote_port())
        r_session = r_conn.session("test_pull_from_exchange")

        for i in range(1, 11):
            dp = r_session.delivery_properties(routing_key="my-key")
            r_session.message_transfer(destination="amq.direct", message=Message(dp, "Message %d" % i))

        for i in range(1, 11):
            msg = queue.get(timeout=5)
            self.assertEqual("Message %d" % i, msg.body)
        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message in queue: " + extra.body)
        except Empty: None


        result = broker.delete("bridge", "test-bridge-1", {})
        self.assertEqual(result.status, 0, result)

        result = broker.delete("link", "test-link-1", {})
        self.assertEqual(result.status, 0, result)

        self.verify_cleanup()

    def test_push_to_exchange(self):
        session = self.session
        
        self.startQmf()
        qmf = self.qmf
        broker = qmf.getObjects(_class="broker")[0]
        result = broker.connect(self.remote_host(), self.remote_port(), False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0, result)

        link = qmf.getObjects(_class="link")[0]
        result = link.bridge(False, "amq.direct", "amq.fanout", "my-key", "", "", False, True, False, 0, 0)
        self.assertEqual(result.status, 0, result)

        bridge = qmf.getObjects(_class="bridge")[0]

        #setup queue to receive messages from remote broker
        r_conn = self.connect(host=self.remote_host(), port=self.remote_port())
        r_session = r_conn.session("test_push_to_exchange")
        r_session.queue_declare(queue="fed1", exclusive=True, auto_delete=True)
        r_session.exchange_bind(queue="fed1", exchange="amq.fanout")
        self.subscribe(session=r_session, queue="fed1", destination="f1")
        queue = r_session.incoming("f1")
        sleep(6)

        #send messages to local broker and confirm it is routed to remote broker
        for i in range(1, 11):
            dp = session.delivery_properties(routing_key="my-key")
            session.message_transfer(destination="amq.direct", message=Message(dp, "Message %d" % i))

        for i in range(1, 11):
            msg = queue.get(timeout=5)
            self.assertEqual("Message %d" % i, msg.body)
        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message in queue: " + extra.body)
        except Empty: None

        result = bridge.close()
        self.assertEqual(result.status, 0, result)
        result = link.close()
        self.assertEqual(result.status, 0, result)

        self.verify_cleanup()

    def test_pull_from_queue(self):
        session = self.session

        #setup queue on remote broker and add some messages
        r_conn = self.connect(host=self.remote_host(), port=self.remote_port())
        r_session = r_conn.session("test_pull_from_queue")
        r_session.queue_declare(queue="my-bridge-queue", auto_delete=True)
        for i in range(1, 6):
            dp = r_session.delivery_properties(routing_key="my-bridge-queue")
            r_session.message_transfer(message=Message(dp, "Message %d" % i))

        #setup queue to receive messages from local broker
        session.queue_declare(queue="fed1", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="fed1", exchange="amq.fanout")
        self.subscribe(queue="fed1", destination="f1")
        queue = session.incoming("f1")

        self.startQmf()
        qmf = self.qmf
        broker = qmf.getObjects(_class="broker")[0]
        result = broker.connect(self.remote_host(), self.remote_port(), False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0, result)

        link = qmf.getObjects(_class="link")[0]
        result = link.bridge(False, "my-bridge-queue", "amq.fanout", "my-key", "", "", True, False, False, 1, 0)
        self.assertEqual(result.status, 0, result)

        bridge = qmf.getObjects(_class="bridge")[0]
        sleep(3)

        #add some more messages (i.e. after bridge was created)
        for i in range(6, 11):
            dp = r_session.delivery_properties(routing_key="my-bridge-queue")
            r_session.message_transfer(message=Message(dp, "Message %d" % i))

        for i in range(1, 11):
            try:
                msg = queue.get(timeout=5)
                self.assertEqual("Message %d" % i, msg.body)
            except Empty:
                self.fail("Failed to find expected message containing 'Message %d'" % i)
        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message in queue: " + extra.body)
        except Empty: None

        result = bridge.close()
        self.assertEqual(result.status, 0, result)
        result = link.close()
        self.assertEqual(result.status, 0, result)

        self.verify_cleanup()

    def test_pull_from_queue_recovery(self):
        session = self.session

        #setup queue on remote broker and add some messages
        r_conn = self.connect(host=self.remote_host(), port=self.remote_port())
        r_session = r_conn.session("test_pull_from_queue_recovery")
        # disable auto-delete otherwise the detach of the fed session may
        # delete the queue right after this test re-creates it.
        r_session.queue_declare(queue="my-bridge-queue", auto_delete=False)
        for i in range(1, 6):
            dp = r_session.delivery_properties(routing_key="my-bridge-queue")
            r_session.message_transfer(message=Message(dp, "Message %d" % i))

        #setup queue to receive messages from local broker
        session.queue_declare(queue="fed1", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="fed1", exchange="amq.fanout")
        self.subscribe(queue="fed1", destination="f1")
        queue = session.incoming("f1")

        self.startQmf()
        qmf = self.qmf
        broker = qmf.getObjects(_class="broker")[0]
        result = broker.connect(self.remote_host(), self.remote_port(), False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0, result)

        link = qmf.getObjects(_class="link")[0]
        result = link.bridge(False, "my-bridge-queue", "amq.fanout", "my-key", "", "", True, False, False, 1, 0)
        self.assertEqual(result.status, 0, result)

        bridge = qmf.getObjects(_class="bridge")[0]
        sleep(5)

        #recreate the remote bridge queue to invalidate the bridge session
        r_session.queue_delete (queue="my-bridge-queue", if_empty=False, if_unused=False)
        r_session.queue_declare(queue="my-bridge-queue", auto_delete=False)

        #add some more messages (i.e. after bridge was created)
        for i in range(6, 11):
            dp = r_session.delivery_properties(routing_key="my-bridge-queue")
            r_session.message_transfer(message=Message(dp, "Message %d" % i))

        for i in range(1, 11):
            try:
                msg = queue.get(timeout=5)
                self.assertEqual("Message %d" % i, msg.body)
            except Empty:
                self.fail("Failed to find expected message containing 'Message %d'" % i)
        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message in queue: " + extra.body)
        except Empty: None

        result = bridge.close()
        self.assertEqual(result.status, 0)
        result = link.close()
        self.assertEqual(result.status, 0)
        self.verify_cleanup()
        r_session.queue_delete (queue="my-bridge-queue", if_empty=False, if_unused=False)

    def test_tracing_automatic(self):
        remoteUrl = "%s:%d" % (self.remote_host(), self.remote_port())
        self.startQmf()
        l_broker = self.qmf_broker
        r_broker = self.qmf.addBroker(remoteUrl)

        l_brokerObj = self.qmf.getObjects(_class="broker", _broker=l_broker)[0]
        r_brokerObj = self.qmf.getObjects(_class="broker", _broker=r_broker)[0]

        l_res = l_brokerObj.connect(self.remote_host(), self.remote_port(),     False, "PLAIN", "guest", "guest", "tcp")
        r_res = r_brokerObj.connect(self.broker.host, self.broker.port, False, "PLAIN", "guest", "guest", "tcp")

        self.assertEqual(l_res.status, 0)
        self.assertEqual(r_res.status, 0)

        l_link = self.qmf.getObjects(_class="link", _broker=l_broker)[0]
        r_link = self.qmf.getObjects(_class="link", _broker=r_broker)[0]

        l_res = l_link.bridge(False, "amq.direct", "amq.direct", "key", "", "", False, False, False, 0, 0)
        r_res = r_link.bridge(False, "amq.direct", "amq.direct", "key", "", "", False, False, False, 0, 0)

        self.assertEqual(l_res.status, 0)
        self.assertEqual(r_res.status, 0)

        count = 0
        while l_link.state != "Operational" or r_link.state != "Operational":
            count += 1
            if count > 10:
                self.fail("Fed links didn't become operational after 10 seconds")
            sleep(1)
            l_link = self.qmf.getObjects(_class="link", _broker=l_broker)[0]
            r_link = self.qmf.getObjects(_class="link", _broker=r_broker)[0]
        sleep(3)

        #setup queue to receive messages from local broker
        session = self.session
        session.queue_declare(queue="fed1", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="fed1", exchange="amq.direct", binding_key="key")
        self.subscribe(queue="fed1", destination="f1")
        queue = session.incoming("f1")

        #setup queue on remote broker and add some messages
        r_conn = self.connect(host=self.remote_host(), port=self.remote_port())
        r_session = r_conn.session("test_trace")
        for i in range(1, 11):
            dp = r_session.delivery_properties(routing_key="key")
            r_session.message_transfer(destination="amq.direct", message=Message(dp, "Message %d" % i))

        for i in range(1, 11):
            try:
                msg = queue.get(timeout=5)
                mp = msg.get("message_properties").application_headers
                self.assertEqual(mp.__class__, dict)
                self.assertEqual(mp['x-qpid.trace'], 'REMOTE') # check that the federation-tag override works
                self.assertEqual("Message %d" % i, msg.body)
            except Empty:
                self.fail("Failed to find expected message containing 'Message %d'" % i)
        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message in queue: " + extra.body)
        except Empty: None

    def test_tracing(self):
        session = self.session
        
        self.startQmf()
        qmf = self.qmf
        broker = qmf.getObjects(_class="broker")[0]
        result = broker.connect(self.remote_host(), self.remote_port(), False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0)

        link = qmf.getObjects(_class="link")[0]
        result = link.bridge(False, "amq.direct", "amq.fanout", "my-key", "my-bridge-id",
                             "exclude-me,also-exclude-me", False, False, False, 0, 0)
        self.assertEqual(result.status, 0)
        bridge = qmf.getObjects(_class="bridge")[0]

        #setup queue to receive messages from local broker
        session.queue_declare(queue="fed1", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="fed1", exchange="amq.fanout")
        self.subscribe(queue="fed1", destination="f1")
        queue = session.incoming("f1")
        sleep(6)

        #send messages to remote broker and confirm it is routed to local broker
        r_conn = self.connect(host=self.remote_host(), port=self.remote_port())
        r_session = r_conn.session("test_tracing")

        trace = [None, "exclude-me", "a,exclude-me,b", "also-exclude-me,c", "dont-exclude-me"]
        body = ["yes", "first-bad", "second-bad", "third-bad", "yes"]
        for b, t in zip(body, trace):
            headers = {}
            if (t): headers["x-qpid.trace"]=t
            dp = r_session.delivery_properties(routing_key="my-key", ttl=1000*60*5)
            mp = r_session.message_properties(application_headers=headers)
            r_session.message_transfer(destination="amq.direct", message=Message(dp, mp, b))

        for e in ["my-bridge-id", "dont-exclude-me,my-bridge-id"]:
            msg = queue.get(timeout=5)
            self.assertEqual("yes", msg.body)
            self.assertEqual(e, self.getAppHeader(msg, "x-qpid.trace"))
            assert(msg.get("delivery_properties").ttl > 0)
            assert(msg.get("delivery_properties").ttl < 1000*60*50)

        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message in queue: " + extra.body)
        except Empty: None

        result = bridge.close()
        self.assertEqual(result.status, 0)
        result = link.close()
        self.assertEqual(result.status, 0)

        self.verify_cleanup()

    def test_dynamic_fanout(self):
        session = self.session
        r_conn = self.connect(host=self.remote_host(), port=self.remote_port())
        r_session = r_conn.session("test_dynamic_fanout")

        session.exchange_declare(exchange="fed.fanout", type="fanout")
        r_session.exchange_declare(exchange="fed.fanout", type="fanout")

        self.startQmf()
        qmf = self.qmf
        broker = qmf.getObjects(_class="broker")[0]
        result = broker.connect(self.remote_host(), self.remote_port(), False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0)

        link = qmf.getObjects(_class="link")[0]
        result = link.bridge(False, "fed.fanout", "fed.fanout", "", "", "", False, False, True, 0, 0)
        self.assertEqual(result.status, 0)
        bridge = qmf.getObjects(_class="bridge")[0]
        sleep(5)

        session.queue_declare(queue="fed1", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="fed1", exchange="fed.fanout")
        self.subscribe(queue="fed1", destination="f1")
        queue = session.incoming("f1")

        for i in range(1, 11):
            dp = r_session.delivery_properties()
            r_session.message_transfer(destination="fed.fanout", message=Message(dp, "Message %d" % i))

        for i in range(1, 11):
            msg = queue.get(timeout=5)
            self.assertEqual("Message %d" % i, msg.body)
        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message in queue: " + extra.body)
        except Empty: None

        result = bridge.close()
        self.assertEqual(result.status, 0)
        result = link.close()
        self.assertEqual(result.status, 0)

        self.verify_cleanup()


    def test_dynamic_direct(self):
        session = self.session
        r_conn = self.connect(host=self.remote_host(), port=self.remote_port())
        r_session = r_conn.session("test_dynamic_direct")

        session.exchange_declare(exchange="fed.direct", type="direct")
        r_session.exchange_declare(exchange="fed.direct", type="direct")

        self.startQmf()
        qmf = self.qmf
        broker = qmf.getObjects(_class="broker")[0]
        result = broker.connect(self.remote_host(), self.remote_port(), False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0)

        link = qmf.getObjects(_class="link")[0]
        result = link.bridge(False, "fed.direct", "fed.direct", "", "", "", False, False, True, 0, 0)
        self.assertEqual(result.status, 0)
        bridge = qmf.getObjects(_class="bridge")[0]
        sleep(5)

        session.queue_declare(queue="fed1", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="fed1", exchange="fed.direct", binding_key="fd-key")
        self.subscribe(queue="fed1", destination="f1")
        queue = session.incoming("f1")

        for i in range(1, 11):
            dp = r_session.delivery_properties(routing_key="fd-key")
            r_session.message_transfer(destination="fed.direct", message=Message(dp, "Message %d" % i))

        for i in range(1, 11):
            msg = queue.get(timeout=5)
            self.assertEqual("Message %d" % i, msg.body)
        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message in queue: " + extra.body)
        except Empty: None

        result = bridge.close()
        self.assertEqual(result.status, 0)
        result = link.close()
        self.assertEqual(result.status, 0)

        self.verify_cleanup()

    def test_dynamic_topic(self):
        session = self.session
        r_conn = self.connect(host=self.remote_host(), port=self.remote_port())
        r_session = r_conn.session("test_dynamic_topic")

        session.exchange_declare(exchange="fed.topic", type="topic")
        r_session.exchange_declare(exchange="fed.topic", type="topic")

        self.startQmf()
        qmf = self.qmf
        broker = qmf.getObjects(_class="broker")[0]
        result = broker.connect(self.remote_host(), self.remote_port(), False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0)

        link = qmf.getObjects(_class="link")[0]
        result = link.bridge(False, "fed.topic", "fed.topic", "", "", "", False, False, True, 0, 0)
        self.assertEqual(result.status, 0)
        bridge = qmf.getObjects(_class="bridge")[0]
        sleep(5)

        session.queue_declare(queue="fed1", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="fed1", exchange="fed.topic", binding_key="ft-key.#")
        self.subscribe(queue="fed1", destination="f1")
        queue = session.incoming("f1")

        for i in range(1, 11):
            dp = r_session.delivery_properties(routing_key="ft-key.one.two")
            r_session.message_transfer(destination="fed.topic", message=Message(dp, "Message %d" % i))

        for i in range(1, 11):
            msg = queue.get(timeout=5)
            self.assertEqual("Message %d" % i, msg.body)
        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message in queue: " + extra.body)
        except Empty: None

        result = bridge.close()
        self.assertEqual(result.status, 0)
        result = link.close()
        self.assertEqual(result.status, 0)

        self.verify_cleanup()

    def test_dynamic_topic_reorigin(self):
        session = self.session
        r_conn = self.connect(host=self.remote_host(), port=self.remote_port())
        r_session = r_conn.session("test_dynamic_topic_reorigin")

        session.exchange_declare(exchange="fed.topic_reorigin", type="topic")
        r_session.exchange_declare(exchange="fed.topic_reorigin", type="topic")

        session.exchange_declare(exchange="fed.topic_reorigin_2", type="topic")
        r_session.exchange_declare(exchange="fed.topic_reorigin_2", type="topic")

        self.startQmf()
        qmf = self.qmf
        broker = qmf.getObjects(_class="broker")[0]
        result = broker.connect(self.remote_host(), self.remote_port(), False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0)

        session.queue_declare(queue="fed2", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="fed2", exchange="fed.topic_reorigin_2", binding_key="ft-key.one.#")
        self.subscribe(queue="fed2", destination="f2")
        queue2 = session.incoming("f2")

        link = qmf.getObjects(_class="link")[0]
        result = link.bridge(False, "fed.topic_reorigin", "fed.topic_reorigin", "", "", "", False, False, True, 0, 0)
        self.assertEqual(result.status, 0)
        result = link.bridge(False, "fed.topic_reorigin_2", "fed.topic_reorigin_2", "", "", "", False, False, True, 0, 0)
        self.assertEqual(result.status, 0)

        bridge = qmf.getObjects(_class="bridge")[0]
        bridge2 = qmf.getObjects(_class="bridge")[1]
        sleep(5)

        session.queue_declare(queue="fed1", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="fed1", exchange="fed.topic_reorigin", binding_key="ft-key.#")
        self.subscribe(queue="fed1", destination="f1")
        queue = session.incoming("f1")

        for i in range(1, 11):
            dp = r_session.delivery_properties(routing_key="ft-key.one.two")
            r_session.message_transfer(destination="fed.topic_reorigin", message=Message(dp, "Message %d" % i))

        for i in range(1, 11):
            msg = queue.get(timeout=5)
            self.assertEqual("Message %d" % i, msg.body)
        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message in queue: " + extra.body)
        except Empty: None

        result = bridge.close()
        self.assertEqual(result.status, 0)
        result = bridge2.close()
        self.assertEqual(result.status, 0)

        # extra check: verify we don't leak bridge objects - keep the link
        # around and verify the bridge count has gone to zero

        attempts = 0
        bridgeCount = len(qmf.getObjects(_class="bridge"))
        while bridgeCount > 0:
            attempts += 1
            if attempts >= 5:
                self.fail("Bridges didn't clean up")
                return
            sleep(1)
            bridgeCount = len(qmf.getObjects(_class="bridge"))

        result = link.close()
        self.assertEqual(result.status, 0)

        self.verify_cleanup()
        
    def test_dynamic_direct_reorigin(self):
        session = self.session
        r_conn = self.connect(host=self.remote_host(), port=self.remote_port())
        r_session = r_conn.session("test_dynamic_direct_reorigin")

        session.exchange_declare(exchange="fed.direct_reorigin", type="direct")
        r_session.exchange_declare(exchange="fed.direct_reorigin", type="direct")

        session.exchange_declare(exchange="fed.direct_reorigin_2", type="direct")
        r_session.exchange_declare(exchange="fed.direct_reorigin_2", type="direct")

        self.startQmf()
        qmf = self.qmf
        broker = qmf.getObjects(_class="broker")[0]
        result = broker.connect(self.remote_host(), self.remote_port(), False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0)

        session.queue_declare(queue="fed2", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="fed2", exchange="fed.direct_reorigin_2", binding_key="ft-key.two")
        self.subscribe(queue="fed2", destination="f2")
        queue2 = session.incoming("f2")

        link = qmf.getObjects(_class="link")[0]
        result = link.bridge(False, "fed.direct_reorigin", "fed.direct_reorigin", "", "", "", False, False, True, 0, 0)
        self.assertEqual(result.status, 0)
        result = link.bridge(False, "fed.direct_reorigin_2", "fed.direct_reorigin_2", "", "", "", False, False, True, 0, 0)
        self.assertEqual(result.status, 0)

        bridge = qmf.getObjects(_class="bridge")[0]
        bridge2 = qmf.getObjects(_class="bridge")[1]
        sleep(5)

        session.queue_declare(queue="fed1", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="fed1", exchange="fed.direct_reorigin", binding_key="ft-key.one")
        self.subscribe(queue="fed1", destination="f1")
        queue = session.incoming("f1")

        for i in range(1, 11):
            dp = r_session.delivery_properties(routing_key="ft-key.one")
            r_session.message_transfer(destination="fed.direct_reorigin", message=Message(dp, "Message %d" % i))

        for i in range(1, 11):
            msg = queue.get(timeout=5)
            self.assertEqual("Message %d" % i, msg.body)
        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message in queue: " + extra.body)
        except Empty: None

        result = bridge.close()
        self.assertEqual(result.status, 0)
        
        # Extra test: don't explicitly close() bridge2.  When the link is closed,
        # it should clean up bridge2 automagically.  verify_cleanup() will detect
        # if bridge2 isn't cleaned up and will fail the test.
        #
        #result = bridge2.close()
        #self.assertEqual(result.status, 0)
        result = link.close()
        self.assertEqual(result.status, 0)

        self.verify_cleanup()

    def test_dynamic_headers_any(self):
        self.do_test_dynamic_headers('any')

    def test_dynamic_headers_all(self):
        self.do_test_dynamic_headers('all')


    def do_test_dynamic_headers(self, match_mode):
        session = self.session
        r_conn = self.connect(host=self.remote_host(), port=self.remote_port())
        r_session = r_conn.session("test_dynamic_headers_%s" % match_mode)

        session.exchange_declare(exchange="fed.headers", type="headers")
        r_session.exchange_declare(exchange="fed.headers", type="headers")

        self.startQmf()
        qmf = self.qmf

        broker = qmf.getObjects(_class="broker")[0]
        result = broker.connect(self.remote_host(), self.remote_port(), False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0)

        link = qmf.getObjects(_class="link")[0]
        result = link.bridge(False, "fed.headers", "fed.headers", "", "", "", False, False, True, 0, 0)
        self.assertEqual(result.status, 0)
        bridge = qmf.getObjects(_class="bridge")[0]
        sleep(5)

        session.queue_declare(queue="fed1", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="fed1", exchange="fed.headers", binding_key="key1", arguments={'x-match':match_mode, 'class':'first'})
        self.subscribe(queue="fed1", destination="f1")
        queue = session.incoming("f1")

        props = r_session.message_properties(application_headers={'class':'first'})
        for i in range(1, 11):
            r_session.message_transfer(destination="fed.headers", message=Message(props, "Message %d" % i))

        for i in range(1, 11):
            msg = queue.get(timeout=5)
            content = msg.body
            self.assertEqual("Message %d" % i, msg.body)
        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message in queue: " + extra.body)
        except Empty: None

        result = bridge.close()
        self.assertEqual(result.status, 0)
        result = link.close()
        self.assertEqual(result.status, 0)

        self.verify_cleanup()

    def test_dynamic_headers_reorigin(self):
        session = self.session
        r_conn = self.connect(host=self.remote_host(), port=self.remote_port())
        r_session = r_conn.session("test_dynamic_headers_reorigin")

        session.exchange_declare(exchange="fed.headers_reorigin", type="headers")
        r_session.exchange_declare(exchange="fed.headers_reorigin", type="headers")

        session.exchange_declare(exchange="fed.headers_reorigin_2", type="headers")
        r_session.exchange_declare(exchange="fed.headers_reorigin_2", type="headers")

        self.startQmf()
        qmf = self.qmf
        broker = qmf.getObjects(_class="broker")[0]
        result = broker.connect(self.remote_host(), self.remote_port(), False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0)

        session.queue_declare(queue="fed2", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="fed2", exchange="fed.headers_reorigin_2", binding_key="key2", arguments={'x-match':'any', 'class':'second'})
        self.subscribe(queue="fed2", destination="f2")
        queue2 = session.incoming("f2")

        link = qmf.getObjects(_class="link")[0]
        result = link.bridge(False, "fed.headers_reorigin", "fed.headers_reorigin", "", "", "", False, False, True, 0, 0)
        self.assertEqual(result.status, 0)
        result = link.bridge(False, "fed.headers_reorigin_2", "fed.headers_reorigin_2", "", "", "", False, False, True, 0, 0)
        self.assertEqual(result.status, 0)

        bridge = qmf.getObjects(_class="bridge")[0]
        bridge2 = qmf.getObjects(_class="bridge")[1]
        sleep(5)

        session.queue_declare(queue="fed1", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="fed1", exchange="fed.headers_reorigin", binding_key="key1", arguments={'x-match':'any', 'class':'first'})
        self.subscribe(queue="fed1", destination="f1")
        queue = session.incoming("f1")

        props = r_session.message_properties(application_headers={'class':'first'})
        for i in range(1, 11):
            r_session.message_transfer(destination="fed.headers_reorigin", message=Message(props, "Message %d" % i))

        for i in range(1, 11):
            msg = queue.get(timeout=5)
            self.assertEqual("Message %d" % i, msg.body)
        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message in queue: " + extra.body)
        except Empty: None

        result = bridge.close()
        self.assertEqual(result.status, 0)

        # Extra test: don't explicitly close() bridge2.  When the link is closed,
        # it should clean up bridge2 automagically.  verify_cleanup() will detect
        # if bridge2 isn't cleaned up and will fail the test.
        #
        #result = bridge2.close()
        #self.assertEqual(result.status, 0)
        result = link.close()
        self.assertEqual(result.status, 0)

        self.verify_cleanup()

    def test_dynamic_headers_unbind(self):
        session = self.session
        r_conn = self.connect(host=self.remote_host(), port=self.remote_port())
        r_session = r_conn.session("test_dynamic_headers_unbind")

        session.exchange_declare(exchange="fed.headers_unbind", type="headers")
        r_session.exchange_declare(exchange="fed.headers_unbind", type="headers")

        self.startQmf()
        qmf = self.qmf

        broker = qmf.getObjects(_class="broker")[0]
        result = broker.connect(self.remote_host(), self.remote_port(), False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0)

        link = qmf.getObjects(_class="link")[0]
        result = link.bridge(False, "fed.headers_unbind", "fed.headers_unbind", "", "", "", False, False, True, 0, 0)
        self.assertEqual(result.status, 0)
        bridge = qmf.getObjects(_class="bridge")[0]
        sleep(5)

        session.queue_declare(queue="fed1", exclusive=True, auto_delete=True)
        queue = qmf.getObjects(_class="queue", name="fed1")[0]
        queue.update()
        self.assertEqual(queue.bindingCount, 1,
                         "bindings not accounted for (expected 1, got %d)" % queue.bindingCount)

        session.exchange_bind(queue="fed1", exchange="fed.headers_unbind", binding_key="key1", arguments={'x-match':'any', 'class':'first'})
        queue.update()
        self.assertEqual(queue.bindingCount, 2,
                         "bindings not accounted for (expected 2, got %d)" % queue.bindingCount)

        session.exchange_unbind(queue="fed1", exchange="fed.headers_unbind", binding_key="key1")
        queue.update()
        self.assertEqual(queue.bindingCount, 1,
                         "bindings not accounted for (expected 1, got %d)" % queue.bindingCount)

        result = bridge.close()
        self.assertEqual(result.status, 0)
        result = link.close()
        self.assertEqual(result.status, 0)

        self.verify_cleanup()


    def test_dynamic_headers_xml(self):
        session = self.session
        r_conn = self.connect(host=self.remote_host(), port=self.remote_port())
        r_session = r_conn.session("test_dynamic_headers_xml")

        session.exchange_declare(exchange="fed.xml", type="xml")
        r_session.exchange_declare(exchange="fed.xml", type="xml")

        self.startQmf()
        qmf = self.qmf

        broker = qmf.getObjects(_class="broker")[0]
        result = broker.connect(self.remote_host(), self.remote_port(), False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0)

        link = qmf.getObjects(_class="link")[0]
        result = link.bridge(False, "fed.xml", "fed.xml", "", "", "", False, False, True, 0, 0)
        
        self.assertEqual(result.status, 0) 
        bridge = qmf.getObjects(_class="bridge")[0]
        sleep(5)

        session.queue_declare(queue="fed1", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="fed1", exchange="fed.xml", binding_key="key1", arguments={'xquery':'true()'})
        self.subscribe(queue="fed1", destination="f1")
        queue = session.incoming("f1")
        
        props = r_session.delivery_properties(routing_key="key1")
        for i in range(1, 11):
            r_session.message_transfer(destination="fed.xml", message=Message(props, "Message %d" % i))

        for i in range(1, 11):
            msg = queue.get(timeout=5)
            content = msg.body
            self.assertEqual("Message %d" % i, msg.body)
        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message in queue: " + extra.body)
        except Empty: None

        result = bridge.close()
        self.assertEqual(result.status, 0)
        result = link.close()
        self.assertEqual(result.status, 0)

        self.verify_cleanup()

    def test_dynamic_headers_reorigin_xml(self):
        session = self.session
        r_conn = self.connect(host=self.remote_host(), port=self.remote_port())
        r_session = r_conn.session("test_dynamic_headers_reorigin_xml")

        session.exchange_declare(exchange="fed.xml_reorigin", type="xml")
        r_session.exchange_declare(exchange="fed.xml_reorigin", type="xml")

        session.exchange_declare(exchange="fed.xml_reorigin_2", type="xml")
        r_session.exchange_declare(exchange="fed.xml_reorigin_2", type="xml")

        self.startQmf()
        qmf = self.qmf
        broker = qmf.getObjects(_class="broker")[0]
        result = broker.connect(self.remote_host(), self.remote_port(), False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0)

        session.queue_declare(queue="fed2", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="fed2", exchange="fed.xml_reorigin_2", binding_key="key2",  arguments={'xquery':'true()'})
        self.subscribe(queue="fed2", destination="f2")
        queue2 = session.incoming("f2")

        link = qmf.getObjects(_class="link")[0]
        result = link.bridge(False, "fed.xml_reorigin", "fed.xml_reorigin", "", "", "", False, False, True, 0, 0)

        self.assertEqual(result.status, 0) 
        result = link.bridge(False, "fed.xml_reorigin_2", "fed.xml_reorigin_2", "", "", "", False, False, True, 0, 0)
        self.assertEqual(result.status, 0)

        bridge = qmf.getObjects(_class="bridge")[0]
        bridge2 = qmf.getObjects(_class="bridge")[1]
        sleep(5)

        foo=qmf.getObjects(_class="link")
        session.queue_declare(queue="fed1", exclusive=True, auto_delete=True)  
        session.exchange_bind(queue="fed1", exchange="fed.xml_reorigin", binding_key="key1",  arguments={'xquery':'true()'})
        self.subscribe(queue="fed1", destination="f1")
        queue = session.incoming("f1")

        props = r_session.delivery_properties(routing_key="key1")
        for i in range(1, 11):
            r_session.message_transfer(destination="fed.xml_reorigin", message=Message(props, "Message %d" % i))

        for i in range(1, 11):
            msg = queue.get(timeout=5)
            self.assertEqual("Message %d" % i, msg.body)
        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message in queue: " + extra.body)
        except Empty: None

        result = bridge.close()
        self.assertEqual(result.status, 0)

        # Extra test: don't explicitly close() bridge2.  When the link is closed,
        # it should clean up bridge2 automagically.  verify_cleanup() will detect
        # if bridge2 isn't cleaned up and will fail the test.
        #
        #result = bridge2.close()
        #self.assertEqual(result.status, 0)
        result = link.close()
        self.assertEqual(result.status, 0)

        self.verify_cleanup()

    def test_dynamic_headers_unbind_xml(self):
        session = self.session
        r_conn = self.connect(host=self.remote_host(), port=self.remote_port())
        r_session = r_conn.session("test_dynamic_xml_unbind")

        session.exchange_declare(exchange="fed.xml_unbind", type="xml")
        r_session.exchange_declare(exchange="fed.xml_unbind", type="xml")

        self.startQmf()
        qmf = self.qmf

        broker = qmf.getObjects(_class="broker")[0]
        result = broker.connect(self.remote_host(), self.remote_port(), False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0)

        link = qmf.getObjects(_class="link")[0]
        result = link.bridge(False, "fed.xml_unbind", "fed.xml_unbind", "", "", "", False, False, True, 0, 0)

        self.assertEqual(result.status, 0) 
        bridge = qmf.getObjects(_class="bridge")[0]
        sleep(5)

        session.queue_declare(queue="fed1", exclusive=True, auto_delete=True)
        queue = qmf.getObjects(_class="queue", name="fed1")[0]
        queue.update()
        self.assertEqual(queue.bindingCount, 1,
                         "bindings not accounted for (expected 1, got %d)" % queue.bindingCount)

        session.exchange_bind(queue="fed1", exchange="fed.xml_unbind", binding_key="key1",  arguments={'xquery':'true()'})
        queue.update()
        self.assertEqual(queue.bindingCount, 2,
                         "bindings not accounted for (expected 2, got %d)" % queue.bindingCount) 

        session.exchange_unbind(queue="fed1", exchange="fed.xml_unbind", binding_key="key1")
        queue.update()
        self.assertEqual(queue.bindingCount, 1,
                         "bindings not accounted for (expected 1, got %d)" % queue.bindingCount)

        result = bridge.close()
        self.assertEqual(result.status, 0)
        result = link.close()
        self.assertEqual(result.status, 0)

        self.verify_cleanup()


    def test_dynamic_topic_nodup(self):
        """Verify that a message whose routing key matches more than one
        binding does not get duplicated to the same queue.
        """
        session = self.session
        r_conn = self.connect(host=self.remote_host(), port=self.remote_port())
        r_session = r_conn.session("test_dynamic_topic_nodup")

        session.exchange_declare(exchange="fed.topic", type="topic")
        r_session.exchange_declare(exchange="fed.topic", type="topic")

        self.startQmf()
        qmf = self.qmf
        broker = qmf.getObjects(_class="broker")[0]
        result = broker.connect(self.remote_host(), self.remote_port(), False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0)

        link = qmf.getObjects(_class="link")[0]
        result = link.bridge(False, "fed.topic", "fed.topic", "", "", "", False, False, True, 0, 0)
        self.assertEqual(result.status, 0)
        bridge = qmf.getObjects(_class="bridge")[0]
        sleep(5)

        session.queue_declare(queue="fed1", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="fed1", exchange="fed.topic", binding_key="red.*")
        session.exchange_bind(queue="fed1", exchange="fed.topic", binding_key="*.herring")

        self.subscribe(queue="fed1", destination="f1")
        queue = session.incoming("f1")

        for i in range(1, 11):
            dp = r_session.delivery_properties(routing_key="red.herring")
            r_session.message_transfer(destination="fed.topic", message=Message(dp, "Message %d" % i))

        for i in range(1, 11):
            msg = queue.get(timeout=5)
            self.assertEqual("Message %d" % i, msg.body)
        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message in queue: " + extra.body)
        except Empty: None

        result = bridge.close()
        self.assertEqual(result.status, 0)
        result = link.close()
        self.assertEqual(result.status, 0)

        self.verify_cleanup()


    def test_dynamic_direct_route_prop(self):
        """ Set up a tree of uni-directional routes across the direct exchange.
        Bind the same key to the same queues on the leaf nodes.  Verify a
        message sent with the routing key transverses the tree an arrives at
        each leaf.  Remove one leaf's queue, and verify that messages still
        reach the other leaf.

        Route Topology:

                    +---> B2  queue:"test-queue", binding key:"spudboy"
        B0 --> B1 --+
                    +---> B3  queue:"test-queue", binding key:"spudboy"
        """
        session = self.session

        # create the federation

        self.startQmf()
        qmf = self.qmf

        self._setup_brokers()

        # create direct exchange on each broker, and retrieve the corresponding
        # management object for that exchange

        exchanges=[]
        for _b in self._brokers:
            _b.client_session.exchange_declare(exchange="fedX.direct", type="direct")
            self.assertEqual(_b.client_session.exchange_query(name="fedX.direct").type,
                             "direct", "exchange_declare failed!")
            # pull the exchange out of qmf...
            retries = 0
            my_exchange = None
            while my_exchange is None:
                objs = qmf.getObjects(_broker=_b.qmf_broker, _class="exchange")
                for ooo in objs:
                    if ooo.name == "fedX.direct":
                        my_exchange = ooo
                        break
                if my_exchange is None:
                    retries += 1
                    self.failIfEqual(retries, 10,
                                     "QMF failed to find new exchange!")
                    sleep(1)
            exchanges.append(my_exchange)

        self.assertEqual(len(exchanges), len(self._brokers), "Exchange creation failed!")

        # connect B0 --> B1
        result = self._brokers[1].qmf_object.connect(self._brokers[0].host,
                                                     self._brokers[0].port,
                                                     False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0)

        # connect B1 --> B2
        result = self._brokers[2].qmf_object.connect(self._brokers[1].host,
                                                     self._brokers[1].port,
                                                     False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0)

        # connect B1 --> B3
        result = self._brokers[3].qmf_object.connect(self._brokers[1].host, 
                                                     self._brokers[1].port,
                                                     False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0)

        # for each link, bridge the "fedX.direct" exchanges:

        for _l in qmf.getObjects(_class="link"):
            # print("Link=%s:%s %s" % (_l.host, _l.port, str(_l.getBroker())))
            result = _l.bridge(False,  # durable
                                 "fedX.direct",  # src
                                 "fedX.direct",  # dst
                                 "",  # key
                                 "",  # tag
                                 "",  # excludes
                                 False, # srcIsQueue
                                 False, # srcIsLocal
                                 True,  # dynamic
                                 0,     # sync
                                 0)     # credit
            self.assertEqual(result.status, 0)

        # wait for the inter-broker links to become operational
        retries = 0
        operational = False
        while not operational:
            operational = True
            for _l in qmf.getObjects(_class="link"):
                #print("Link=%s:%s %s" % (_l.host, _l.port, str(_l.state)))
                if _l.state != "Operational":
                    operational = False
            if not operational:
                retries += 1
                self.failIfEqual(retries, 10,
                                 "inter-broker links failed to become operational.")
                sleep(1)

        # @todo - There is no way to determine when the bridge objects become
        # active.  Hopefully, this is long enough!
        sleep(6)

        # create a queue on B2, bound to "spudboy"
        self._brokers[2].client_session.queue_declare(queue="fedX1", exclusive=True, auto_delete=True)
        self._brokers[2].client_session.exchange_bind(queue="fedX1", exchange="fedX.direct", binding_key="spudboy")

        # create a queue on B3, bound to "spudboy"
        self._brokers[3].client_session.queue_declare(queue="fedX1", exclusive=True, auto_delete=True)
        self._brokers[3].client_session.exchange_bind(queue="fedX1", exchange="fedX.direct", binding_key="spudboy")

        # subscribe to messages arriving on B2's queue
        self.subscribe(self._brokers[2].client_session, queue="fedX1", destination="f1")
        queue_2 = self._brokers[2].client_session.incoming("f1")

        # subscribe to messages arriving on B3's queue
        self.subscribe(self._brokers[3].client_session, queue="fedX1", destination="f1")
        queue_3 = self._brokers[3].client_session.incoming("f1")

        # wait until the binding key has propagated to each broker (twice at
        # broker B1).  Work backwards from binding brokers.

        binding_counts = [1, 2, 1, 1]
        self.assertEqual(len(binding_counts), len(exchanges), "Update Test!")
        for i in range(3,-1,-1):
            retries = 0
            exchanges[i].update()
            while exchanges[i].bindingCount < binding_counts[i]:
                retries += 1
                self.failIfEqual(retries, 10,
                                 "binding failed to propagate to broker %d"
                                 % i)
                sleep(3)
                exchanges[i].update()

        # send 10 msgs from B0
        for i in range(1, 11):
            dp = self._brokers[0].client_session.delivery_properties(routing_key="spudboy")
            self._brokers[0].client_session.message_transfer(destination="fedX.direct", message=Message(dp, "Message_drp %d" % i))

        # wait for 10 messages to be forwarded from B0->B1,
        # 10 messages from B1->B2,
        # and 10 messages from B1->B3
        retries = 0
        for ex in exchanges:
            ex.update()
        while (exchanges[0].msgReceives != 10 or exchanges[0].msgRoutes != 10 or
               exchanges[1].msgReceives != 10 or exchanges[1].msgRoutes != 20 or
               exchanges[2].msgReceives != 10 or exchanges[2].msgRoutes != 10 or
               exchanges[3].msgReceives != 10 or exchanges[3].msgRoutes != 10):
            retries += 1
            self.failIfEqual(retries, 10,
                             "federation failed to route msgs %d:%d %d:%d %d:%d %d:%d"
                             % (exchanges[0].msgReceives,
                                exchanges[0].msgRoutes,
                                exchanges[1].msgReceives,
                                exchanges[1].msgRoutes,
                                exchanges[2].msgReceives,
                                exchanges[2].msgRoutes,
                                exchanges[3].msgReceives,
                                exchanges[3].msgRoutes))
            sleep(1)
            for ex in exchanges:
                ex.update()

        # get exactly 10 msgs on B2 and B3
        for i in range(1, 11):
            msg = queue_2.get(timeout=5)
            self.assertEqual("Message_drp %d" % i, msg.body)
            msg = queue_3.get(timeout=5)
            self.assertEqual("Message_drp %d" % i, msg.body)

        try:
            extra = queue_2.get(timeout=1)
            self.fail("Got unexpected message in queue_2: " + extra.body)
        except Empty: None

        try:
            extra = queue_3.get(timeout=1)
            self.fail("Got unexpected message in queue_3: " + extra.body)
        except Empty: None


        # tear down the queue on B2
        self._brokers[2].client_session.exchange_unbind(queue="fedX1", exchange="fedX.direct", binding_key="spudboy")
        self._brokers[2].client_session.message_cancel(destination="f1")
        self._brokers[2].client_session.queue_delete(queue="fedX1")

        # @todo - restore code when QPID-2499 fixed!!
        sleep(6)
        # wait for the binding count on B1 to drop from 2 to 1
        retries = 0
        exchanges[1].update()
        while exchanges[1].bindingCount != 1:
            retries += 1
            self.failIfEqual(retries, 10,
                             "unbinding failed to propagate to broker B1: %d"
                             % exchanges[1].bindingCount)
            sleep(1)
            exchanges[1].update()

        # send 10 msgs from B0
        for i in range(11, 21):
            dp = self._brokers[0].client_session.delivery_properties(routing_key="spudboy")
            self._brokers[0].client_session.message_transfer(destination="fedX.direct", message=Message(dp, "Message_drp %d" % i))

        # verify messages are forwarded to B3 only
        retries = 0
        for ex in exchanges:
            ex.update()
        while (exchanges[0].msgReceives != 20 or exchanges[0].msgRoutes != 20 or
               exchanges[1].msgReceives != 20 or exchanges[1].msgRoutes != 30 or
               exchanges[2].msgReceives != 10 or exchanges[2].msgRoutes != 10 or
               exchanges[3].msgReceives != 20 or exchanges[3].msgRoutes != 20):
            retries += 1
            self.failIfEqual(retries, 10,
                             "federation failed to route more msgs %d:%d %d:%d %d:%d %d:%d"
                             % (exchanges[0].msgReceives,
                                exchanges[0].msgRoutes,
                                exchanges[1].msgReceives,
                                exchanges[1].msgRoutes,
                                exchanges[2].msgReceives,
                                exchanges[2].msgRoutes,
                                exchanges[3].msgReceives,
                                exchanges[3].msgRoutes))
            sleep(1)
            for ex in exchanges:
                ex.update()

        # get exactly 10 msgs on B3 only
        for i in range(11, 21):
            msg = queue_3.get(timeout=5)
            self.assertEqual("Message_drp %d" % i, msg.body)

        try:
            extra = queue_3.get(timeout=1)
            self.fail("Got unexpected message in queue_3: " + extra.body)
        except Empty: None

        # cleanup

        self._brokers[3].client_session.exchange_unbind(queue="fedX1", exchange="fedX.direct", binding_key="spudboy")
        self._brokers[3].client_session.message_cancel(destination="f1")
        self._brokers[3].client_session.queue_delete(queue="fedX1")

        for _b in qmf.getObjects(_class="bridge"):
            result = _b.close()
            self.assertEqual(result.status, 0)

        for _l in qmf.getObjects(_class="link"):
            result = _l.close()
            self.assertEqual(result.status, 0)

        for _b in self._brokers:
            _b.client_session.exchange_delete(exchange="fedX.direct")

        self._teardown_brokers()

        self.verify_cleanup()

    def test_dynamic_topic_route_prop(self):
        """ Set up a tree of uni-directional routes across a topic exchange.
        Bind the same key to the same queues on the leaf nodes.  Verify a
        message sent with the routing key transverses the tree an arrives at
        each leaf.  Remove one leaf's queue, and verify that messages still
        reach the other leaf.

        Route Topology:

                    +---> B2  queue:"test-queue", binding key:"spud.*"
        B0 --> B1 --+
                    +---> B3  queue:"test-queue", binding key:"spud.*"
        """
        session = self.session

        # create the federation

        self.startQmf()
        qmf = self.qmf

        self._setup_brokers()

        # create exchange on each broker, and retrieve the corresponding
        # management object for that exchange

        exchanges=[]
        for _b in self._brokers:
            _b.client_session.exchange_declare(exchange="fedX.topic", type="topic")
            self.assertEqual(_b.client_session.exchange_query(name="fedX.topic").type,
                             "topic", "exchange_declare failed!")
            # pull the exchange out of qmf...
            retries = 0
            my_exchange = None
            while my_exchange is None:
                objs = qmf.getObjects(_broker=_b.qmf_broker, _class="exchange")
                for ooo in objs:
                    if ooo.name == "fedX.topic":
                        my_exchange = ooo
                        break
                if my_exchange is None:
                    retries += 1
                    self.failIfEqual(retries, 10,
                                     "QMF failed to find new exchange!")
                    sleep(1)
            exchanges.append(my_exchange)

        self.assertEqual(len(exchanges), len(self._brokers), "Exchange creation failed!")

        # connect B0 --> B1
        result = self._brokers[1].qmf_object.connect(self._brokers[0].host,
                                                     self._brokers[0].port,
                                                     False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0)

        # connect B1 --> B2
        result = self._brokers[2].qmf_object.connect(self._brokers[1].host,
                                                     self._brokers[1].port,
                                                     False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0)

        # connect B1 --> B3
        result = self._brokers[3].qmf_object.connect(self._brokers[1].host, 
                                                     self._brokers[1].port,
                                                     False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0)

        # for each link, bridge the "fedX.topic" exchanges:

        for _l in qmf.getObjects(_class="link"):
            # print("Link=%s:%s %s" % (_l.host, _l.port, str(_l.getBroker())))
            result = _l.bridge(False,  # durable
                                 "fedX.topic",  # src
                                 "fedX.topic",  # dst
                                 "",  # key
                                 "",  # tag
                                 "",  # excludes
                                 False, # srcIsQueue
                                 False, # srcIsLocal
                                 True,  # dynamic
                                 0,     # sync
                                 0)     # credit
            self.assertEqual(result.status, 0)

        # wait for the inter-broker links to become operational
        retries = 0
        operational = False
        while not operational:
            operational = True
            for _l in qmf.getObjects(_class="link"):
                #print("Link=%s:%s %s" % (_l.host, _l.port, str(_l.state)))
                if _l.state != "Operational":
                    operational = False
            if not operational:
                retries += 1
                self.failIfEqual(retries, 10,
                                 "inter-broker links failed to become operational.")
                sleep(1)

        # @todo - There is no way to determine when the bridge objects become
        # active.
        sleep(6)

        # create a queue on B2, bound to "spudboy"
        self._brokers[2].client_session.queue_declare(queue="fedX1", exclusive=True, auto_delete=True)
        self._brokers[2].client_session.exchange_bind(queue="fedX1", exchange="fedX.topic", binding_key="spud.*")

        # create a queue on B3, bound to "spudboy"
        self._brokers[3].client_session.queue_declare(queue="fedX1", exclusive=True, auto_delete=True)
        self._brokers[3].client_session.exchange_bind(queue="fedX1", exchange="fedX.topic", binding_key="spud.*")

        # subscribe to messages arriving on B2's queue
        self.subscribe(self._brokers[2].client_session, queue="fedX1", destination="f1")
        queue_2 = self._brokers[2].client_session.incoming("f1")

        # subscribe to messages arriving on B3's queue
        self.subscribe(self._brokers[3].client_session, queue="fedX1", destination="f1")
        queue_3 = self._brokers[3].client_session.incoming("f1")

        # wait until the binding key has propagated to each broker (twice at
        # broker B1).  Work backwards from binding brokers.

        binding_counts = [1, 2, 1, 1]
        self.assertEqual(len(binding_counts), len(exchanges), "Update Test!")
        for i in range(3,-1,-1):
            retries = 0
            exchanges[i].update()
            while exchanges[i].bindingCount < binding_counts[i]:
                retries += 1
                self.failIfEqual(retries, 10,
                                 "binding failed to propagate to broker %d"
                                 % i)
                sleep(3)
                exchanges[i].update()

        # send 10 msgs from B0
        for i in range(1, 11):
            dp = self._brokers[0].client_session.delivery_properties(routing_key="spud.boy")
            self._brokers[0].client_session.message_transfer(destination="fedX.topic", message=Message(dp, "Message_trp %d" % i))

        # wait for 10 messages to be forwarded from B0->B1,
        # 10 messages from B1->B2,
        # and 10 messages from B1->B3
        retries = 0
        for ex in exchanges:
            ex.update()
        while (exchanges[0].msgReceives != 10 or exchanges[0].msgRoutes != 10 or
               exchanges[1].msgReceives != 10 or exchanges[1].msgRoutes != 20 or
               exchanges[2].msgReceives != 10 or exchanges[2].msgRoutes != 10 or
               exchanges[3].msgReceives != 10 or exchanges[3].msgRoutes != 10):
            retries += 1
            self.failIfEqual(retries, 10,
                             "federation failed to route msgs %d:%d %d:%d %d:%d %d:%d"
                             % (exchanges[0].msgReceives,
                                exchanges[0].msgRoutes,
                                exchanges[1].msgReceives,
                                exchanges[1].msgRoutes,
                                exchanges[2].msgReceives,
                                exchanges[2].msgRoutes,
                                exchanges[3].msgReceives,
                                exchanges[3].msgRoutes))
            sleep(1)
            for ex in exchanges:
                ex.update()

        # get exactly 10 msgs on B2 and B3
        for i in range(1, 11):
            msg = queue_2.get(timeout=5)
            self.assertEqual("Message_trp %d" % i, msg.body)
            msg = queue_3.get(timeout=5)
            self.assertEqual("Message_trp %d" % i, msg.body)

        try:
            extra = queue_2.get(timeout=1)
            self.fail("Got unexpected message in queue_2: " + extra.body)
        except Empty: None

        try:
            extra = queue_3.get(timeout=1)
            self.fail("Got unexpected message in queue_3: " + extra.body)
        except Empty: None

        # tear down the queue on B2
        self._brokers[2].client_session.exchange_unbind(queue="fedX1", exchange="fedX.topic", binding_key="spud.*")
        self._brokers[2].client_session.message_cancel(destination="f1")
        self._brokers[2].client_session.queue_delete(queue="fedX1")

        # wait for the binding count on B1 to drop from 2 to 1
        retries = 0
        exchanges[1].update()
        while exchanges[1].bindingCount != 1:
            retries += 1
            self.failIfEqual(retries, 10,
                             "unbinding failed to propagate to broker B1: %d"
                             % exchanges[1].bindingCount)
            sleep(1)
            exchanges[1].update()

        # send 10 msgs from B0
        for i in range(11, 21):
            dp = self._brokers[0].client_session.delivery_properties(routing_key="spud.boy")
            self._brokers[0].client_session.message_transfer(destination="fedX.topic", message=Message(dp, "Message_trp %d" % i))

        # verify messages are forwarded to B3 only
        retries = 0
        for ex in exchanges:
            ex.update()
        while (exchanges[0].msgReceives != 20 or exchanges[0].msgRoutes != 20 or
               exchanges[1].msgReceives != 20 or exchanges[1].msgRoutes != 30 or
               exchanges[2].msgReceives != 10 or exchanges[2].msgRoutes != 10 or
               exchanges[3].msgReceives != 20 or exchanges[3].msgRoutes != 20):
            retries += 1
            self.failIfEqual(retries, 10,
                             "federation failed to route more msgs %d:%d %d:%d %d:%d %d:%d"
                             % (exchanges[0].msgReceives,
                                exchanges[0].msgRoutes,
                                exchanges[1].msgReceives,
                                exchanges[1].msgRoutes,
                                exchanges[2].msgReceives,
                                exchanges[2].msgRoutes,
                                exchanges[3].msgReceives,
                                exchanges[3].msgRoutes))
            sleep(1)
            for ex in exchanges:
                ex.update()

        # get exactly 10 msgs on B3 only
        for i in range(11, 21):
            msg = queue_3.get(timeout=5)
            self.assertEqual("Message_trp %d" % i, msg.body)

        try:
            extra = queue_3.get(timeout=1)
            self.fail("Got unexpected message in queue_3: " + extra.body)
        except Empty: None

        # cleanup

        self._brokers[3].client_session.exchange_unbind(queue="fedX1", exchange="fedX.topic", binding_key="spud.*")
        self._brokers[3].client_session.message_cancel(destination="f1")
        self._brokers[3].client_session.queue_delete(queue="fedX1")

        for _b in qmf.getObjects(_class="bridge"):
            result = _b.close()
            self.assertEqual(result.status, 0)

        for _l in qmf.getObjects(_class="link"):
            result = _l.close()
            self.assertEqual(result.status, 0)

        for _b in self._brokers:
            _b.client_session.exchange_delete(exchange="fedX.topic")

        self._teardown_brokers()

        self.verify_cleanup()


    def test_dynamic_fanout_route_prop(self):
        """ Set up a tree of uni-directional routes across a fanout exchange.
        Bind the same key to the same queues on the leaf nodes.  Verify a
        message sent with the routing key transverses the tree an arrives at
        each leaf.  Remove one leaf's queue, and verify that messages still
        reach the other leaf.

        Route Topology:

                    +---> B2  queue:"test-queue", binding key:"spud.*"
        B0 --> B1 --+
                    +---> B3  queue:"test-queue", binding key:"spud.*"
        """
        session = self.session

        # create the federation

        self.startQmf()
        qmf = self.qmf

        self._setup_brokers()

        # create fanout exchange on each broker, and retrieve the corresponding
        # management object for that exchange

        exchanges=[]
        for _b in self._brokers:
            _b.client_session.exchange_declare(exchange="fedX.fanout", type="fanout")
            self.assertEqual(_b.client_session.exchange_query(name="fedX.fanout").type,
                             "fanout", "exchange_declare failed!")
            # pull the exchange out of qmf...
            retries = 0
            my_exchange = None
            while my_exchange is None:
                objs = qmf.getObjects(_broker=_b.qmf_broker, _class="exchange")
                for ooo in objs:
                    if ooo.name == "fedX.fanout":
                        my_exchange = ooo
                        break
                if my_exchange is None:
                    retries += 1
                    self.failIfEqual(retries, 10,
                                     "QMF failed to find new exchange!")
                    sleep(1)
            exchanges.append(my_exchange)

        self.assertEqual(len(exchanges), len(self._brokers), "Exchange creation failed!")

        # connect B0 --> B1
        result = self._brokers[1].qmf_object.connect(self._brokers[0].host,
                                                     self._brokers[0].port,
                                                     False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0)

        # connect B1 --> B2
        result = self._brokers[2].qmf_object.connect(self._brokers[1].host,
                                                     self._brokers[1].port,
                                                     False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0)

        # connect B1 --> B3
        result = self._brokers[3].qmf_object.connect(self._brokers[1].host, 
                                                     self._brokers[1].port,
                                                     False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0)

        # for each link, bridge the "fedX.fanout" exchanges:

        for _l in qmf.getObjects(_class="link"):
            # print("Link=%s:%s %s" % (_l.host, _l.port, str(_l.getBroker())))
            result = _l.bridge(False,  # durable
                                 "fedX.fanout",  # src
                                 "fedX.fanout",  # dst
                                 "",  # key
                                 "",  # tag
                                 "",  # excludes
                                 False, # srcIsQueue
                                 False, # srcIsLocal
                                 True,  # dynamic
                                 0,     # sync
                                 0)     # credit
            self.assertEqual(result.status, 0)

        # wait for the inter-broker links to become operational
        retries = 0
        operational = False
        while not operational:
            operational = True
            for _l in qmf.getObjects(_class="link"):
                # print("Link=%s:%s %s" % (_l.host, _l.port, str(_l.state)))
                if _l.state != "Operational":
                    operational = False
            if not operational:
                retries += 1
                self.failIfEqual(retries, 10,
                                 "inter-broker links failed to become operational.")
                sleep(1)

        # @todo - There is no way to determine when the bridge objects become
        # active.
        sleep(6)

        # create a queue on B2, bound to the exchange
        self._brokers[2].client_session.queue_declare(queue="fedX1", exclusive=True, auto_delete=True)
        self._brokers[2].client_session.exchange_bind(queue="fedX1", exchange="fedX.fanout")

        # create a queue on B3, bound to the exchange
        self._brokers[3].client_session.queue_declare(queue="fedX1", exclusive=True, auto_delete=True)
        self._brokers[3].client_session.exchange_bind(queue="fedX1", exchange="fedX.fanout")

        # subscribe to messages arriving on B2's queue
        self.subscribe(self._brokers[2].client_session, queue="fedX1", destination="f1")
        queue_2 = self._brokers[2].client_session.incoming("f1")

        # subscribe to messages arriving on B3's queue
        self.subscribe(self._brokers[3].client_session, queue="fedX1", destination="f1")
        queue_3 = self._brokers[3].client_session.incoming("f1")

        # wait until the binding key has propagated to each broker (twice at
        # broker B1).  Work backwards from binding brokers.

        binding_counts = [1, 2, 1, 1]
        self.assertEqual(len(binding_counts), len(exchanges), "Update Test!")
        for i in range(3,-1,-1):
            retries = 0
            exchanges[i].update()
            while exchanges[i].bindingCount < binding_counts[i]:
                retries += 1
                self.failIfEqual(retries, 10,
                                 "binding failed to propagate to broker %d"
                                 % i)
                sleep(3)
                exchanges[i].update()

        # send 10 msgs from B0
        for i in range(1, 11):
            dp = self._brokers[0].client_session.delivery_properties()
            self._brokers[0].client_session.message_transfer(destination="fedX.fanout", message=Message(dp, "Message_frp %d" % i))

        # wait for 10 messages to be forwarded from B0->B1,
        # 10 messages from B1->B2,
        # and 10 messages from B1->B3
        retries = 0
        for ex in exchanges:
            ex.update()
        while (exchanges[0].msgReceives != 10 or exchanges[0].msgRoutes != 10 or
               exchanges[1].msgReceives != 10 or exchanges[1].msgRoutes != 20 or
               exchanges[2].msgReceives != 10 or exchanges[2].msgRoutes != 10 or
               exchanges[3].msgReceives != 10 or exchanges[3].msgRoutes != 10):
            retries += 1
            self.failIfEqual(retries, 10,
                             "federation failed to route msgs %d:%d %d:%d %d:%d %d:%d"
                             % (exchanges[0].msgReceives,
                                exchanges[0].msgRoutes,
                                exchanges[1].msgReceives,
                                exchanges[1].msgRoutes,
                                exchanges[2].msgReceives,
                                exchanges[2].msgRoutes,
                                exchanges[3].msgReceives,
                                exchanges[3].msgRoutes))
            sleep(1)
            for ex in exchanges:
                ex.update()

        # get exactly 10 msgs on B2 and B3
        for i in range(1, 11):
            msg = queue_2.get(timeout=5)
            self.assertEqual("Message_frp %d" % i, msg.body)
            msg = queue_3.get(timeout=5)
            self.assertEqual("Message_frp %d" % i, msg.body)

        try:
            extra = queue_2.get(timeout=1)
            self.fail("Got unexpected message in queue_2: " + extra.body)
        except Empty: None

        try:
            extra = queue_3.get(timeout=1)
            self.fail("Got unexpected message in queue_3: " + extra.body)
        except Empty: None

        # tear down the queue on B2
        self._brokers[2].client_session.exchange_unbind(queue="fedX1", exchange="fedX.fanout")
        self._brokers[2].client_session.message_cancel(destination="f1")
        self._brokers[2].client_session.queue_delete(queue="fedX1")

        # wait for the binding count on B1 to drop from 2 to 1
        retries = 0
        exchanges[1].update()
        while exchanges[1].bindingCount != 1:
            retries += 1
            self.failIfEqual(retries, 10,
                             "unbinding failed to propagate to broker B1: %d"
                             % exchanges[1].bindingCount)
            sleep(1)
            exchanges[1].update()

        # send 10 msgs from B0
        for i in range(11, 21):
            dp = self._brokers[0].client_session.delivery_properties()
            self._brokers[0].client_session.message_transfer(destination="fedX.fanout", message=Message(dp, "Message_frp %d" % i))

        # verify messages are forwarded to B3 only
        retries = 0
        for ex in exchanges:
            ex.update()
        while (exchanges[0].msgReceives != 20 or exchanges[0].msgRoutes != 20 or
               exchanges[1].msgReceives != 20 or exchanges[1].msgRoutes != 30 or
               exchanges[2].msgReceives != 10 or exchanges[2].msgRoutes != 10 or
               exchanges[3].msgReceives != 20 or exchanges[3].msgRoutes != 20):
            retries += 1
            self.failIfEqual(retries, 10,
                             "federation failed to route more msgs %d:%d %d:%d %d:%d %d:%d"
                             % (exchanges[0].msgReceives,
                                exchanges[0].msgRoutes,
                                exchanges[1].msgReceives,
                                exchanges[1].msgRoutes,
                                exchanges[2].msgReceives,
                                exchanges[2].msgRoutes,
                                exchanges[3].msgReceives,
                                exchanges[3].msgRoutes))
            sleep(1)
            for ex in exchanges:
                ex.update()

        # get exactly 10 msgs on B3 only
        for i in range(11, 21):
            msg = queue_3.get(timeout=5)
            self.assertEqual("Message_frp %d" % i, msg.body)

        try:
            extra = queue_3.get(timeout=1)
            self.fail("Got unexpected message in queue_3: " + extra.body)
        except Empty: None

        # cleanup

        self._brokers[3].client_session.exchange_unbind(queue="fedX1", exchange="fedX.fanout")
        self._brokers[3].client_session.message_cancel(destination="f1")
        self._brokers[3].client_session.queue_delete(queue="fedX1")

        for _b in qmf.getObjects(_class="bridge"):
            result = _b.close()
            self.assertEqual(result.status, 0)

        for _l in qmf.getObjects(_class="link"):
            result = _l.close()
            self.assertEqual(result.status, 0)

        for _b in self._brokers:
            _b.client_session.exchange_delete(exchange="fedX.fanout")

        self._teardown_brokers()

        self.verify_cleanup()


    def getProperty(self, msg, name):
        for h in msg.headers:
            if hasattr(h, name): return getattr(h, name)
        return None            

    def getAppHeader(self, msg, name):
        headers = self.getProperty(msg, "application_headers")
        if headers:
            return headers[name]
        return None

    def test_dynamic_topic_bounce(self):
        """ Bounce the connection between federated Topic Exchanges.
        """
        class Params:
            def exchange_type(self): return "topic"
            def bind_queue(self, ssn, qname, ename):
                ssn.exchange_bind(queue=qname, exchange=ename,
                                  binding_key="spud.*")
            def unbind_queue(self, ssn, qname, ename):
                ssn.exchange_unbind(queue=qname, exchange=ename, binding_key="spud.*")
            def delivery_properties(self, ssn):
                return  ssn.delivery_properties(routing_key="spud.boy")

        self.generic_dynamic_bounce_test(Params())

    def test_dynamic_direct_bounce(self):
        """ Bounce the connection between federated Direct Exchanges.
        """
        class Params:
            def exchange_type(self): return "direct"
            def bind_queue(self, ssn, qname, ename):
                ssn.exchange_bind(queue=qname, exchange=ename, binding_key="spud")
            def unbind_queue(self, ssn, qname, ename):
                ssn.exchange_unbind(queue=qname, exchange=ename, binding_key="spud")
            def delivery_properties(self, ssn):
                return  ssn.delivery_properties(routing_key="spud")
        self.generic_dynamic_bounce_test(Params())

    def test_dynamic_fanout_bounce(self):
        """ Bounce the connection between federated Fanout Exchanges.
        """
        class Params:
            def exchange_type(self): return "fanout"
            def bind_queue(self, ssn, qname, ename):
                ssn.exchange_bind(queue=qname, exchange=ename)
            def unbind_queue(self, ssn, qname, ename):
                ssn.exchange_unbind(queue=qname, exchange=ename)
            def delivery_properties(self, ssn):
                return  ssn.delivery_properties(routing_key="spud")
        self.generic_dynamic_bounce_test(Params())

    def test_dynamic_headers_bounce(self):
        """ Bounce the connection between federated Headers Exchanges.
        """
        class Params:
            def exchange_type(self): return "headers"
            def bind_queue(self, ssn, qname, ename):
                ssn.exchange_bind(queue=qname, exchange=ename,
                                  binding_key="spud", arguments={'x-match':'any', 'class':'first'})
            def unbind_queue(self, ssn, qname, ename):
                ssn.exchange_unbind(queue=qname, exchange=ename, binding_key="spud")
            def delivery_properties(self, ssn):
                return  ssn.message_properties(application_headers={'class':'first'})
        ## @todo KAG - re-enable once federation bugs with headers exchanges
        ## are fixed.
        #self.generic_dynamic_bounce_test(Params())
        return


    def generic_dynamic_bounce_test(self, params):
        """ Verify that a federated broker can maintain a binding to a local
        queue using the same key as a remote binding.  Destroy and reconnect
        the federation link, and verify routes are restored correctly.
        See QPID-3170.
        Topology:

        Queue1 <---"Key"---B0<==[Federated Exchange]==>B1---"Key"--->Queue2
        """
        session = self.session

        # create the federation

        self.startQmf()
        qmf = self.qmf

        self._setup_brokers()

        # create exchange on each broker, and retrieve the corresponding
        # management object for that exchange

        exchanges=[]
        for _b in self._brokers[0:2]:
            _b.client_session.exchange_declare(exchange="fedX", type=params.exchange_type())
            self.assertEqual(_b.client_session.exchange_query(name="fedX").type,
                             params.exchange_type(), "exchange_declare failed!")
            # pull the exchange out of qmf...
            retries = 0
            my_exchange = None
            timeout = time() + 10
            while my_exchange is None and time() <= timeout:
                objs = qmf.getObjects(_broker=_b.qmf_broker, _class="exchange")
                for ooo in objs:
                    if ooo.name == "fedX":
                        my_exchange = ooo
                        break
            if my_exchange is None:
                self.fail("QMF failed to find new exchange!")
            exchanges.append(my_exchange)

        #
        # on each broker, create a local queue bound to the exchange with the
        # same key value.
        #

        self._brokers[0].client_session.queue_declare(queue="fedX1", exclusive=True, auto_delete=True)
        params.bind_queue(self._brokers[0].client_session, "fedX1", "fedX")
        self.subscribe(self._brokers[0].client_session, queue="fedX1", destination="f1")
        queue_0 = self._brokers[0].client_session.incoming("f1")

        self._brokers[1].client_session.queue_declare(queue="fedX1", exclusive=True, auto_delete=True)
        params.bind_queue(self._brokers[1].client_session, "fedX1", "fedX")
        self.subscribe(self._brokers[1].client_session, queue="fedX1", destination="f1")
        queue_1 = self._brokers[1].client_session.incoming("f1")

        # now federate the two brokers

        # connect B0 --> B1
        result = self._brokers[1].qmf_object.connect(self._brokers[0].host,
                                                     self._brokers[0].port,
                                                     False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0)

        # connect B1 --> B0
        result = self._brokers[0].qmf_object.connect(self._brokers[1].host,
                                                     self._brokers[1].port,
                                                     False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0)

        # for each link, bridge the "fedX" exchanges:

        for _l in qmf.getObjects(_class="link"):
            # print("Link=%s:%s %s" % (_l.host, _l.port, str(_l.getBroker())))
            result = _l.bridge(False,  # durable
                                 "fedX",  # src
                                 "fedX",  # dst
                                 "",  # key
                                 "",  # tag
                                 "",  # excludes
                                 False, # srcIsQueue
                                 False, # srcIsLocal
                                 True,  # dynamic
                                 0,     # sync
                                 0)     # credit
            self.assertEqual(result.status, 0)

        # wait for all the inter-broker links to become operational
        operational = False
        timeout = time() + 10
        while not operational and time() <= timeout:
            operational = True
            for _l in qmf.getObjects(_class="link"):
                #print("Link=%s:%s %s" % (_l.host, _l.port, str(_l.state)))
                if _l.state != "Operational":
                    operational = False
        self.failUnless(operational, "inter-broker links failed to become operational.")

        # @todo - There is no way to determine when the bridge objects become
        # active.

        # wait until the binding key has propagated to each broker - each
        # broker should see 2 bindings (1 local, 1 remote)

        binding_counts = [2, 2]
        self.assertEqual(len(binding_counts), len(exchanges), "Update Test!")
        for i in range(2):
            exchanges[i].update()
            timeout = time() + 10
            while exchanges[i].bindingCount < binding_counts[i] and time() <= timeout:
                exchanges[i].update()
            self.failUnless(exchanges[i].bindingCount == binding_counts[i])

        # send 10 msgs to B0
        for i in range(1, 11):
            # dp = self._brokers[0].client_session.delivery_properties(routing_key=params.routing_key())
            dp = params.delivery_properties(self._brokers[0].client_session)
            self._brokers[0].client_session.message_transfer(destination="fedX", message=Message(dp, "Message_trp %d" % i))

        # get exactly 10 msgs on B0's local queue and B1's queue
        for i in range(1, 11):
            try:
                msg = queue_0.get(timeout=5)
                self.assertEqual("Message_trp %d" % i, msg.body)
                msg = queue_1.get(timeout=5)
                self.assertEqual("Message_trp %d" % i, msg.body)
            except Empty:
                self.fail("Only got %d msgs - expected 10" % i)
        try:
            extra = queue_0.get(timeout=1)
            self.fail("Got unexpected message in queue_0: " + extra.body)
        except Empty: None

        try:
            extra = queue_1.get(timeout=1)
            self.fail("Got unexpected message in queue_1: " + extra.body)
        except Empty: None

        #
        # Tear down the bridges between the two exchanges, then wait
        # for the bindings to be cleaned up
        #

        for _b in qmf.getObjects(_class="bridge"):
            result = _b.close()
            self.assertEqual(result.status, 0)

        binding_counts = [1, 1]
        self.assertEqual(len(binding_counts), len(exchanges), "Update Test!")
        for i in range(2):
            exchanges[i].update()
            timeout = time() + 10
            while exchanges[i].bindingCount != binding_counts[i] and time() <= timeout:
                exchanges[i].update()
            self.failUnless(exchanges[i].bindingCount == binding_counts[i])

        #
        # restore the bridges between the two exchanges, and wait for the
        # bindings to propagate.
        #

        for _l in qmf.getObjects(_class="link"):
            # print("Link=%s:%s %s" % (_l.host, _l.port, str(_l.getBroker())))
            result = _l.bridge(False,  # durable
                                 "fedX",  # src
                                 "fedX",  # dst
                                 "",  # key
                                 "",  # tag
                                 "",  # excludes
                                 False, # srcIsQueue
                                 False, # srcIsLocal
                                 True,  # dynamic
                                 0,     # sync
                                 0)     # credit
            self.assertEqual(result.status, 0)

        binding_counts = [2, 2]
        self.assertEqual(len(binding_counts), len(exchanges), "Update Test!")
        for i in range(2):
            exchanges[i].update()
            timeout = time() + 10
            while exchanges[i].bindingCount != binding_counts[i] and time() <= timeout:
                exchanges[i].update()
            self.failUnless(exchanges[i].bindingCount == binding_counts[i])

        #
        # verify traffic flows correctly
        #

        for i in range(1, 11):
            #dp = self._brokers[1].client_session.delivery_properties(routing_key=params.routing_key())
            dp = params.delivery_properties(self._brokers[1].client_session)
            self._brokers[1].client_session.message_transfer(destination="fedX", message=Message(dp, "Message_trp %d" % i))

        # get exactly 10 msgs on B0's queue and B1's queue
        for i in range(1, 11):
            try:
                msg = queue_0.get(timeout=5)
                self.assertEqual("Message_trp %d" % i, msg.body)
                msg = queue_1.get(timeout=5)
                self.assertEqual("Message_trp %d" % i, msg.body)
            except Empty:
                self.fail("Only got %d msgs - expected 10" % i)
        try:
            extra = queue_0.get(timeout=1)
            self.fail("Got unexpected message in queue_0: " + extra.body)
        except Empty: None

        try:
            extra = queue_1.get(timeout=1)
            self.fail("Got unexpected message in queue_1: " + extra.body)
        except Empty: None


        #
        # cleanup
        #
        params.unbind_queue(self._brokers[0].client_session, "fedX1", "fedX")
        self._brokers[0].client_session.message_cancel(destination="f1")
        self._brokers[0].client_session.queue_delete(queue="fedX1")

        params.unbind_queue(self._brokers[1].client_session, "fedX1", "fedX")
        self._brokers[1].client_session.message_cancel(destination="f1")
        self._brokers[1].client_session.queue_delete(queue="fedX1")

        for _b in qmf.getObjects(_class="bridge"):
            result = _b.close()
            self.assertEqual(result.status, 0)

        for _l in qmf.getObjects(_class="link"):
            result = _l.close()
            self.assertEqual(result.status, 0)

        for _b in self._brokers[0:2]:
            _b.client_session.exchange_delete(exchange="fedX")

        self._teardown_brokers()

        self.verify_cleanup()


    def test_multilink_direct(self):
        """ Verify that two distinct links can be created between federated
        brokers.
        """
        self.startQmf()
        qmf = self.qmf
        self._setup_brokers()
        src_broker = self._brokers[0]
        dst_broker = self._brokers[1]

        # create a direct exchange on each broker
        for _b in [src_broker, dst_broker]:
            _b.client_session.exchange_declare(exchange="fedX.direct", type="direct")
            self.assertEqual(_b.client_session.exchange_query(name="fedX.direct").type,
                             "direct", "exchange_declare failed!")

        # create destination queues
        for _q in [("HiQ", "high"), ("MedQ", "medium"), ("LoQ", "low")]:
            dst_broker.client_session.queue_declare(queue=_q[0], auto_delete=True)
            dst_broker.client_session.exchange_bind(queue=_q[0], exchange="fedX.direct", binding_key=_q[1])

        # create two connections, one for high priority traffic
        for _q in ["HiPri", "Traffic"]:
            result = dst_broker.qmf_object.create("link", _q,
                                                  {"host":src_broker.host,
                                                   "port":src_broker.port},
                                                  False)
            self.assertEqual(result.status, 0);

        links = qmf.getObjects(_broker=dst_broker.qmf_broker, _class="link")
        for _l in links:
            if _l.name == "HiPri":
                hi_link = _l
            elif _l.name == "Traffic":
                data_link = _l
            else:
                self.fail("Unexpected Link found: " + _l.name)

        # now create a route for messages sent with key "high" to use the
        # hi_link
        result = dst_broker.qmf_object.create("bridge", "HiPriBridge",
                                              {"link":hi_link.name,
                                               "src":"fedX.direct",
                                               "dest":"fedX.direct",
                                               "key":"high"}, False)
        self.assertEqual(result.status, 0);


        # create routes for the "medium" and "low" links to use the normal
        # data_link
        for _b in [("MediumBridge", "medium"), ("LowBridge", "low")]:
            result = dst_broker.qmf_object.create("bridge", _b[0],
                                                  {"link":data_link.name,
                                                   "src":"fedX.direct",
                                                   "dest":"fedX.direct",
                                                   "key":_b[1]}, False)
            self.assertEqual(result.status, 0);

        # now wait for the links to become operational
        for _l in [hi_link, data_link]:
            expire_time = time() + 30
            while _l.state != "Operational" and time() < expire_time:
                _l.update()
            self.assertEqual(_l.state, "Operational", "Link failed to become operational")

        # verify each link uses a different connection
        self.assertNotEqual(hi_link.connectionRef, data_link.connectionRef,
                            "Different links using the same connection")

        hi_conn = qmf.getObjects(_broker=dst_broker.qmf_broker,
                                 _objectId=hi_link.connectionRef)[0]
        data_conn = qmf.getObjects(_broker=dst_broker.qmf_broker,
                                 _objectId=data_link.connectionRef)[0]


        # send hi data, verify only goes over hi link

        r_ssn = dst_broker.connection.session()
        hi_receiver = r_ssn.receiver("HiQ");
        med_receiver = r_ssn.receiver("MedQ");
        low_receiver = r_ssn.receiver("LoQ");

        for _c in [hi_conn, data_conn]:
            _c.update()
            self.assertEqual(_c.msgsToClient, 0, "Unexpected messages received")

        s_ssn = src_broker.connection.session()
        hi_sender = s_ssn.sender("fedX.direct/high")
        med_sender = s_ssn.sender("fedX.direct/medium")
        low_sender = s_ssn.sender("fedX.direct/low")

        try:
            hi_sender.send(qpid.messaging.Message(content="hi priority"))
            msg = hi_receiver.fetch(timeout=10)
            r_ssn.acknowledge()
            self.assertEqual(msg.content, "hi priority");
        except:
            self.fail("Hi Pri message failure")

        hi_conn.update()
        data_conn.update()
        self.assertEqual(hi_conn.msgsToClient, 1, "Expected 1 hi pri message")
        self.assertEqual(data_conn.msgsToClient, 0, "Expected 0 data messages")

        # send low and medium, verify it does not go over hi link

        try:
            med_sender.send(qpid.messaging.Message(content="medium priority"))
            msg = med_receiver.fetch(timeout=10)
            r_ssn.acknowledge()
            self.assertEqual(msg.content, "medium priority");
        except:
            self.fail("Medium Pri message failure")

        hi_conn.update()
        data_conn.update()
        self.assertEqual(hi_conn.msgsToClient, 1, "Expected 1 hi pri message")
        self.assertEqual(data_conn.msgsToClient, 1, "Expected 1 data message")

        try:
            low_sender.send(qpid.messaging.Message(content="low priority"))
            msg = low_receiver.fetch(timeout=10)
            r_ssn.acknowledge()
            self.assertEqual(msg.content, "low priority");
        except:
            self.fail("Low Pri message failure")

        hi_conn.update()
        data_conn.update()
        self.assertEqual(hi_conn.msgsToClient, 1, "Expected 1 hi pri message")
        self.assertEqual(data_conn.msgsToClient, 2, "Expected 2 data message")

        # cleanup

        for _b in qmf.getObjects(_broker=dst_broker.qmf_broker,_class="bridge"):
            result = _b.close()
            self.assertEqual(result.status, 0)

        for _l in qmf.getObjects(_broker=dst_broker.qmf_broker,_class="link"):
            result = _l.close()
            self.assertEqual(result.status, 0)

        for _q in [("HiQ", "high"), ("MedQ", "medium"), ("LoQ", "low")]:
            dst_broker.client_session.exchange_unbind(queue=_q[0], exchange="fedX.direct", binding_key=_q[1])
            dst_broker.client_session.queue_delete(queue=_q[0])

        for _b in [src_broker, dst_broker]:
            _b.client_session.exchange_delete(exchange="fedX.direct")

        self._teardown_brokers()

        self.verify_cleanup()


    def test_multilink_shared_queue(self):
        """ Verify that two distinct links can be created between federated
        brokers.
        """
        self.startQmf()
        qmf = self.qmf
        self._setup_brokers()
        src_broker = self._brokers[0]
        dst_broker = self._brokers[1]

        # create a topic exchange on the destination broker
        dst_broker.client_session.exchange_declare(exchange="fedX.topic", type="topic")
        self.assertEqual(dst_broker.client_session.exchange_query(name="fedX.topic").type,
                         "topic", "exchange_declare failed!")

        # create a destination queue
        dst_broker.client_session.queue_declare(queue="destQ", auto_delete=True)
        dst_broker.client_session.exchange_bind(queue="destQ", exchange="fedX.topic", binding_key="srcQ")

        # create a single source queue
        src_broker.client_session.queue_declare(queue="srcQ", auto_delete=True)

        # create two connections
        for _q in ["Link1", "Link2"]:
            result = dst_broker.qmf_object.create("link", _q,
                                                  {"host":src_broker.host,
                                                   "port":src_broker.port},
                                                  False)
            self.assertEqual(result.status, 0);

        links = qmf.getObjects(_broker=dst_broker.qmf_broker, _class="link")
        self.assertEqual(len(links), 2)

        # now create two "parallel" queue routes from the source queue to the
        # destination exchange.
        result = dst_broker.qmf_object.create("bridge", "Bridge1",
                                              {"link":"Link1",
                                               "src":"srcQ",
                                               "dest":"fedX.topic",
                                               "srcIsQueue": True},
                                              False)
        self.assertEqual(result.status, 0);
        result = dst_broker.qmf_object.create("bridge", "Bridge2",
                                              {"link":"Link2",
                                               "src":"srcQ",
                                               "dest":"fedX.topic",
                                               "srcIsQueue": True},
                                              False)
        self.assertEqual(result.status, 0);


        # now wait for the links to become operational
        for _l in links:
            expire_time = time() + 30
            while _l.state != "Operational" and time() < expire_time:
                _l.update()
            self.assertEqual(_l.state, "Operational", "Link failed to become operational")

        # verify each link uses a different connection
        self.assertNotEqual(links[0].connectionRef, links[1].connectionRef,
                            "Different links using the same connection")

        conn1 = qmf.getObjects(_broker=dst_broker.qmf_broker,
                               _objectId=links[0].connectionRef)[0]
        conn2 = qmf.getObjects(_broker=dst_broker.qmf_broker,
                               _objectId=links[1].connectionRef)[0]

        # verify messages sent to the queue are pulled by each connection

        r_ssn = dst_broker.connection.session()
        receiver = r_ssn.receiver("destQ");

        for _c in [conn1, conn2]:
            _c.update()
            self.assertEqual(_c.msgsToClient, 0, "Unexpected messages received")

        s_ssn = src_broker.connection.session()
        sender = s_ssn.sender("srcQ")

        try:
            for x in range(5):
                sender.send(qpid.messaging.Message(content="hello"))
            for x in range(5):
                msg = receiver.fetch(timeout=10)
                self.assertEqual(msg.content, "hello");
                r_ssn.acknowledge()
        except:
            self.fail("Message failure")

        # expect messages to be split over each connection.
        conn1.update()
        conn2.update()
        self.assertNotEqual(conn1.msgsToClient, 0, "No messages sent")
        self.assertNotEqual(conn2.msgsToClient, 0, "No messages sent")
        self.assertEqual(conn2.msgsToClient + conn1.msgsToClient, 5,
                         "Expected 5 messages total")

        for _b in qmf.getObjects(_broker=dst_broker.qmf_broker,_class="bridge"):
            result = _b.close()
            self.assertEqual(result.status, 0)

        for _l in qmf.getObjects(_broker=dst_broker.qmf_broker,_class="link"):
            result = _l.close()
            self.assertEqual(result.status, 0)

        dst_broker.client_session.exchange_unbind(queue="destQ", exchange="fedX.topic", binding_key="srcQ")
        dst_broker.client_session.exchange_delete(exchange="fedX.topic")

        self._teardown_brokers()

        self.verify_cleanup()


    def test_dynamic_direct_shared_queue(self):
        """
        Route Topology:

               +<--- B1
        B0 <---+<--- B2
               +<--- B3
        """
        session = self.session

        # create the federation

        self.startQmf()
        qmf = self.qmf

        self._setup_brokers()

        # create direct exchange on each broker, and retrieve the corresponding
        # management object for that exchange

        exchanges=[]
        for _b in self._brokers:
            _b.client_session.exchange_declare(exchange="fedX.direct", type="direct")
            self.assertEqual(_b.client_session.exchange_query(name="fedX.direct").type,
                             "direct", "exchange_declare failed!")
            # pull the exchange out of qmf...
            retries = 0
            my_exchange = None
            while my_exchange is None:
                objs = qmf.getObjects(_broker=_b.qmf_broker, _class="exchange")
                for ooo in objs:
                    if ooo.name == "fedX.direct":
                        my_exchange = ooo
                        break
                if my_exchange is None:
                    retries += 1
                    self.failIfEqual(retries, 10,
                                     "QMF failed to find new exchange!")
                    sleep(1)
            exchanges.append(my_exchange)

        self.assertEqual(len(exchanges), len(self._brokers), "Exchange creation failed!")

        # Create 2 links per each source broker (1,2,3) to the downstream
        # broker 0:
        for _b in range(1,4):
            for _l in ["dynamic", "queue"]:
                result = self._brokers[0].qmf_object.create( "link",
                                                             "Link-%d-%s" % (_b, _l),
                                                             {"host":self._brokers[_b].host,
                                                              "port":self._brokers[_b].port}, False)
                self.assertEqual(result.status, 0)

            # create queue on source brokers for use by the dynamic route
            self._brokers[_b].client_session.queue_declare(queue="fedSrcQ", exclusive=False, auto_delete=True)

        for _l in range(1,4):
            # for each dynamic link, create a dynamic bridge for the "fedX.direct"
            # exchanges, using the fedSrcQ on each upstream source broker
            result = self._brokers[0].qmf_object.create("bridge",
                                                        "Bridge-%d-dynamic" % _l,
                                                        {"link":"Link-%d-dynamic" % _l,
                                                         "src":"fedX.direct",
                                                         "dest":"fedX.direct",
                                                         "dynamic":True,
                                                         "queue":"fedSrcQ"}, False)
            self.assertEqual(result.status, 0)

            # create a queue route that shares the queue used by the dynamic route
            result = self._brokers[0].qmf_object.create("bridge",
                                                        "Bridge-%d-queue" % _l,
                                                        {"link":"Link-%d-queue" % _l,
                                                         "src":"fedSrcQ",
                                                         "dest":"fedX.direct",
                                                         "srcIsQueue":True}, False)
            self.assertEqual(result.status, 0)


        # wait for the inter-broker links to become operational
        retries = 0
        operational = False
        while not operational:
            operational = True
            for _l in qmf.getObjects(_class="link"):
                #print("Link=%s:%s %s" % (_l.host, _l.port, str(_l.state)))
                if _l.state != "Operational":
                    operational = False
            if not operational:
                retries += 1
                self.failIfEqual(retries, 10,
                                 "inter-broker links failed to become operational.")
                sleep(1)

        # @todo - There is no way to determine when the bridge objects become
        # active.  Hopefully, this is long enough!
        sleep(6)

        # create a queue on B0, bound to "spudboy"
        self._brokers[0].client_session.queue_declare(queue="DestQ", exclusive=True, auto_delete=True)
        self._brokers[0].client_session.exchange_bind(queue="DestQ", exchange="fedX.direct", binding_key="spudboy")

        # subscribe to messages arriving on B2's queue
        self.subscribe(self._brokers[0].client_session, queue="DestQ", destination="f1")
        queue = self._brokers[0].client_session.incoming("f1")

        # wait until the binding key has propagated to each broker

        binding_counts = [1, 1, 1, 1]
        self.assertEqual(len(binding_counts), len(exchanges), "Update Test!")
        for i in range(3,-1,-1):
            retries = 0
            exchanges[i].update()
            while exchanges[i].bindingCount < binding_counts[i]:
                retries += 1
                self.failIfEqual(retries, 10,
                                 "binding failed to propagate to broker %d"
                                 % i)
                sleep(3)
                exchanges[i].update()

        for _b in range(1,4):
            # send 3 msgs from each source broker
            for i in range(3):
                dp = self._brokers[_b].client_session.delivery_properties(routing_key="spudboy")
                self._brokers[_b].client_session.message_transfer(destination="fedX.direct", message=Message(dp, "Message_drp %d" % i))

        # get exactly 9 (3 per broker) on B0
        for i in range(9):
            msg = queue.get(timeout=5)

        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message in queue: " + extra.body)
        except Empty: None

        # verify that messages went across every link
        for _l in qmf.getObjects(_broker=self._brokers[0].qmf_broker,
                                 _class="link"):
            for _c in qmf.getObjects(_broker=self._brokers[0].qmf_broker,
                                     _objectId=_l.connectionRef):
                self.assertNotEqual(_c.msgsToClient, 0, "Messages did not pass over link as expected.")

        # cleanup

        self._brokers[0].client_session.exchange_unbind(queue="DestQ", exchange="fedX.direct", binding_key="spudboy")
        self._brokers[0].client_session.message_cancel(destination="f1")
        self._brokers[0].client_session.queue_delete(queue="DestQ")

        for _b in qmf.getObjects(_class="bridge"):
            result = _b.close()
            self.assertEqual(result.status, 0)

        for _l in qmf.getObjects(_class="link"):
            result = _l.close()
            self.assertEqual(result.status, 0)

        for _b in self._brokers:
            _b.client_session.exchange_delete(exchange="fedX.direct")

        self._teardown_brokers()

        self.verify_cleanup()

    def test_dynamic_bounce_unbinds_named_queue(self):
        """ Verify that a propagated binding is removed when the connection is
        bounced
        """
        session = self.session

        # create the federation

        self.startQmf()
        qmf = self.qmf

        self._setup_brokers()

        # create exchange on each broker, and retrieve the corresponding
        # management object for that exchange

        exchanges=[]
        for _b in self._brokers[0:2]:
            _b.client_session.exchange_declare(exchange="fedX", type="direct")
            self.assertEqual(_b.client_session.exchange_query(name="fedX").type,
                             "direct", "exchange_declare failed!")
            # pull the exchange out of qmf...
            retries = 0
            my_exchange = None
            timeout = time() + 10
            while my_exchange is None and time() <= timeout:
                objs = qmf.getObjects(_broker=_b.qmf_broker, _class="exchange")
                for ooo in objs:
                    if ooo.name == "fedX":
                        my_exchange = ooo
                        break
            if my_exchange is None:
                self.fail("QMF failed to find new exchange!")
            exchanges.append(my_exchange)

        # on the destination broker, create a binding for propagation
        self._brokers[0].client_session.queue_declare(queue="fedDstQ")
        self._brokers[0].client_session.exchange_bind(queue="fedDstQ", exchange="fedX", binding_key="spud")

        # on the source broker, create a bridge queue
        self._brokers[1].client_session.queue_declare(queue="fedSrcQ")

        # connect B1 --> B0
        result = self._brokers[0].qmf_object.create( "link",
                                                     "Link-dynamic",
                                                     {"host":self._brokers[1].host,
                                                      "port":self._brokers[1].port}, False)
        self.assertEqual(result.status, 0)

        # bridge the "fedX" exchange:
        result = self._brokers[0].qmf_object.create("bridge",
                                                    "Bridge-dynamic",
                                                    {"link":"Link-dynamic",
                                                     "src":"fedX",
                                                     "dest":"fedX",
                                                     "dynamic":True,
                                                     "queue":"fedSrcQ"}, False)
        self.assertEqual(result.status, 0)

        # wait for the inter-broker links to become operational
        operational = False
        timeout = time() + 10
        while not operational and time() <= timeout:
            operational = True
            for _l in qmf.getObjects(_class="link"):
                #print("Link=%s:%s %s" % (_l.host, _l.port, str(_l.state)))
                if _l.state != "Operational":
                    operational = False
        self.failUnless(operational, "inter-broker links failed to become operational.")

        # wait until the binding key has propagated to the src broker
        exchanges[1].update()
        timeout = time() + 10
        while exchanges[1].bindingCount < 1 and time() <= timeout:
            exchanges[1].update()
        self.failUnless(exchanges[1].bindingCount == 1)

        #
        # Tear down the bridges between the two exchanges, then wait
        # for the bindings to be cleaned up
        #
        for _b in qmf.getObjects(_class="bridge"):
            result = _b.close()
            self.assertEqual(result.status, 0)
        exchanges[1].update()
        timeout = time() + 10
        while exchanges[1].bindingCount != 0 and time() <= timeout:
            exchanges[1].update()
        self.failUnless(exchanges[1].bindingCount == 0)

        self._brokers[1].client_session.queue_delete(queue="fedSrcQ")

        for _b in qmf.getObjects(_class="bridge"):
            result = _b.close()
            self.assertEqual(result.status, 0)

        for _l in qmf.getObjects(_class="link"):
            result = _l.close()
            self.assertEqual(result.status, 0)

        for _b in self._brokers[0:2]:
            _b.client_session.exchange_delete(exchange="fedX")

        self._teardown_brokers()

        self.verify_cleanup()

    def test_credit(self):
        """ Test a federation link configured to use explict acks and a credit
        limit
        """
        session = self.session

        # setup queue on remote broker and add some messages
        r_conn = self.connect(host=self.remote_host(), port=self.remote_port())
        r_session = r_conn.session("test_credit")
        r_session.queue_declare(queue="my-bridge-queue", auto_delete=True)

        #setup queue to receive messages from local broker
        session.queue_declare(queue="fed1", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="fed1", exchange="amq.fanout")
        self.subscribe(queue="fed1", destination="f1")
        queue = session.incoming("f1")

        self.startQmf()
        qmf = self.qmf
        broker = qmf.getObjects(_class="broker")[0]
        result = broker.connect(self.remote_host(), self.remote_port(), False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0, result)

        link = qmf.getObjects(_class="link")[0]

        # now wait for Link to go operational
        retries = 0
        operational = False
        while not operational:
            link.update()
            if link.state == "Operational":
                operational = True;
            if not operational:
                retries += 1
                self.failIfEqual(retries, 10,
                                 "inter-broker links failed to become operational.")
                sleep(1)

        # create the subscription
        result = link.bridge(False, "my-bridge-queue", "amq.fanout", "my-key",
                             "", "", True, False, False,
                             3,   # explicit ack, with sync every 3 msgs
                             7)   # msg credit
        self.assertEqual(result.status, 0, result)
        bridge = qmf.getObjects(_class="bridge")[0]

        # generate enough traffic to trigger flow control and syncs
        for i in range(1000):
            dp = r_session.delivery_properties(routing_key="my-bridge-queue")
            r_session.message_transfer(message=Message(dp, "Message %d" % i))

        for i in range(1000):
            try:
                msg = queue.get(timeout=5)
                self.assertEqual("Message %d" % i, msg.body)
            except Empty:
                self.fail("Failed to find expected message containing 'Message %d'" % i)
        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message in queue: " + extra.body)
        except Empty: None

        result = bridge.close()
        self.assertEqual(result.status, 0, result)
        result = link.close()
        self.assertEqual(result.status, 0, result)

        r_session.close()
        r_conn.close()

        self.verify_cleanup()

