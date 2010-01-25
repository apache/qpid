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
                           QmfData, QmfQueryPredicate) 
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
    def __init__(self, name, heartbeat):
        Thread.__init__(self)
        self.notifier = _testNotifier()
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

        _obj1 = QmfAgentData( self.agent, _schema=_schema )
        _obj1.set_value("index1", 100)
        _obj1.set_value("index2", "a name" )
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

        # add an "unstructured" object to the Agent
        _obj2 = QmfAgentData(self.agent, _object_id="01545")
        _obj2.set_value("field1", "a value")
        _obj2.set_value("field2", 2)
        _obj2.set_value("field3", {"a":1, "map":2, "value":3})
        _obj2.set_value("field4", ["a", "list", "value"])
        self.agent.add_object(_obj2)

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
            raise Exception("AGENT DID NOT TERMINATE AS EXPECTED!!!")

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

    def test_all_oids(self):
        # create console
        # find agents
        # synchronous query for all objects by id
        # verify known object ids are returned
        self.notifier = _testNotifier()
        self.console = qmf.qmfConsole.Console(notifier=self.notifier,
                                              agent_timeout=3)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.addConnection(self.conn)

        for aname in ["agent1", "agent2"]:
            agent = self.console.find_agent(aname, timeout=3)
            self.assertTrue(agent and agent.get_name() == aname)

            query = QmfQuery.create_wildcard(QmfQuery.TARGET_OBJECT_ID)
            oid_list = self.console.doQuery(agent, query)

            self.assertTrue(isinstance(oid_list, type([])), 
                            "Unexpected return type")
            self.assertTrue(len(oid_list) == 3, "Wrong count")
            self.assertTrue('100a name' in oid_list)
            self.assertTrue('99another name' in oid_list)
            self.assertTrue('01545' in oid_list)

        self.console.destroy(10)


    def test_direct_oids(self):
        # create console
        # find agents
        # synchronous query for each objects
        # verify objects and schemas are correct
        self.notifier = _testNotifier()
        self.console = qmf.qmfConsole.Console(notifier=self.notifier,
                                              agent_timeout=3)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.addConnection(self.conn)

        for aname in ["agent1", "agent2"]:
            agent = self.console.find_agent(aname, timeout=3)
            self.assertTrue(agent and agent.get_name() == aname)

            for oid in ['100a name', '99another name', '01545']:
                query = QmfQuery.create_id(QmfQuery.TARGET_OBJECT, oid)
                obj_list = self.console.doQuery(agent, query)

                self.assertTrue(isinstance(obj_list, type([])), 
                                "Unexpected return type")
                self.assertTrue(len(obj_list) == 1)
                obj = obj_list[0]
                self.assertTrue(isinstance(obj, QmfData))
                self.assertTrue(obj.get_object_id() == oid)

                if obj.is_described():
                    self.assertTrue(oid in ['100a name', '99another name'])
                    schema_id = obj.get_schema_class_id()
                    self.assertTrue(isinstance(schema_id, SchemaClassId))
                else:
                    self.assertTrue(oid == "01545")



        self.console.destroy(10)



    def test_packages(self):
        # create console
        # find agents
        # synchronous query all package names
        self.notifier = _testNotifier()
        self.console = qmf.qmfConsole.Console(notifier=self.notifier,
                                              agent_timeout=3)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.addConnection(self.conn)

        for aname in ["agent1", "agent2"]:
            agent = self.console.find_agent(aname, timeout=3)
            self.assertTrue(agent and agent.get_name() == aname)

            query = QmfQuery.create_wildcard(QmfQuery.TARGET_PACKAGES)
            package_list = self.console.doQuery(agent, query)
            self.assertTrue(len(package_list) == 1)
            self.assertTrue('MyPackage' in package_list)


        self.console.destroy(10)



    def test_predicate_schema_id(self):
        # create console
        # find agents
        # synchronous query for all schema by package name
        self.notifier = _testNotifier()
        self.console = qmf.qmfConsole.Console(notifier=self.notifier,
                                              agent_timeout=3)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.addConnection(self.conn)

        for aname in ["agent1", "agent2"]:
            agent = self.console.find_agent(aname, timeout=3)
            self.assertTrue(agent and agent.get_name() == aname)

            query = QmfQuery.create_predicate(QmfQuery.TARGET_SCHEMA,
                                              QmfQueryPredicate(
                    {QmfQuery.CMP_EQ: [SchemaClassId.KEY_PACKAGE, 
                                       "MyPackage"]}))

            schema_list = self.console.doQuery(agent, query)
            self.assertTrue(len(schema_list))
            for schema in schema_list:
                self.assertTrue(schema.get_class_id().get_package_name() ==
                                "MyPackage")


        self.console.destroy(10)



    def test_predicate_no_match(self):
        # create console
        # find agents
        # synchronous query for all schema by package name
        self.notifier = _testNotifier()
        self.console = qmf.qmfConsole.Console(notifier=self.notifier,
                                              agent_timeout=3)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.addConnection(self.conn)

        for aname in ["agent1", "agent2"]:
            agent = self.console.find_agent(aname, timeout=3)
            self.assertTrue(agent and agent.get_name() == aname)

            query = QmfQuery.create_predicate(QmfQuery.TARGET_SCHEMA,
                                              QmfQueryPredicate(
                    {QmfQuery.CMP_EQ: [SchemaClassId.KEY_PACKAGE, 
                                       "No-Such-Package"]}))

            schema_list = self.console.doQuery(agent, query)
            self.assertTrue(len(schema_list) == 0)

        self.console.destroy(10)


