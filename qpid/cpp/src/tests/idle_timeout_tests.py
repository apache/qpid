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

import os
import shutil
import signal
import sys

from brokertest import *
from qpid.harness import Skipped

class AmqpIdleTimeoutTest(BrokerTest):
    """
    Test AMQP 1.0 idle-timeout support
    """
    def setUp(self):
        BrokerTest.setUp(self)
        if not BrokerTest.amqp_lib:
            raise Skipped("AMQP 1.0 library not found")
        if qm != qpid_messaging:
            raise Skipped("AMQP 1.0 client not found")
        self._broker = self.broker()

    def test_client_timeout(self):
        """Ensure that the client disconnects should the broker stop
        responding.
        """
        conn = self._broker.connect(native=False, timeout=None,
                                    protocol="amqp1.0", heartbeat=1)
        self.assertTrue(conn.isOpen())
        # should disconnect within 2 seconds of broker stop
        deadline = time.time() + 8
        os.kill(self._broker.pid, signal.SIGSTOP)
        while time.time() < deadline:
            if not conn.isOpen():
                break;
        self.assertTrue(not conn.isOpen())
        os.kill(self._broker.pid, signal.SIGCONT)


    def test_broker_timeout(self):
        """By default, the broker will adopt the same timeout as the client
        (mimics the 0-10 timeout behavior).  Verify the broker disconnects
        unresponsive clients.
        """

        count = len(self._broker.agent.getAllConnections())

        # Create a new connection to the broker:
        receiver_cmd = ["qpid-receive",
                        "--broker", self._broker.host_port(),
                        "--address=amq.fanout",
                        "--connection-options={protocol:amqp1.0, heartbeat:1}",
                        "--forever"]
        receiver = self.popen(receiver_cmd, stdout=PIPE, stderr=PIPE,
                              expect=EXPECT_UNKNOWN)
        start = time.time()
        deadline = time.time() + 10
        while time.time() < deadline:
            if count < len(self._broker.agent.getAllConnections()):
                break;
        self.assertTrue(count < len(self._broker.agent.getAllConnections()))

        # now 'hang' the client, the broker should disconnect
        start = time.time()
        os.kill(receiver.pid, signal.SIGSTOP)
        deadline = time.time() + 10
        while time.time() < deadline:
            if count == len(self._broker.agent.getAllConnections()):
                break;
        self.assertEqual(count, len(self._broker.agent.getAllConnections()))
        os.kill(receiver.pid, signal.SIGCONT)
        receiver.teardown()


if __name__ == "__main__":
    shutil.rmtree("brokertest.tmp", True)
    os.execvp("qpid-python-test",
              ["qpid-python-test", "-m", "idle_timeout_tests"] + sys.argv[1:])
