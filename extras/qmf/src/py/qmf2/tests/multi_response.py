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
from qmf2.common import (Notifier, SchemaObjectClass, SchemaClassId,
                         SchemaProperty, qmfTypes, SchemaMethod, QmfQuery,
                         QmfData) 
import qmf2.console
from qmf2.agent import(QmfAgentData, Agent)

# note: objects, schema per agent must each be > max objs
_SCHEMAS_PER_AGENT=7
_OBJS_PER_AGENT=19
_MAX_OBJS_PER_MSG=3


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
        self.schema_count = _SCHEMAS_PER_AGENT
        self.obj_count = _OBJS_PER_AGENT
        self.notifier = _testNotifier()
        self.broker_url = broker_url
        self.agent = Agent(name,
                           _notifier=self.notifier,
                           heartbeat_interval=heartbeat,
                           max_msg_size=_MAX_OBJS_PER_MSG)

        # Dynamically construct a management database
        for i in range(self.schema_count):
            _schema = SchemaObjectClass( _classId=SchemaClassId("MyPackage",
                                                                "MyClass-" + str(i)),
                                         _desc="A test data schema",
                                         _object_id_names=["index1", "index2"] )
            # add properties
            _schema.add_property( "index1", SchemaProperty(qmfTypes.TYPE_UINT8))
            _schema.add_property( "index2", SchemaProperty(qmfTypes.TYPE_LSTR))

            # these two properties are statistics
            _schema.add_property( "query_count", SchemaProperty(qmfTypes.TYPE_UINT32))
            _schema.add_property( "method_call_count", SchemaProperty(qmfTypes.TYPE_UINT32))

            # These two properties can be set via the method call
            _schema.add_property( "set_string", SchemaProperty(qmfTypes.TYPE_LSTR))
            _schema.add_property( "set_int", SchemaProperty(qmfTypes.TYPE_UINT32))

            # add method
            _meth = SchemaMethod( _desc="Method to set string and int in object." )
            _meth.add_argument( "arg_int", SchemaProperty(qmfTypes.TYPE_UINT32) )
            _meth.add_argument( "arg_str", SchemaProperty(qmfTypes.TYPE_LSTR) )
            _schema.add_method( "set_meth", _meth )

            # Add schema to Agent

            self.agent.register_object_class(_schema)

            # instantiate managed data objects matching the schema

            for j in range(self.obj_count):

                self.agent.add_object( QmfAgentData( self.agent, _schema=_schema,
                                                     _values={"index1":j,
                                                              "index2": "name-" + str(j),
                                                              "set_string": "UNSET",
                                                              "set_int": 0,
                                                              "query_count": 0,
                                                              "method_call_count": 0} ))

        self.running = False
        self.ready = Event()

    def start_app(self):
        self.running = True
        self.start()
        self.ready.wait(10)
        if not self.ready.is_set():
            raise Exception("Agent failed to connect to broker.")

    def stop_app(self):
        self.running = False
        # wake main thread
        self.notifier.indication() # hmmm... collide with daemon???
        self.join(10)
        if self.isAlive():
            raise Exception("AGENT DID NOT TERMINATE AS EXPECTED!!!")

    def run(self):
        # broker_url = "user/passwd@hostname:port"
        self.conn = qpid.messaging.Connection(self.broker_url.host,
                                              self.broker_url.port,
                                              self.broker_url.user,
                                              self.broker_url.password)
        self.conn.connect()
        self.agent.set_connection(self.conn)
        self.ready.set()

        while self.running:
            self.notifier.wait_for_work(None)
            wi = self.agent.get_next_workitem(timeout=0)
            while wi is not None:
                logging.error("UNEXPECTED AGENT WORKITEM RECEIVED=%s" % wi.get_type())
                self.agent.release_workitem(wi)
                wi = self.agent.get_next_workitem(timeout=0)

        if self.conn:
            self.agent.remove_connection(10)
        self.agent.destroy(10)




