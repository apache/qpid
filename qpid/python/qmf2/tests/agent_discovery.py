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
import unittest
import logging
from threading import Thread, Event

import qpid.messaging
import qmf2.common
import qmf2.console
import qmf2.agent


class _testNotifier(qmf2.common.Notifier):
    def __init__(self):
        self._event = Event()

    def indication(self):
        # note: called by qmf daemon thread
        self._event.set()

    def wait_for_work(self, timeout):
        # note: called by application thread to wait
        # for qmf to generate work
        self._event.wait(timeout)
        timed_out = self._event.isSet() == False
        if not timed_out:
            self._event.clear()
            return True
        return False


class _agentApp(Thread):
    def __init__(self, name, heartbeat):
        Thread.__init__(self)
        self.notifier = _testNotifier()
        self.agent = qmf2.agent.Agent(name,
                           _notifier=self.notifier,
                           _heartbeat_interval=heartbeat)
        # No database needed for this test
        self.running = True
        self.start()

    def connect_agent(self, broker_url):
        # broker_url = "user/passwd@hostname:port"
        self.conn = qpid.messaging.Connection(broker_url.host,
                                         broker_url.port,
                                         broker_url.user,
                                         broker_url.password)
        self.conn.connect()
        self.agent.set_connection(self.conn)

    def disconnect_agent(self, timeout):
        if self.conn:
            self.agent.remove_connection(timeout)

    def shutdown_agent(self, timeout):
        self.agent.destroy(timeout)

    def stop(self):
        self.running = False
        self.notifier.indication() # hmmm... collide with daemon???
        self.join(10)
        if self.isAlive():
            logging.error("AGENT DID NOT TERMINATE AS EXPECTED!!!")

    def run(self):
        while self.running:
            self.notifier.wait_for_work(None)
            wi = self.agent.get_next_workitem(timeout=0)
            while wi is not None:
                logging.error("UNEXPECTED AGENT WORKITEM RECEIVED=%s" % wi.get_type())
                self.agent.release_workitem(wi)
                wi = self.agent.get_next_workitem(timeout=0)



