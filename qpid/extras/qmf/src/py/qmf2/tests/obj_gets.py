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
import datetime
from threading import Thread, Event

import qpid.messaging
from qmf2.common import (Notifier, SchemaObjectClass, SchemaClassId,
                         SchemaProperty, qmfTypes, SchemaMethod, QmfQuery,
                         QmfData)
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

        # Management Database 
        # - two different schema packages, 
        # - two classes within one schema package
        # - multiple objects per schema package+class
        # - two "undescribed" objects

        # "package1/class1"

        _schema = SchemaObjectClass( _classId=SchemaClassId("package1", "class1"),
                                     _desc="A test data schema - one",
                                     _object_id_names=["key"] )

        _schema.add_property( "key", SchemaProperty(qmfTypes.TYPE_LSTR))
        _schema.add_property( "count1", SchemaProperty(qmfTypes.TYPE_UINT32))
        _schema.add_property( "count2", SchemaProperty(qmfTypes.TYPE_UINT32))

        self.agent.register_object_class(_schema)

        _obj = QmfAgentData( self.agent,
                             _values={"key":"p1c1_key1"},
                             _schema=_schema)
        _obj.set_value("count1", 0)
        _obj.set_value("count2", 0)
        self.agent.add_object( _obj )

        _obj = QmfAgentData( self.agent, 
                             _values={"key":"p1c1_key2"},
                             _schema=_schema )
        _obj.set_value("count1", 9)
        _obj.set_value("count2", 10)
        self.agent.add_object( _obj )

        # "package1/class2"

        _schema = SchemaObjectClass( _classId=SchemaClassId("package1", "class2"),
                                     _desc="A test data schema - two",
                                     _object_id_names=["name"] )
        # add properties
        _schema.add_property( "name", SchemaProperty(qmfTypes.TYPE_LSTR))
        _schema.add_property( "string1", SchemaProperty(qmfTypes.TYPE_LSTR))

        self.agent.register_object_class(_schema)

        _obj = QmfAgentData( self.agent, 
                             _values={"name":"p1c2_name1"},
                             _schema=_schema )
        _obj.set_value("string1", "a data string")
        self.agent.add_object( _obj )


        # "package2/class1"

        _schema = SchemaObjectClass( _classId=SchemaClassId("package2", "class1"),
                                     _desc="A test data schema - second package",
                                     _object_id_names=["key"] )

        _schema.add_property( "key", SchemaProperty(qmfTypes.TYPE_LSTR))
        _schema.add_property( "counter", SchemaProperty(qmfTypes.TYPE_UINT32))

        self.agent.register_object_class(_schema)

        _obj = QmfAgentData( self.agent, 
                             _values={"key":"p2c1_key1"},
                             _schema=_schema )
        _obj.set_value("counter", 0)
        self.agent.add_object( _obj )

        _obj = QmfAgentData( self.agent, 
                             _values={"key":"p2c1_key2"},
                             _schema=_schema )
        _obj.set_value("counter", 2112)
        self.agent.add_object( _obj )


        # add two "unstructured" objects to the Agent

        _obj = QmfAgentData(self.agent, _object_id="undesc-1")
        _obj.set_value("field1", "a value")
        _obj.set_value("field2", 2)
        _obj.set_value("field3", {"a":1, "map":2, "value":3})
        _obj.set_value("field4", ["a", "list", "value"])
        self.agent.add_object(_obj)


        _obj = QmfAgentData(self.agent, _object_id="undesc-2")
        _obj.set_value("key-1", "a value")
        _obj.set_value("key-2", 2)
        self.agent.add_object(_obj)

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
    agent_count = 5

    def configure(self, config):
        self.config = config
        self.broker = config.broker
        self.defines = self.config.defines

    def setUp(self):
        self.agents = []
        for i in range(self.agent_count):
            agent = _agentApp("agent-" + str(i), self.broker, 1)
            agent.start_app()
            self.agents.append(agent)
        #print("!!!! STARTING TEST: %s" % datetime.datetime.utcnow())

    def tearDown(self):
        #print("!!!! STOPPING TEST: %s" % datetime.datetime.utcnow())
        for agent in self.agents:
            if agent is not None:
                agent.stop_app()


    def test_all_agents(self):
        # create console
        # find all agents
        # synchronous query for all objects by id
        # verify known object ids are returned
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
            aname = agent_app.agent.get_name()
            agent = self.console.find_agent(aname, timeout=3)
            self.assertTrue(agent and agent.get_name() == aname)

        # console has discovered all agents, now query all undesc-2 objects
        #print("!!!! STARTING GET: %s" % datetime.datetime.utcnow())
        objs = self.console.get_objects(_object_id="undesc-2", _timeout=5)
        #print("!!!! STOPPING GET: %s" % datetime.datetime.utcnow())
        self.assertTrue(len(objs) == self.agent_count)
        for obj in objs:
            self.assertTrue(obj.get_object_id() == "undesc-2")

        # now query all objects from schema "package1"
        #print("!!!! STARTING GET: %s" % datetime.datetime.utcnow())
        objs = self.console.get_objects(_pname="package1", _timeout=5)
        #print("!!!! STOPPING GET: %s" % datetime.datetime.utcnow())
        self.assertTrue(len(objs) == (self.agent_count * 3))
        for obj in objs:
            self.assertTrue(obj.get_schema_class_id().get_package_name() == "package1")

        # now query all objects from schema "package2"
        #print("!!!! STARTING GET: %s" % datetime.datetime.utcnow())
        objs = self.console.get_objects(_pname="package2", _timeout=5)
        #print("!!!! STOPPING GET: %s" % datetime.datetime.utcnow())
        self.assertTrue(len(objs) == (self.agent_count * 2))
        for obj in objs:
            self.assertTrue(obj.get_schema_class_id().get_package_name() == "package2")

        # now query all objects from schema "package1/class2"
        #print("!!!! STARTING GET: %s" % datetime.datetime.utcnow())
        objs = self.console.get_objects(_pname="package1", _cname="class2",
                                        _timeout=5)
        #print("!!!! STOPPING GET: %s" % datetime.datetime.utcnow())
        self.assertTrue(len(objs) == self.agent_count)
        for obj in objs:
            self.assertTrue(obj.get_schema_class_id().get_package_name() == "package1")
            self.assertTrue(obj.get_schema_class_id().get_class_name() == "class2")

        # given the schema identifier from the last query, repeat using the
        # specific schema id
        #print("!!!! STARTING GET: %s" % datetime.datetime.utcnow())
        schema_id = objs[0].get_schema_class_id()
        #print("!!!! STOPPING GET: %s" % datetime.datetime.utcnow())
        objs = self.console.get_objects(_schema_id=schema_id, _timeout=5)
        self.assertTrue(len(objs) == self.agent_count)
        for obj in objs:
            self.assertTrue(obj.get_schema_class_id() == schema_id)


        self.console.destroy(10)



    def test_agent_subset(self):
        # create console
        # find all agents
        # synchronous query for all objects by id
        # verify known object ids are returned
        self.notifier = _testNotifier()
        self.console = qmf2.console.Console(notifier=self.notifier,
                                              agent_timeout=3)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.add_connection(self.conn)

        agent_list = []
        for agent_app in self.agents:
            aname = agent_app.agent.get_name()
            agent = self.console.find_agent(aname, timeout=3)
            self.assertTrue(agent and agent.get_name() == aname)
            agent_list.append(agent)

        # Only use a subset of the agents:
        agent_list = agent_list[:len(agent_list)/2]

        # console has discovered all agents, now query all undesc-2 objects
        objs = self.console.get_objects(_object_id="undesc-2",
                                        _agents=agent_list, _timeout=5)
        self.assertTrue(len(objs) == len(agent_list))
        for obj in objs:
            self.assertTrue(obj.get_object_id() == "undesc-2")

        # now query all objects from schema "package1"
        objs = self.console.get_objects(_pname="package1",
                                        _agents=agent_list,
                                        _timeout=5)
        self.assertTrue(len(objs) == (len(agent_list) * 3))
        for obj in objs:
            self.assertTrue(obj.get_schema_class_id().get_package_name() == "package1")

        # now query all objects from schema "package2"
        objs = self.console.get_objects(_pname="package2", 
                                        _agents=agent_list,
                                        _timeout=5)
        self.assertTrue(len(objs) == (len(agent_list) * 2))
        for obj in objs:
            self.assertTrue(obj.get_schema_class_id().get_package_name() == "package2")

        # now query all objects from schema "package1/class2"
        objs = self.console.get_objects(_pname="package1", _cname="class2", 
                                        _agents=agent_list,
                                        _timeout=5)
        self.assertTrue(len(objs) == len(agent_list))
        for obj in objs:
            self.assertTrue(obj.get_schema_class_id().get_package_name() == "package1")
            self.assertTrue(obj.get_schema_class_id().get_class_name() == "class2")

        # given the schema identifier from the last query, repeat using the
        # specific schema id
        schema_id = objs[0].get_schema_class_id()
        objs = self.console.get_objects(_schema_id=schema_id, 
                                        _agents=agent_list,
                                        _timeout=5)
        self.assertTrue(len(objs) == len(agent_list))
        for obj in objs:
            self.assertTrue(obj.get_schema_class_id() == schema_id)


        self.console.destroy(10)



    def test_single_agent(self):
        # create console
        # find all agents
        # synchronous query for all objects by id
        # verify known object ids are returned
        self.notifier = _testNotifier()
        self.console = qmf2.console.Console(notifier=self.notifier,
                                              agent_timeout=3)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.add_connection(self.conn)

        agent_list = []
        for agent_app in self.agents:
            aname = agent_app.agent.get_name()
            agent = self.console.find_agent(aname, timeout=3)
            self.assertTrue(agent and agent.get_name() == aname)
            agent_list.append(agent)

        # Only use one agent
        agent = agent_list[0]

        # console has discovered all agents, now query all undesc-2 objects
        objs = self.console.get_objects(_object_id="undesc-2",
                                        _agents=agent, _timeout=5)
        self.assertTrue(len(objs) == 1)
        for obj in objs:
            self.assertTrue(obj.get_object_id() == "undesc-2")

        # now query all objects from schema "package1"
        objs = self.console.get_objects(_pname="package1",
                                        _agents=agent,
                                        _timeout=5)
        self.assertTrue(len(objs) == 3)
        for obj in objs:
            self.assertTrue(obj.get_schema_class_id().get_package_name() == "package1")

        # now query all objects from schema "package2"
        objs = self.console.get_objects(_pname="package2", 
                                        _agents=agent,
                                        _timeout=5)
        self.assertTrue(len(objs) == 2)
        for obj in objs:
            self.assertTrue(obj.get_schema_class_id().get_package_name() == "package2")

        # now query all objects from schema "package1/class2"
        objs = self.console.get_objects(_pname="package1", _cname="class2", 
                                        _agents=agent,
                                        _timeout=5)
        self.assertTrue(len(objs) == 1)
        for obj in objs:
            self.assertTrue(obj.get_schema_class_id().get_package_name() == "package1")
            self.assertTrue(obj.get_schema_class_id().get_class_name() == "class2")

        # given the schema identifier from the last query, repeat using the
        # specific schema id
        schema_id = objs[0].get_schema_class_id()
        objs = self.console.get_objects(_schema_id=schema_id, 
                                        _agents=agent,
                                        _timeout=5)
        self.assertTrue(len(objs) == 1)
        for obj in objs:
            self.assertTrue(obj.get_schema_class_id() == schema_id)


        self.console.destroy(10)



    def test_all_objs_by_oid(self):
        # create console
        # find all agents
        # synchronous query for all described objects by:
        #    oid & schema_id
        #    oid & package name
        #    oid & package and class name
        # verify known object ids are returned
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
            aname = agent_app.agent.get_name()
            agent = self.console.find_agent(aname, timeout=3)
            self.assertTrue(agent and agent.get_name() == aname)

        # now query all objects from schema "package1"
        #print("!!!! STARTING GET: %s" % datetime.datetime.utcnow())
        objs = self.console.get_objects(_pname="package1",
                                        _object_id="p1c1_key1", _timeout=5)
        #print("!!!! STOPPING GET: %s" % datetime.datetime.utcnow())
        self.assertTrue(len(objs) == self.agent_count)
        for obj in objs:
            self.assertTrue(obj.get_schema_class_id().get_package_name() == "package1")
            self.assertTrue(obj.get_schema_class_id().get_class_name() == "class1")
            self.assertTrue(obj.get_object_id() == "p1c1_key1")
        # mooch the schema for a later test
        schema_id_p1c1 = objs[0].get_schema_class_id()

        #print("!!!! STARTING GET: %s" % datetime.datetime.utcnow())
        objs = self.console.get_objects(_pname="package1",
                                        _object_id="p1c2_name1", _timeout=5)
        #print("!!!! STOPPING GET: %s" % datetime.datetime.utcnow())
        self.assertTrue(len(objs) == self.agent_count)
        for obj in objs:
            self.assertTrue(obj.get_schema_class_id().get_package_name() == "package1")
            self.assertTrue(obj.get_schema_class_id().get_class_name() == "class2")
            self.assertTrue(obj.get_object_id() == "p1c2_name1")

        #print("!!!! STARTING GET: %s" % datetime.datetime.utcnow())
        objs = self.console.get_objects(_pname="package2", _cname="class1",
                                        _object_id="p2c1_key1", _timeout=5)
        #print("!!!! STOPPING GET: %s" % datetime.datetime.utcnow())
        self.assertTrue(len(objs) == self.agent_count)
        for obj in objs:
            self.assertTrue(obj.get_schema_class_id().get_package_name() == "package2")
            self.assertTrue(obj.get_schema_class_id().get_class_name() == "class1")
            self.assertTrue(obj.get_object_id() == "p2c1_key1")

        #print("!!!! STARTING GET: %s" % datetime.datetime.utcnow())
        objs = self.console.get_objects(_schema_id=schema_id_p1c1,
                                        _object_id="p1c1_key2", _timeout=5)
        #print("!!!! STOPPING GET: %s" % datetime.datetime.utcnow())
        self.assertTrue(len(objs) == self.agent_count)
        for obj in objs:
            self.assertTrue(obj.get_schema_class_id().get_package_name() == "package1")
            self.assertTrue(obj.get_schema_class_id().get_class_name() == "class1")
            self.assertTrue(obj.get_object_id() == "p1c1_key2")

        # this should return all "undescribed" objects
        #print("!!!! STARTING GET: %s" % datetime.datetime.utcnow())
        objs = self.console.get_objects(_timeout=5)
        #print("!!!! STOPPING GET: %s" % datetime.datetime.utcnow())
        self.assertTrue(len(objs) == (self.agent_count * 2))
        for obj in objs:
            self.assertTrue(obj.get_object_id() == "undesc-1" or
                            obj.get_object_id() == "undesc-2")

        # these should fail
        #print("!!!! STARTING GET: %s" % datetime.datetime.utcnow())
        objs = self.console.get_objects(_schema_id=schema_id_p1c1,
                                        _object_id="does not exist",
                                        _timeout=5)
        #print("!!!! STOPPING GET: %s" % datetime.datetime.utcnow())
        self.assertTrue(objs == None)

        #print("!!!! STARTING GET: %s" % datetime.datetime.utcnow())
        objs = self.console.get_objects(_pname="package2",
                                        _object_id="does not exist",
                                        _timeout=5)
        #print("!!!! STOPPING GET: %s" % datetime.datetime.utcnow())
        self.assertTrue(objs == None)

        #print("!!!! STARTING GET: %s" % datetime.datetime.utcnow())
        objs = self.console.get_objects(_pname="package3",
                                        _object_id="does not exist",
                                        _timeout=5)
        #print("!!!! STOPPING GET: %s" % datetime.datetime.utcnow())
        self.assertTrue(objs == None)

        self.console.destroy(10)


    def test_wildcard_schema_id(self):
        # create console
        # find all agents
        # synchronous query for all described objects by:
        #    oid & wildcard schema_id
        #    wildcard schema_id
        # verify known object ids are returned
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
            aname = agent_app.agent.get_name()
            agent = self.console.find_agent(aname, timeout=3)
            self.assertTrue(agent and agent.get_name() == aname)

        wild_schema_id = SchemaClassId("package1", "class1")
        objs = self.console.get_objects(_schema_id=wild_schema_id, _timeout=5)
        self.assertTrue(len(objs) == (self.agent_count * 2))
        for obj in objs:
            self.assertTrue(obj.get_schema_class_id().get_package_name() == "package1")
            self.assertTrue(obj.get_schema_class_id().get_class_name() == "class1")

        wild_schema_id = SchemaClassId("package1", "class2")
        objs = self.console.get_objects(_schema_id=wild_schema_id, _timeout=5)
        self.assertTrue(len(objs) == self.agent_count)
        for obj in objs:
            self.assertTrue(obj.get_schema_class_id().get_package_name() == "package1")
            self.assertTrue(obj.get_schema_class_id().get_class_name() == "class2")
            self.assertTrue(obj.get_object_id() == "p1c2_name1")

        wild_schema_id = SchemaClassId("package2", "class1")
        objs = self.console.get_objects(_schema_id=wild_schema_id, _timeout=5)
        self.assertTrue(len(objs) == (self.agent_count * 2))
        for obj in objs:
            self.assertTrue(obj.get_schema_class_id().get_package_name() == "package2")
            self.assertTrue(obj.get_schema_class_id().get_class_name() == "class1")

        wild_schema_id = SchemaClassId("package1", "class1")
        objs = self.console.get_objects(_schema_id=wild_schema_id,
                                        _object_id="p1c1_key2", _timeout=5)
        self.assertTrue(len(objs) == self.agent_count)
        for obj in objs:
            self.assertTrue(obj.get_schema_class_id().get_package_name() == "package1")
            self.assertTrue(obj.get_schema_class_id().get_class_name() == "class1")
            self.assertTrue(obj.get_object_id() == "p1c1_key2")

        # should fail
        objs = self.console.get_objects(_schema_id=wild_schema_id,
                                        _object_id="does not exist",
                                        _timeout=5)
        self.assertTrue(objs == None)

        wild_schema_id = SchemaClassId("package2", "class1")
        objs = self.console.get_objects(_schema_id=wild_schema_id,
                                        _object_id="p2c1_key2", _timeout=5)
        self.assertTrue(len(objs) == self.agent_count)
        for obj in objs:
            self.assertTrue(obj.get_schema_class_id().get_package_name() == "package2")
            self.assertTrue(obj.get_schema_class_id().get_class_name() == "class1")
            self.assertTrue(obj.get_object_id() == "p2c1_key2")

        # should fail
        wild_schema_id = SchemaClassId("package1", "bad-class")
        objs = self.console.get_objects(_schema_id=wild_schema_id,
                                        _object_id="p1c1_key2", _timeout=5)
        self.assertTrue(objs == None)

        self.console.destroy(10)

