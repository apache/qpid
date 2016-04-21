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

import os, signal, sys, time, imp, re, subprocess, glob, random, logging, shutil, math, unittest, random
import traceback
from qpid.messaging import Message, SessionError, NotFound, ConnectionError, ReceiverError, Connection, Timeout, Disposition, REJECTED, Empty
from brokertest import *
from ha_test import HaPort
from threading import Thread, Lock, Condition
from logging import getLogger, WARN, ERROR, DEBUG, INFO
from qpidtoollibs import BrokerObject

class Domain(BrokerObject):
    def __init__(self, broker, values):
        BrokerObject.__init__(self, broker, values)

class Config:
    def __init__(self, broker, address="q;{create:always}", version="amqp1.0"):
        self.url = broker.host_port()
        self.address = address
        self.version = version

    def __str__(self):
        return "url: %s, address: %s, version: %s" % (self.url, self.address, self.version)

class AmqpBrokerTest(BrokerTest):
    """
    Tests using AMQP 1.0 support
    """
    def setUp(self):
        BrokerTest.setUp(self)
        self.port_holder = HaPort(self)
        self.broker = self.amqp_broker(port_holder=self.port_holder)
        self.default_config = Config(self.broker)
        self.agent = self.broker.agent

    def sender(self, config, reply_to=None):
        cmd = ["qpid-send",
               "--broker", config.url,
               "--address", config.address,
               "--connection-options", "{protocol:%s}" % config.version,
               "--content-stdin", "--send-eos=1"
               ]
        if reply_to:
            cmd.append( "--reply-to=%s" % reply_to)
        return self.popen(cmd, stdin=PIPE)

    def receiver(self, config):
        cmd = ["qpid-receive",
               "--broker", config.url,
               "--address", config.address,
               "--connection-options", "{protocol:%r}" % config.version,
               "--timeout=10"
               ]
        return self.popen(cmd, stdout=PIPE)

    def ready_receiver(self, config):
        s = self.broker.connect().session()
        r = s.receiver("readyq; {create:always}")
        cmd = ["qpid-receive",
               "--broker", config.url,
               "--address", config.address,
               "--connection-options", "{protocol:%r}" % config.version,
               "--timeout=10", "--ready-address=readyq;{create:always}"
               ]
        result = self.popen(cmd, stdout=PIPE)
        r.fetch(timeout=1) # wait until receiver is actually ready
        s.acknowledge()
        r.close()
        s.close()
        return result

    def send_and_receive(self, send_config=None, recv_config=None, count=1000, reply_to=None, wait_for_receiver=False, debug=False):
        if debug:
            print "sender config is %s" % (send_config or self.default_config)
            print "receiver config is %s" % (recv_config or self.default_config)
        sender = self.sender(send_config or self.default_config, reply_to)
        sender._set_cloexec_flag(sender.stdin) #required for older python, see http://bugs.python.org/issue4112
        if wait_for_receiver:
            receiver = self.ready_receiver(recv_config or self.default_config)
        else:
            receiver = self.receiver(recv_config or self.default_config)

        messages = ["message-%s" % (i+1) for i in range(count)]
        for m in messages:
            sender.stdin.write(m + "\n")
            sender.stdin.flush()
        sender.stdin.close()
        if debug:
            c = send_config or self.default_config
            print "sent %s messages to %s sn %s" % (len(messages), c.address, c.url)

        if debug:
            c = recv_config or self.default_config
            print "reading messages from %s sn %s" % (c.address, c.url)
        for m in messages:
            l = receiver.stdout.readline().rstrip()
            if debug:
                print l
            assert m == l, (m, l)

        sender.wait()
        receiver.wait()

    def test_simple(self):
        self.send_and_receive()

    def test_translate1(self):
        self.send_and_receive(recv_config=Config(self.broker, version="amqp0-10"))

    def test_translate2(self):
        self.send_and_receive(send_config=Config(self.broker, version="amqp0-10"))

    def test_translate_with_large_routingkey(self):
        self.send_and_receive(send_config=Config(self.broker, address="amq.topic/a.%s" % ("x" * 256), version="amqp1.0"), recv_config=Config(self.broker, address="amq.topic/a.*", version="amqp0-10"), wait_for_receiver=True)

    def send_and_receive_empty(self, send_config=None, recv_config=None):
        sconfig = send_config or self.default_config
        rconfig = recv_config or self.default_config
        send_cmd = ["qpid-send",
               "--broker", sconfig.url,
               "--address=%s" % sconfig.address,
               "--connection-options={protocol:%s}" % sconfig.version,
               "--content-size=0",
               "--messages=1",
               "-P", "my-header=abc"
               ]
        sender = self.popen(send_cmd)
        sender.wait()
        receive_cmd = ["qpid-receive",
               "--broker", rconfig.url,
               "--address=%s" % rconfig.address,
               "--connection-options={protocol:%s}" % rconfig.version,
               "--messages=1",
               "--print-content=false", "--print-headers=true"
               ]
        receiver = self.popen(receive_cmd, stdout=PIPE)
        l = receiver.stdout.read()
        assert "my-header:abc" in l
        receiver.wait()

    def test_translate_empty_1(self):
        self.send_and_receive_empty(recv_config=Config(self.broker, version="amqp0-10"))

    def test_translate_empty_2(self):
        self.send_and_receive_empty(send_config=Config(self.broker, version="amqp0-10"))

    def request_response(self, reply_to, send_config=None, request_config=None, response_config=None, count=1000, wait_for_receiver=False):
        rconfig = request_config or self.default_config
        echo_cmd = ["qpid-receive",
               "--broker", rconfig.url,
               "--address=%s" % rconfig.address,
               "--connection-options={protocol:%s}" % rconfig.version,
               "--timeout=10", "--print-content=false", "--print-headers=false"
               ]
        requests = self.popen(echo_cmd)
        self.send_and_receive(send_config, response_config, count, reply_to=reply_to, wait_for_receiver=wait_for_receiver)
        requests.wait()

    def request_response_local(self, request_address, response_address, wait_for_receiver=False, request_version="amqp1.0", echo_version="amqp1.0"):
        self.request_response(response_address, send_config=Config(self.broker, address=request_address, version=request_version), request_config=Config(self.broker, address=request_address, version=echo_version), response_config=Config(self.broker, address=response_address, version=request_version), wait_for_receiver=wait_for_receiver)

    def test_request_reponse_queue(self):
        self.agent.create("queue", "q1")
        self.agent.create("queue", "q2")
        self.request_response_local("q1", "q2")

    def test_request_reponse_queue_translated1(self):
        self.agent.create("queue", "q1")
        self.agent.create("queue", "q2")
        self.request_response_local("q1", "q2", request_version="amqp0-10", echo_version="amqp1.0")

    def test_request_reponse_queue_translated2(self):
        self.agent.create("queue", "q1")
        self.agent.create("queue", "q2")
        self.request_response_local("q1", "q2", request_version="amqp1.0", echo_version="amqp0-10")

    def test_request_reponse_exchange(self):
        self.agent.create("queue", "q1")
        self.request_response_local("q1", "amq.fanout", wait_for_receiver=True)

    def test_request_reponse_exchange_translated1(self):
        self.agent.create("queue", "q1")
        self.request_response_local("q1", "amq.fanout", wait_for_receiver=True, request_version="amqp0-10", echo_version="amqp1.0")

    def test_request_reponse_exchange_translated2(self):
        self.agent.create("queue", "q1")
        self.request_response_local("q1", "amq.fanout", wait_for_receiver=True, request_version="amqp1.0", echo_version="amqp0-10")

    def test_request_reponse_exchange_with_subject(self):
        self.agent.create("queue", "q1")
        self.request_response_local("q1", "amq.topic/abc; {node:{type:topic}}", wait_for_receiver=True)

    def test_request_reponse_exchange_with_subject_translated1(self):
        self.agent.create("queue", "q1")
        self.request_response_local("q1", "amq.topic/abc; {node:{type:topic}}", wait_for_receiver=True, request_version="amqp0-10", echo_version="amqp1.0")

    def test_request_reponse_exchange_with_subject_translated2(self):
        self.agent.create("queue", "q1")
        self.request_response_local("q1", "amq.topic/abc; {node:{type:topic}}", wait_for_receiver=True, request_version="amqp1.0", echo_version="amqp0-10")

    def test_domain(self):
        brokerB = self.amqp_broker()
        self.agent.create("domain", "BrokerB", {"url":brokerB.host_port()})
        domains = self.agent._getAllBrokerObjects(Domain)
        assert len(domains) == 1
        assert domains[0].name == "BrokerB"

    def incoming_link(self, mechanism):
        brokerB = self.amqp_broker()
        agentB = brokerB.agent
        self.agent.create("queue", "q")
        agentB.create("queue", "q")
        self.agent.create("domain", "BrokerB", {"url":brokerB.host_port(), "sasl_mechanisms":mechanism})
        self.agent.create("incoming", "Link1", {"domain":"BrokerB","source":"q","target":"q"})
        #send to brokerB, receive from brokerA
        self.send_and_receive(send_config=Config(brokerB))

    def test_incoming_link_anonymous(self):
        self.incoming_link("ANONYMOUS")

    def test_incoming_link_nosasl(self):
        self.incoming_link("NONE")

    def test_outgoing_link(self):
        brokerB = self.amqp_broker()
        agentB = brokerB.agent
        self.agent.create("queue", "q")
        agentB.create("queue", "q")
        self.agent.create("domain", "BrokerB", {"url":brokerB.host_port(), "sasl_mechanisms":"NONE"})
        self.agent.create("outgoing", "Link1", {"domain":"BrokerB","source":"q","target":"q"})
        #send to brokerA, receive from brokerB
        self.send_and_receive(recv_config=Config(brokerB))

    def test_relay(self):
        brokerB = self.amqp_broker()
        agentB = brokerB.agent
        agentB.create("queue", "q")
        self.agent.create("domain", "BrokerB", {"url":brokerB.host_port(), "sasl_mechanisms":"NONE"})
        #send to q on broker B through brokerA
        self.send_and_receive(send_config=Config(self.broker, address="q@BrokerB"), recv_config=Config(brokerB))

    def test_reconnect(self):
        receiver_cmd = ["qpid-receive",
               "--broker", self.broker.host_port(),
               "--address=amq.fanout",
               "--connection-options={protocol:amqp1.0, reconnect:True,container_id:receiver}",
               "--timeout=10", "--print-content=true", "--print-headers=false"
               ]
        receiver = self.popen(receiver_cmd, stdout=PIPE)

        sender_cmd = ["qpid-send",
               "--broker", self.broker.host_port(),
               "--address=amq.fanout",
               "--connection-options={protocol:amqp1.0,reconnect:True,container_id:sender}",
               "--content-stdin", "--send-eos=1"
               ]
        sender = self.popen(sender_cmd, stdin=PIPE)
        sender._set_cloexec_flag(sender.stdin) #required for older python, see http://bugs.python.org/issue4112


        batch1 = ["message-%s" % (i+1) for i in range(10000)]
        for m in batch1:
            sender.stdin.write(m + "\n")
            sender.stdin.flush()

        self.broker.kill()
        self.broker = self.amqp_broker(port_holder=self.port_holder)

        batch2 = ["message-%s" % (i+1) for i in range(10000, 20000)]
        for m in batch2:
            sender.stdin.write(m + "\n")
            sender.stdin.flush()

        sender.stdin.close()

        last = None
        m = receiver.stdout.readline().rstrip()
        while len(m):
            last = m
            m = receiver.stdout.readline().rstrip()
        assert last == "message-20000", (last)

    """ Create and return a broker with AMQP 1.0 support """
    def amqp_broker(self):
        assert BrokerTest.amqp_lib, "Cannot locate AMQP 1.0 plug-in"
        self.port_holder = HaPort(self) #reserve port
        args = ["--load-module", BrokerTest.amqp_lib,
                "--socket-fd=%s" % self.port_holder.fileno,
                "--listen-disable=tcp",
                "--log-enable=trace+:Protocol",
                "--log-enable=info+"]
        return BrokerTest.broker(self, args, port=self.port_holder.port)

    def amqp_broker(self, port_holder=None):
        assert BrokerTest.amqp_lib, "Cannot locate AMQP 1.0 plug-in"
        if port_holder:
            args = ["--load-module", BrokerTest.amqp_lib,
                    "--socket-fd=%s" % port_holder.fileno,
                    "--listen-disable=tcp",
                    "--log-enable=trace+:Protocol",
                    "--log-enable=info+"]
            return BrokerTest.broker(self, args, port=port_holder.port)
        else:
            args = ["--load-module", BrokerTest.amqp_lib,
                    "--log-enable=trace+:Protocol",
                    "--log-enable=info+"]
            return BrokerTest.broker(self, args)


if __name__ == "__main__":
    shutil.rmtree("brokertest.tmp", True)
    os.execvp("qpid-python-test",
              ["qpid-python-test", "-m", "interlink_tests"] + sys.argv[1:])