class BaseTest(unittest.TestCase):
    def configure(self, config):
        self.config = config
        self.broker = config.broker
        self.defines = self.config.defines

    def setUp(self):
        # one second agent indication interval
        self.agent1 = _agentApp("agent1", 1)
        self.agent1.connect_agent(self.broker)
        self.agent2 = _agentApp("agent2", 1)
        self.agent2.connect_agent(self.broker)

    def tearDown(self):
        if self.agent1:
            self.agent1.shutdown_agent(10)
            self.agent1.stop()
            self.agent1 = None
        if self.agent2:
            self.agent2.shutdown_agent(10)
            self.agent2.stop()
            self.agent2 = None

    def test_discover_all(self):
        # create console
        # enable agent discovery
        # wait
        # expect agent add for agent1 and agent2
        self.notifier = _testNotifier()
        self.console = qmf2.console.Console(notifier=self.notifier,
                                              agent_timeout=3)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.addConnection(self.conn)
        self.console.enable_agent_discovery()

        agent1_found = agent2_found = False
        wi = self.console.get_next_workitem(timeout=3)
        while wi and not (agent1_found and agent2_found):
            if wi.get_type() == wi.AGENT_ADDED:
                agent = wi.get_params().get("agent")
                if not agent or not isinstance(agent, qmf2.console.Agent):
                    self.fail("Unexpected workitem from agent")
                else:
                    if agent.get_name() == "agent1":
                        agent1_found = True
                    elif agent.get_name() == "agent2":
                        agent2_found = True
                    else:
                        self.fail("Unexpected agent name received: %s" %
                                  agent.get_name())
                    if agent1_found and agent2_found:
                        break;

            wi = self.console.get_next_workitem(timeout=3)

        self.assertTrue(agent1_found and agent2_found, "All agents not discovered")

        self.console.destroy(10)


    def test_discover_one(self):
        # create console
        # enable agent discovery, filter for agent1 only
        # wait until timeout
        # expect agent add for agent1 only
        self.notifier = _testNotifier()
        self.console = qmf2.console.Console(notifier=self.notifier,
                                              agent_timeout=3)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.addConnection(self.conn)

        query = qmf2.common.QmfQuery.create_predicate(
                           qmf2.common.QmfQuery.TARGET_AGENT,
                           qmf2.common.QmfQueryPredicate({qmf2.common.QmfQuery.CMP_EQ:
                                 [qmf2.common.QmfQuery.KEY_AGENT_NAME, "agent1"]}))
        self.console.enable_agent_discovery(query)

        agent1_found = agent2_found = False
        wi = self.console.get_next_workitem(timeout=3)
        while wi:
            if wi.get_type() == wi.AGENT_ADDED:
                agent = wi.get_params().get("agent")
                if not agent or not isinstance(agent, qmf2.console.Agent):
                    self.fail("Unexpected workitem from agent")
                else:
                    if agent.get_name() == "agent1":
                        agent1_found = True
                    elif agent.get_name() == "agent2":
                        agent2_found = True
                    else:
                        self.fail("Unexpected agent name received: %s" %
                                  agent.get_name())

            wi = self.console.get_next_workitem(timeout=2)

        self.assertTrue(agent1_found and not agent2_found, "Unexpected agent discovered")

        self.console.destroy(10)


    def test_heartbeat(self):
        # create console with 2 sec agent timeout
        # enable agent discovery, find all agents
        # stop agent1, expect timeout notification
        # stop agent2, expect timeout notification
        self.notifier = _testNotifier()
        self.console = qmf2.console.Console(notifier=self.notifier,
                                              agent_timeout=2)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.addConnection(self.conn)
        self.console.enable_agent_discovery()

        agent1_found = agent2_found = False
        wi = self.console.get_next_workitem(timeout=4)
        while wi and not (agent1_found and agent2_found):
            if wi.get_type() == wi.AGENT_ADDED:
                agent = wi.get_params().get("agent")
                if not agent or not isinstance(agent, qmf2.console.Agent):
                    self.fail("Unexpected workitem from agent")
                else:
                    if agent.get_name() == "agent1":
                        agent1_found = True
                    elif agent.get_name() == "agent2":
                        agent2_found = True
                    else:
                        self.fail("Unexpected agent name received: %s" %
                                  agent.get_name())
                    if agent1_found and agent2_found:
                        break;

            wi = self.console.get_next_workitem(timeout=4)

        self.assertTrue(agent1_found and agent2_found, "All agents not discovered")

        # now kill agent1 and wait for expiration

        agent1 = self.agent1
        self.agent1 = None
        agent1.shutdown_agent(10)
        agent1.stop()

        wi = self.console.get_next_workitem(timeout=4)
        while wi is not None:
            if wi.get_type() == wi.AGENT_DELETED:
                agent = wi.get_params().get("agent")
                if not agent or not isinstance(agent, qmf2.console.Agent):
                    self.fail("Unexpected workitem from agent")
                else:
                    if agent.get_name() == "agent1":
                        agent1_found = False
                    else:
                        self.fail("Unexpected agent_deleted received: %s" %
                                  agent.get_name())
                    if not agent1_found:
                        break;

            wi = self.console.get_next_workitem(timeout=4)

        self.assertFalse(agent1_found, "agent1 did not delete!")

        # now kill agent2 and wait for expiration

        agent2 = self.agent2
        self.agent2 = None
        agent2.shutdown_agent(10)
        agent2.stop()

        wi = self.console.get_next_workitem(timeout=4)
        while wi is not None:
            if wi.get_type() == wi.AGENT_DELETED:
                agent = wi.get_params().get("agent")
                if not agent or not isinstance(agent, qmf2.console.Agent):
                    self.fail("Unexpected workitem from agent")
                else:
                    if agent.get_name() == "agent2":
                        agent2_found = False
                    else:
                        self.fail("Unexpected agent_deleted received: %s" %
                                  agent.get_name())
                    if not agent2_found:
                        break;

            wi = self.console.get_next_workitem(timeout=4)

        self.assertFalse(agent2_found, "agent2 did not delete!")

        self.console.destroy(10)


    def test_find_agent(self):
        # create console
        # do not enable agent discovery
        # find agent1, expect success
        # find agent-none, expect failure
        # find agent2, expect success
        self.notifier = _testNotifier()
        self.console = qmf2.console.Console(notifier=self.notifier)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.addConnection(self.conn)

        agent1 = self.console.find_agent("agent1", timeout=3)
        self.assertTrue(agent1 and agent1.get_name() == "agent1")

        no_agent = self.console.find_agent("agent-none", timeout=3)
        self.assertTrue(no_agent == None)

        agent2 = self.console.find_agent("agent2", timeout=3)
        self.assertTrue(agent2 and agent2.get_name() == "agent2")

        self.console.removeConnection(self.conn, 10)
        self.console.destroy(10)


