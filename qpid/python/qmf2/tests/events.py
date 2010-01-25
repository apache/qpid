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
import time
import datetime
import logging
from threading import Thread, Event

import qpid.messaging
from qmf2.common import (Notifier, SchemaObjectClass, SchemaClassId,
                         SchemaProperty, qmfTypes, SchemaMethod, QmfQuery,
                         QmfData, QmfQueryPredicate, SchemaEventClass,
                         QmfEvent)
import qmf2.console
from qmf2.agent import(QmfAgentData, Agent)


class _testNotifier(Notifier):
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
    def __init__(self, name, broker_url, heartbeat):
        Thread.__init__(self)
        self.timeout = 3
        self.broker_url = broker_url
        self.notifier = _testNotifier()
        self.agent = Agent(name,
                           _notifier=self.notifier,
                           _heartbeat_interval=heartbeat)

        # Dynamically construct a management database

        _schema = SchemaEventClass(_classId=SchemaClassId("MyPackage",
                                                          "MyClass",
                                                          stype=SchemaClassId.TYPE_EVENT),
                                   _desc="A test event schema")
        # add properties
        _schema.add_property( "prop-1", SchemaProperty(qmfTypes.TYPE_UINT8))
        _schema.add_property( "prop-2", SchemaProperty(qmfTypes.TYPE_LSTR))

        # Add schema to Agent
        self.schema = _schema
        self.agent.register_object_class(_schema)

        self.running = False

    def start_app(self):
        self.running = True
        self.start()

    def stop_app(self):
        self.running = False
        # wake main thread
        self.notifier.indication() # hmmm... collide with daemon???
        self.join(self.timeout)
        if self.isAlive():
            raise Exception("AGENT DID NOT TERMINATE AS EXPECTED!!!")

    def run(self):
        # broker_url = "user/passwd@hostname:port"
        conn = qpid.messaging.Connection(self.broker_url.host,
                                         self.broker_url.port,
                                         self.broker_url.user,
                                         self.broker_url.password)
        conn.connect()
        self.agent.set_connection(conn)

        counter = 1
        while self.running:
            # post an event every second
            event = QmfEvent.create(long(time.time() * 1000),
                                    QmfEvent.SEV_WARNING,
                                    {"prop-1": counter,
                                     "prop-2": str(datetime.datetime.utcnow())},
                                    _schema=self.schema)
            counter += 1
            self.agent.raise_event(event)
            wi = self.agent.get_next_workitem(timeout=0)
            while wi is not None:
                logging.error("UNEXPECTED AGENT WORKITEM RECEIVED=%s" % wi.get_type())
                self.agent.release_workitem(wi)
                wi = self.agent.get_next_workitem(timeout=0)
            self.notifier.wait_for_work(1)

        self.agent.remove_connection(self.timeout)
        self.agent.destroy(self.timeout)



class BaseTest(unittest.TestCase):
    def configure(self, config):
        self.config = config
        self.broker = config.broker
        self.defines = self.config.defines

    def setUp(self):
        # one second agent indication interval
        self.agent1 = _agentApp("agent1", self.broker, 1)
        self.agent1.start_app()
        self.agent2 = _agentApp("agent2", self.broker, 1)
        self.agent2.start_app()

    def tearDown(self):
        if self.agent1:
            self.agent1.stop_app()
            self.agent1 = None
        if self.agent2:
            self.agent2.stop_app()
            self.agent2 = None

    def test_get_events(self):
        # create console
        # find agents

        self.notifier = _testNotifier()
        self.console = qmf.qmfConsole.Console(notifier=self.notifier,
                                              agent_timeout=3)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.addConnection(self.conn)

        # find the agents
        for aname in ["agent1", "agent2"]:
            agent = self.console.find_agent(aname, timeout=3)
            self.assertTrue(agent and agent.get_name() == aname)

        # now wait for events
        agent1_events = agent2_events = 0
        wi = self.console.get_next_workitem(timeout=4)
        while wi:
            if wi.get_type() == wi.EVENT_RECEIVED:
                event = wi.get_params().get("event")
                self.assertTrue(isinstance(event, QmfEvent))
                self.assertTrue(event.get_severity() == QmfEvent.SEV_WARNING)
                self.assertTrue(event.get_value("prop-1") > 0)

                agent = wi.get_params().get("agent")
                if not agent or not isinstance(agent, qmf.qmfConsole.Agent):
                    self.fail("Unexpected workitem from agent")
                else:
                    if agent.get_name() == "agent1":
                        agent1_events += 1
                    elif agent.get_name() == "agent2":
                        agent2_events += 1
                    else:
                        self.fail("Unexpected agent name received: %s" %
                                  agent.get_name())
                    if agent1_events and agent2_events:
                        break;

            wi = self.console.get_next_workitem(timeout=4)

        self.assertTrue(agent1_events > 0 and agent2_events > 0)

        self.console.destroy(10)




