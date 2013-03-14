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
from qpid.datatypes import uuid4
from brokertest import *
from threading import Thread, Lock, Condition
from logging import getLogger, WARN, ERROR, DEBUG, INFO
from qpidtoollibs import BrokerAgent, BrokerObject
from uuid import UUID

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
        os.putenv("QPID_LOAD_MODULE", BrokerTest.amqpc_lib)
        self.broker = self.amqp_broker()
        self.default_config = Config(self.broker)
        self.agent = BrokerAgent(self.broker.connect())

    def sender(self, config):
        cmd = ["qpid-send",
               "--broker", config.url,
               "--address", config.address,
               "--connection-options", "{protocol:%s}" % config.version,
               "--content-stdin", "--send-eos=1"
               ]
        return self.popen(cmd, stdin=PIPE)

    def receiver(self, config):
        cmd = ["qpid-receive",
               "--broker", config.url,
               "--address", config.address,
               "--connection-options", "{protocol:%r}" % config.version,
               "--timeout=10"
               ]
        return self.popen(cmd, stdout=PIPE)

    def send_and_receive(self, send_config=None, recv_config=None, count=1000, debug=False):
        if debug:
            print "sender config is %s" % (send_config or self.default_config)
            print "receiver config is %s" % (recv_config or self.default_config)
        sender = self.sender(send_config or self.default_config)
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

    def test_domain(self):
        brokerB = self.amqp_broker()
        self.agent.create("domain", "BrokerB", {"url":brokerB.host_port()})
        domains = self.agent._getAllBrokerObjects(Domain)
        assert len(domains) == 1
        assert domains[0].name == "BrokerB"

    def test_incoming_link(self):
        brokerB = self.amqp_broker()
        agentB = BrokerAgent(brokerB.connect())
        self.agent.create("queue", "q")
        agentB.create("queue", "q")
        self.agent.create("domain", "BrokerB", {"url":brokerB.host_port(), "sasl_mechanisms":"NONE"})
        self.agent.create("incoming", "Link1", {"domain":"BrokerB","source":"q","target":"q"})
        #send to brokerB, receive from brokerA
        self.send_and_receive(send_config=Config(brokerB))

    def test_outgoing_link(self):
        brokerB = self.amqp_broker()
        agentB = BrokerAgent(brokerB.connect())
        self.agent.create("queue", "q")
        agentB.create("queue", "q")
        self.agent.create("domain", "BrokerB", {"url":brokerB.host_port(), "sasl_mechanisms":"NONE"})
        self.agent.create("outgoing", "Link1", {"domain":"BrokerB","source":"q","target":"q"})
        #send to brokerA, receive from brokerB
        self.send_and_receive(recv_config=Config(brokerB))

    def test_relay(self):
        brokerB = self.amqp_broker()
        agentB = BrokerAgent(brokerB.connect())
        agentB.create("queue", "q")
        self.agent.create("domain", "BrokerB", {"url":brokerB.host_port(), "sasl_mechanisms":"NONE"})
        #send to q on broker B through brokerA
        self.send_and_receive(send_config=Config(self.broker, address="q@BrokerB"), recv_config=Config(brokerB))

    """ Create and return a broker with AMQP 1.0 support """
    def amqp_broker(self):
        assert BrokerTest.amqp_lib, "Cannot locate AMQP 1.0 plug-in"
        args = ["--load-module", BrokerTest.amqp_lib,
                "--max-negotiate-time=600000",
                "--log-enable=trace+:Protocol",
                "--log-enable=info+"]
        return BrokerTest.broker(self, args)

if __name__ == "__main__":
    shutil.rmtree("brokertest.tmp", True)
    os.execvp("qpid-python-test",
              ["qpid-python-test", "-m", "interlink_tests"] + sys.argv[1:])
