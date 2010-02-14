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
                           QmfData, WorkItem)
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
        self.notifier = _testNotifier()
        self.broker_url = broker_url
        self.agent = Agent(name,
                           _notifier=self.notifier,
                           _heartbeat_interval=heartbeat)

        # Dynamically construct a management database

        _schema = SchemaObjectClass( _classId=SchemaClassId("MyPackage", "MyClass"),
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

        _obj1 = QmfAgentData( self.agent, _schema=_schema,
                              _values={"index1":100, "index2":"a name"})
        _obj1.set_value("set_string", "UNSET")
        _obj1.set_value("set_int", 0)
        _obj1.set_value("query_count", 0)
        _obj1.set_value("method_call_count", 0)
        self.agent.add_object( _obj1 )

        self.agent.add_object( QmfAgentData( self.agent, _schema=_schema,
                                             _values={"index1":99,
                                                      "index2": "another name",
                                                      "set_string": "UNSET",
                                                      "set_int": 0,
                                                      "query_count": 0,
                                                      "method_call_count": 0} ))

        self.agent.add_object( QmfAgentData( self.agent, _schema=_schema,
                                             _values={"index1":50,
                                                      "index2": "my name",
                                                      "set_string": "SET",
                                                      "set_int": 0,
                                                      "query_count": 0,
                                                      "method_call_count": 0} ))


        # add an "unstructured" object to the Agent
        _obj2 = QmfAgentData(self.agent, _object_id="01545")
        _obj2.set_value("field1", "a value")
        _obj2.set_value("field2", 2)
        _obj2.set_value("field3", {"a":1, "map":2, "value":3})
        _obj2.set_value("field4", ["a", "list", "value"])
        _obj2.set_value("index1", 50)
        self.agent.add_object(_obj2)

        _obj2 = QmfAgentData(self.agent, _object_id="01546")
        _obj2.set_value("field1", "a value")
        _obj2.set_value("field2", 3)
        _obj2.set_value("field3", {"a":1, "map":2, "value":3})
        _obj2.set_value("field4", ["a", "list", "value"])
        _obj2.set_value("index1", 51)
        self.agent.add_object(_obj2)

        _obj2 = QmfAgentData(self.agent, _object_id="01544")
        _obj2.set_value("field1", "a value")
        _obj2.set_value("field2", 4)
        _obj2.set_value("field3", {"a":1, "map":2, "value":3})
        _obj2.set_value("field4", ["a", "list", "value"])
        _obj2.set_value("index1", 49)
        self.agent.add_object(_obj2)

        _obj2 = QmfAgentData(self.agent, _object_id="01543")
        _obj2.set_value("field1", "a value")
        _obj2.set_value("field2", 4)
        _obj2.set_value("field3", {"a":1, "map":2, "value":3})
        _obj2.set_value("field4", ["a", "list", "value"])
        _obj2.set_value("index1", 48)
        self.agent.add_object(_obj2)

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
        self.config = config
        self.broker = config.broker
        self.defines = self.config.defines

    def setUp(self):
        # one second agent indication interval
        self.agent_heartbeat = 1
        self.agent1 = _agentApp("agent1", self.broker, self.agent_heartbeat)
        self.agent1.start_app()
        self.agent2 = _agentApp("agent2", self.broker, self.agent_heartbeat)
        self.agent2.start_app()

    def tearDown(self):
        if self.agent1:
            self.agent1.stop_app()
            self.agent1 = None
        if self.agent2:
            self.agent2.stop_app()
            self.agent2 = None

    def test_all_schema_ids(self):
        # create console
        # find agents
        # asynchronous query for all schema ids
        self.notifier = _testNotifier()
        self.console = qmf2.console.Console(notifier=self.notifier,
                                              agent_timeout=3)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.add_connection(self.conn)

        for aname in ["agent1", "agent2"]:
            agent = self.console.find_agent(aname, timeout=3)
            self.assertTrue(agent and agent.get_name() == aname)

            # send queries
            query = QmfQuery.create_wildcard(QmfQuery.TARGET_SCHEMA_ID)
            rc = self.console.do_query(agent, query,
                                       _reply_handle=aname)
            self.assertTrue(rc)

        # done.  Now wait for async responses

        count = 0
        while self.notifier.wait_for_work(3):
            wi = self.console.get_next_workitem(timeout=0)
            while wi is not None:
                count += 1
                self.assertTrue(wi.get_type() == WorkItem.QUERY_COMPLETE)
                self.assertTrue(wi.get_handle() == "agent1" or
                                wi.get_handle() == "agent2")
                reply = wi.get_params()
                self.assertTrue(len(reply) == 1)
                self.assertTrue(isinstance(reply[0], SchemaClassId))
                self.assertTrue(reply[0].get_package_name() == "MyPackage")
                self.assertTrue(reply[0].get_class_name() == "MyClass")
                self.console.release_workitem(wi)
                wi = self.console.get_next_workitem(timeout=0)

        self.assertTrue(count == 2)
        self.console.destroy(10)



    def test_undescribed_objs(self):
        # create console
        # find agents
        # asynchronous query for all non-schema objects
        self.notifier = _testNotifier()
        self.console = qmf2.console.Console(notifier=self.notifier,
                                              agent_timeout=3)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.add_connection(self.conn)

        for aname in ["agent1", "agent2"]:
            agent = self.console.find_agent(aname, timeout=3)
            self.assertTrue(agent and agent.get_name() == aname)

            # send queries
            query = QmfQuery.create_wildcard(QmfQuery.TARGET_OBJECT)
            rc = self.console.do_query(agent, query, _reply_handle=aname)
            self.assertTrue(rc)

        # done.  Now wait for async responses

        count = 0
        while self.notifier.wait_for_work(3):
            wi = self.console.get_next_workitem(timeout=0)
            while wi is not None:
                count += 1
                self.assertTrue(wi.get_type() == WorkItem.QUERY_COMPLETE)
                self.assertTrue(wi.get_handle() == "agent1" or
                                wi.get_handle() == "agent2")
                reply = wi.get_params()
                self.assertTrue(len(reply) == 4)
                self.assertTrue(isinstance(reply[0], qmf2.console.QmfConsoleData))
                self.assertFalse(reply[0].is_described()) # no schema
                self.console.release_workitem(wi)
                wi = self.console.get_next_workitem(timeout=0)

        self.assertTrue(count == 2)
        self.console.destroy(10)



    def test_described_objs(self):
        # create console
        # find agents
        # asynchronous query for all schema-based objects
        self.notifier = _testNotifier()
        self.console = qmf2.console.Console(notifier=self.notifier,
                                              agent_timeout=3)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.add_connection(self.conn)

        for aname in ["agent1", "agent2"]:
            agent = self.console.find_agent(aname, timeout=3)
            self.assertTrue(agent and agent.get_name() == aname)

            #
            t_params = {QmfData.KEY_SCHEMA_ID: SchemaClassId("MyPackage", "MyClass")}
            query = QmfQuery.create_wildcard(QmfQuery.TARGET_OBJECT, t_params)
            #
            rc = self.console.do_query(agent, query, _reply_handle=aname)
            self.assertTrue(rc)

        # done.  Now wait for async responses

        count = 0
        while self.notifier.wait_for_work(3):
            wi = self.console.get_next_workitem(timeout=0)
            while wi is not None:
                count += 1
                self.assertTrue(wi.get_type() == WorkItem.QUERY_COMPLETE)
                self.assertTrue(wi.get_handle() == "agent1" or
                                wi.get_handle() == "agent2")
                reply = wi.get_params()
                self.assertTrue(len(reply) == 3)
                self.assertTrue(isinstance(reply[0], qmf2.console.QmfConsoleData))
                self.assertTrue(reply[0].is_described()) # has schema
                self.console.release_workitem(wi)
                wi = self.console.get_next_workitem(timeout=0)

        self.assertTrue(count == 2)
        # @todo test if the console has learned the corresponding schemas....
        self.console.destroy(10)



    def test_all_schemas(self):
        # create console
        # find agents
        # asynchronous query for all schemas
        self.notifier = _testNotifier()
        self.console = qmf2.console.Console(notifier=self.notifier,
                                              agent_timeout=3)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.add_connection(self.conn)

        # test internal state using non-api calls:
        # no schemas present yet
        self.assertTrue(len(self.console._schema_cache) == 0)
        # end test

        for aname in ["agent1", "agent2"]:
            agent = self.console.find_agent(aname, timeout=3)
            self.assertTrue(agent and agent.get_name() == aname)

            # send queries
            query = QmfQuery.create_wildcard(QmfQuery.TARGET_SCHEMA)
            rc = self.console.do_query(agent, query, _reply_handle=aname)
            self.assertTrue(rc)

        # done.  Now wait for async responses

        count = 0
        while self.notifier.wait_for_work(3):
            wi = self.console.get_next_workitem(timeout=0)
            while wi is not None:
                count += 1
                self.assertTrue(wi.get_type() == WorkItem.QUERY_COMPLETE)
                self.assertTrue(wi.get_handle() == "agent1" or
                                wi.get_handle() == "agent2")
                reply = wi.get_params()
                self.assertTrue(len(reply) == 1)
                self.assertTrue(isinstance(reply[0], qmf2.common.SchemaObjectClass))
                self.assertTrue(reply[0].get_class_id().get_package_name() == "MyPackage")
                self.assertTrue(reply[0].get_class_id().get_class_name() == "MyClass")
                self.console.release_workitem(wi)
                wi = self.console.get_next_workitem(timeout=0)

        self.assertTrue(count == 2)

        # test internal state using non-api calls:
        # schema has been learned
        self.assertTrue(len(self.console._schema_cache) == 1)
        # end test

        self.console.destroy(10)



    def test_query_expiration(self):
        # create console
        # find agents
        # kill the agents
        # send async query
        # wait for & verify expiration
        self.notifier = _testNotifier()
        self.console = qmf2.console.Console(notifier=self.notifier,
                                              agent_timeout=30)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.add_connection(self.conn)

        # find the agents
        agents = []
        for aname in ["agent1", "agent2"]:
            agent = self.console.find_agent(aname, timeout=3)
            self.assertTrue(agent and agent.get_name() == aname)
            agents.append(agent)

        # now nuke the agents from orbit.  It's the only way to be sure.

        self.agent1.stop_app()
        self.agent1 = None
        self.agent2.stop_app()
        self.agent2 = None

        # now send queries to agents that no longer exist
        for agent in agents:
            query = QmfQuery.create_wildcard(QmfQuery.TARGET_SCHEMA)
            rc = self.console.do_query(agent, query,
                                       _reply_handle=agent.get_name(),
                                       _timeout=2)
            self.assertTrue(rc)

        # done.  Now wait for async responses due to timeouts

        count = 0
        while self.notifier.wait_for_work(3):
            wi = self.console.get_next_workitem(timeout=0)
            while wi is not None:
                count += 1
                self.assertTrue(wi.get_type() == WorkItem.QUERY_COMPLETE)
                self.assertTrue(wi.get_handle() == "agent1" or
                                wi.get_handle() == "agent2")
                reply = wi.get_params()
                self.assertTrue(len(reply) == 0)  # empty

                self.console.release_workitem(wi)
                wi = self.console.get_next_workitem(timeout=0)

        self.assertTrue(count == 2)
        self.console.destroy(10)