class BaseTest(unittest.TestCase):
    def configure(self, config):
        self.agent_count = 2
        self.config = config
        self.broker = config.broker
        self.defines = self.config.defines

    def setUp(self):
        # one second agent indication interval
        self.agent_heartbeat = 1
        self.agents = []
        for a in range(self.agent_count):
            agent = _agentApp("agent-" + str(a), 
                              self.broker, 
                              self.agent_heartbeat)
            agent.start_app()
            self.agents.append(agent)

    def tearDown(self):
        for agent in self.agents:
            if agent is not None:
                agent.stop_app()

    def test_all_schema_id(self):
        # create console
        # find agents
        # synchronous query for all schemas_ids
        self.notifier = _testNotifier()
        self.console = qmf2.console.Console(notifier=self.notifier,
                                              agent_timeout=3)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.add_connection(self.conn)

        for agent_app in self.agents:
            agent = self.console.find_agent(agent_app.agent.get_name(), timeout=3)
            self.assertTrue(agent and agent.get_name() == agent_app.agent.get_name())

            # get a list of all schema_ids
            query = QmfQuery.create_wildcard(QmfQuery.TARGET_SCHEMA_ID)
            sid_list = self.console.do_query(agent, query)
            self.assertTrue(sid_list and len(sid_list) == _SCHEMAS_PER_AGENT)
            for sid in sid_list:
                self.assertTrue(isinstance(sid, SchemaClassId))
                self.assertTrue(sid.get_package_name() == "MyPackage")
                self.assertTrue(sid.get_class_name().split('-')[0] == "MyClass")

        self.console.destroy(10)


    def test_all_schema(self):
        # create console
        # find agents
        # synchronous query for all schemas
        self.notifier = _testNotifier()
        self.console = qmf2.console.Console(notifier=self.notifier,
                                              agent_timeout=3)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.add_connection(self.conn)

        for agent_app in self.agents:
            agent = self.console.find_agent(agent_app.agent.get_name(), timeout=3)
            self.assertTrue(agent and agent.get_name() == agent_app.agent.get_name())

            # get a list of all schema_ids
            query = QmfQuery.create_wildcard(QmfQuery.TARGET_SCHEMA)
            schema_list = self.console.do_query(agent, query)
            self.assertTrue(schema_list and 
                            len(schema_list) == _SCHEMAS_PER_AGENT) 
            for schema in schema_list:
                self.assertTrue(isinstance(schema, SchemaObjectClass))

        self.console.destroy(10)


    def test_all_object_id(self):
        # create console
        # find agents
        # synchronous query for all object_ids by schema_id
        self.notifier = _testNotifier()
        self.console = qmf2.console.Console(notifier=self.notifier,
                                              agent_timeout=3)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.add_connection(self.conn)

        for agent_app in self.agents:
            agent = self.console.find_agent(agent_app.agent.get_name(), timeout=3)
            self.assertTrue(agent and agent.get_name() == agent_app.agent.get_name())

            # get a list of all schema_ids
            query = QmfQuery.create_wildcard(QmfQuery.TARGET_SCHEMA_ID)
            sid_list = self.console.do_query(agent, query)
            self.assertTrue(sid_list and len(sid_list) == _SCHEMAS_PER_AGENT)
            for sid in sid_list:
                query = QmfQuery.create_wildcard_object_id(sid)
                oid_list = self.console.do_query(agent, query)
                self.assertTrue(oid_list and
                                len(oid_list) == _OBJS_PER_AGENT) 
                for oid in oid_list:
                    self.assertTrue(isinstance(oid, basestring))

        self.console.destroy(10)


    def test_all_objects(self):
        # create console
        # find agents
        # synchronous query for all objects by schema_id
        self.notifier = _testNotifier()
        self.console = qmf2.console.Console(notifier=self.notifier,
                                              agent_timeout=3)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.add_connection(self.conn)

        for agent_app in self.agents:
            agent = self.console.find_agent(agent_app.agent.get_name(), timeout=3)
            self.assertTrue(agent and agent.get_name() == agent_app.agent.get_name())

            # get a list of all schema_ids
            query = QmfQuery.create_wildcard(QmfQuery.TARGET_SCHEMA_ID)
            sid_list = self.console.do_query(agent, query)
            self.assertTrue(sid_list and len(sid_list) == _SCHEMAS_PER_AGENT)
            for sid in sid_list:
                query = QmfQuery.create_wildcard_object(sid)
                obj_list = self.console.do_query(agent, query)
                self.assertTrue(obj_list and
                                len(obj_list) == _OBJS_PER_AGENT)
                for obj in obj_list:
                    self.assertTrue(isinstance(obj,
                                               qmf2.console.QmfConsoleData))

        self.console.destroy(10)
