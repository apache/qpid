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
import time
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
                           heartbeat_interval=heartbeat,
                           max_duration=10,
                           default_duration=7,
                           min_duration=5,
                           min_interval=1,
                           default_interval=2)

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

        _obj = QmfAgentData( self.agent, 
                             _values={"name":"p1c2_name2"},
                             _schema=_schema )
        _obj.set_value("string1", "another data string")
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


    def test_sync_by_schema(self):
        # create console
        # find all agents
        # subscribe to changes to any object in package1/class1
        # should succeed
        self.notifier = _testNotifier()
        self.console = qmf2.console.Console(notifier=self.notifier,
                                              agent_timeout=3)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.add_connection(self.conn)

        subscriptions = []
        index = 0

        # query to match all objects in schema package1/class1
        sid = SchemaClassId.create("package1", "class1")
        t_params = {QmfData.KEY_SCHEMA_ID: sid}
        query = QmfQuery.create_wildcard(QmfQuery.TARGET_OBJECT,
                                         _target_params=t_params)
        for agent_app in self.agents:
            aname = agent_app.agent.get_name()
            agent = self.console.find_agent(aname, timeout=3)
            self.assertTrue(agent and agent.get_name() == aname)

            # now subscribe to agent

            sp = self.console.create_subscription(agent,
                                                  query,
                                                  index)
            self.assertTrue(isinstance(sp, qmf2.console.SubscribeParams))
            self.assertTrue(sp.succeeded())
            self.assertTrue(sp.get_error() == None)
            self.assertTrue(sp.get_duration() == 10)
            self.assertTrue(sp.get_publish_interval() == 2)

            subscriptions.append([sp, 0])
            index += 1

        # now wait for the (2 * interval) and count the updates
        r_count = 0
        while self.notifier.wait_for_work(4):
            wi = self.console.get_next_workitem(timeout=0)
            while wi is not None:
                r_count += 1
                self.assertTrue(wi.get_type() == WorkItem.SUBSCRIBE_INDICATION)
                reply = wi.get_params()
                self.assertTrue(isinstance(reply, type([])))
                self.assertTrue(len(reply) == 2)
                for obj in reply:
                    self.assertTrue(isinstance(obj, QmfData))
                    self.assertTrue(obj.get_object_id() == "p1c1_key2" or
                                    obj.get_object_id() == "p1c1_key1")
                sid = reply[0].get_schema_class_id()
                self.assertTrue(isinstance(sid, SchemaClassId))
                self.assertTrue(sid.get_package_name() == "package1")
                self.assertTrue(sid.get_class_name() == "class1")

                self.assertTrue(wi.get_handle() < len(subscriptions))
                subscriptions[wi.get_handle()][1] += 1

                self.console.release_workitem(wi)

                wi = self.console.get_next_workitem(timeout=0)

        # for now, I expect 5 publish per subscription
        self.assertTrue(r_count == 5 * len(subscriptions))
        for ii in range(len(subscriptions)):
            self.assertTrue(subscriptions[ii][1] == 5)

        self.console.destroy(10)


    def test_sync_by_obj_id(self):
        # create console
        # find all agents
        # subscribe to changes to any object in package1/class1
        # should succeed
        self.notifier = _testNotifier()
        self.console = qmf2.console.Console(notifier=self.notifier,
                                              agent_timeout=3)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.add_connection(self.conn)

        subscriptions = []
        index = 0

        # query to match all objects in schema package1/class1
        # sid = SchemaClassId.create("package1", "class1")
        # t_params = {QmfData.KEY_SCHEMA_ID: sid}
        query = QmfQuery.create_id_object("undesc-2")

        for agent_app in self.agents:
            aname = agent_app.agent.get_name()
            agent = self.console.find_agent(aname, timeout=3)
            self.assertTrue(agent and agent.get_name() == aname)

            # now subscribe to agent

            sp = self.console.create_subscription(agent,
                                                  query,
                                                  index)
            self.assertTrue(isinstance(sp, qmf2.console.SubscribeParams))
            self.assertTrue(sp.succeeded())
            self.assertTrue(sp.get_error() == None)

            subscriptions.append([sp, 0])
            index += 1

        # now wait for all subscriptions to expire (2x interval w/o
        # indications)
        r_count = 0
        while self.notifier.wait_for_work(4):
            wi = self.console.get_next_workitem(timeout=0)
            while wi is not None:
                r_count += 1
                self.assertTrue(wi.get_type() == WorkItem.SUBSCRIBE_INDICATION)
                reply = wi.get_params()
                self.assertTrue(isinstance(reply, type([])))
                self.assertTrue(len(reply) == 1)
                self.assertTrue(isinstance(reply[0], QmfData))
                self.assertTrue(reply[0].get_object_id() == "undesc-2")
                # print("!!! get_params() = %s" % wi.get_params())
                self.assertTrue(wi.get_handle() < len(subscriptions))
                subscriptions[wi.get_handle()][1] += 1
                # self.assertTrue(isinstance(reply, qmf2.console.MethodResult))
                # self.assertTrue(reply.succeeded())
                # self.assertTrue(reply.get_argument("cookie") ==
                # wi.get_handle())
                self.console.release_workitem(wi)

                wi = self.console.get_next_workitem(timeout=0)

        # for now, I expect 5 publish per subscription
        self.assertTrue(r_count == 5 * len(subscriptions))
        #for ii in range(len(subscriptions)):
        #    self.assertTrue(subscriptions[ii][1] == 5)

        self.console.destroy(10)


    def test_sync_by_obj_id_schema(self):
        # create console
        # find all agents
        # subscribe to changes to any object in package1/class1
        # should succeed
        self.notifier = _testNotifier()
        self.console = qmf2.console.Console(notifier=self.notifier,
                                              agent_timeout=3)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.add_connection(self.conn)

        subscriptions = []
        index = 0

        # query to match object "p2c1_key2" in schema package2/class1
        sid = SchemaClassId.create("package2", "class1")
        query = QmfQuery.create_id_object("p2c1_key2", sid)

        for agent_app in self.agents:
            aname = agent_app.agent.get_name()
            agent = self.console.find_agent(aname, timeout=3)
            self.assertTrue(agent and agent.get_name() == aname)

            # now subscribe to agent

            sp = self.console.create_subscription(agent,
                                                  query,
                                                  index)
            self.assertTrue(isinstance(sp, qmf2.console.SubscribeParams))
            self.assertTrue(sp.succeeded())
            self.assertTrue(sp.get_error() == None)

            subscriptions.append([sp, 0])
            index += 1

        # now wait for all subscriptions to expire (2x interval w/o
        # indications)
        r_count = 0
        while self.notifier.wait_for_work(4):
            wi = self.console.get_next_workitem(timeout=0)
            while wi is not None:
                r_count += 1
                self.assertTrue(wi.get_type() == WorkItem.SUBSCRIBE_INDICATION)
                reply = wi.get_params()
                self.assertTrue(isinstance(reply, type([])))
                self.assertTrue(len(reply) == 1)
                self.assertTrue(isinstance(reply[0], QmfData))
                self.assertTrue(reply[0].get_object_id() == "p2c1_key2")
                sid = reply[0].get_schema_class_id()
                self.assertTrue(isinstance(sid, SchemaClassId))
                self.assertTrue(sid.get_package_name() == "package2")
                self.assertTrue(sid.get_class_name() == "class1")
                self.assertTrue(wi.get_handle() < len(subscriptions))
                subscriptions[wi.get_handle()][1] += 1
                # self.assertTrue(isinstance(reply, qmf2.console.MethodResult))
                # self.assertTrue(reply.succeeded())
                # self.assertTrue(reply.get_argument("cookie") ==
                # wi.get_handle())
                self.console.release_workitem(wi)

                wi = self.console.get_next_workitem(timeout=0)

        # for now, I expect 5 publish per subscription
        self.assertTrue(r_count == 5 * len(subscriptions))
        #for ii in range(len(subscriptions)):
        #    self.assertTrue(subscriptions[ii][1] == 5)

        self.console.destroy(10)



    def test_sync_refresh(self):
        # create console
        # find one agent
        # subscribe to changes to any object in package1/class1
        # after 3 data indications, refresh
        # verify > 5 more data indications received
        self.notifier = _testNotifier()
        self.console = qmf2.console.Console(notifier=self.notifier,
                                              agent_timeout=3)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.add_connection(self.conn)

        # query to match object "p2c1_key2" in schema package2/class1
        sid = SchemaClassId.create("package2", "class1")
        query = QmfQuery.create_id_object("p2c1_key2", sid)

        agent_app = self.agents[0]
        aname = agent_app.agent.get_name()
        agent = self.console.find_agent(aname, timeout=3)
        self.assertTrue(agent and agent.get_name() == aname)

        # setup subscription on agent

        sp = self.console.create_subscription(agent,
                                              query,
                                              "my-handle")
        self.assertTrue(isinstance(sp, qmf2.console.SubscribeParams))
        self.assertTrue(sp.succeeded())
        self.assertTrue(sp.get_error() == None)

        # refresh after three subscribe indications, count all
        # indications to verify refresh worked
        r_count = 0
        while self.notifier.wait_for_work(4):
            wi = self.console.get_next_workitem(timeout=0)
            while wi is not None:
                r_count += 1
                self.assertTrue(wi.get_type() == WorkItem.SUBSCRIBE_INDICATION)
                reply = wi.get_params()
                self.assertTrue(isinstance(reply, type([])))
                self.assertTrue(len(reply) == 1)
                self.assertTrue(isinstance(reply[0], QmfData))
                self.assertTrue(reply[0].get_object_id() == "p2c1_key2")
                sid = reply[0].get_schema_class_id()
                self.assertTrue(isinstance(sid, SchemaClassId))
                self.assertTrue(sid.get_package_name() == "package2")
                self.assertTrue(sid.get_class_name() == "class1")
                self.assertTrue(wi.get_handle() == "my-handle")

                self.console.release_workitem(wi)

                if r_count == 3:
                    rp = self.console.refresh_subscription(sp.get_subscription_id())
                    self.assertTrue(isinstance(rp, qmf2.console.SubscribeParams))

                wi = self.console.get_next_workitem(timeout=0)

        # for now, I expect 5 publish per subscription
        self.assertTrue(r_count > 5)
        # print("!!! total r_count=%d" % r_count)
        #for ii in range(len(subscriptions)):
        #    self.assertTrue(subscriptions[ii][1] == 5)

        self.console.destroy(10)



    def test_sync_cancel(self):
        # create console
        # find one agent
        # subscribe to changes to any object in package1/class1
        # after 2 data indications, cancel subscription
        # verify < 5 data indications received
        self.notifier = _testNotifier()
        self.console = qmf2.console.Console(notifier=self.notifier,
                                              agent_timeout=3)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.add_connection(self.conn)

        # query to match object "p2c1_key2" in schema package2/class1
        sid = SchemaClassId.create("package2", "class1")
        query = QmfQuery.create_id_object("p2c1_key2", sid)

        agent_app = self.agents[0]
        aname = agent_app.agent.get_name()
        agent = self.console.find_agent(aname, timeout=3)
        self.assertTrue(agent and agent.get_name() == aname)

        # setup subscription on agent

        sp = self.console.create_subscription(agent,
                                              query,
                                              "my-handle")
        self.assertTrue(isinstance(sp, qmf2.console.SubscribeParams))
        self.assertTrue(sp.succeeded())
        self.assertTrue(sp.get_error() == None)

        # refresh after three subscribe indications, count all
        # indications to verify refresh worked
        r_count = 0
        while self.notifier.wait_for_work(4):
            wi = self.console.get_next_workitem(timeout=0)
            while wi is not None:
                r_count += 1
                self.assertTrue(wi.get_type() == WorkItem.SUBSCRIBE_INDICATION)
                reply = wi.get_params()
                self.assertTrue(isinstance(reply, type([])))
                self.assertTrue(len(reply) == 1)
                self.assertTrue(isinstance(reply[0], QmfData))
                self.assertTrue(reply[0].get_object_id() == "p2c1_key2")
                sid = reply[0].get_schema_class_id()
                self.assertTrue(isinstance(sid, SchemaClassId))
                self.assertTrue(sid.get_package_name() == "package2")
                self.assertTrue(sid.get_class_name() == "class1")
                self.assertTrue(wi.get_handle() == "my-handle")

                self.console.release_workitem(wi)

                if r_count == 3:
                    self.console.cancel_subscription(sp.get_subscription_id())

                wi = self.console.get_next_workitem(timeout=0)

        # for now, I expect 5 publish per subscription full duration
        self.assertTrue(r_count < 5)
        #for ii in range(len(subscriptions)):
        #    self.assertTrue(subscriptions[ii][1] == 5)

        self.console.destroy(10)


    def test_async_by_obj_id_schema(self):
        # create console
        # find one agent
        # async subscribe to changes to any object in package1/class1
        self.notifier = _testNotifier()
        self.console = qmf2.console.Console(notifier=self.notifier,
                                              agent_timeout=3)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.add_connection(self.conn)

        # query to match object "p2c1_key2" in schema package2/class1
        sid = SchemaClassId.create("package2", "class1")
        query = QmfQuery.create_id_object("p2c1_key2", sid)

        agent_app = self.agents[0]
        aname = agent_app.agent.get_name()
        agent = self.console.find_agent(aname, timeout=3)
        self.assertTrue(agent and agent.get_name() == aname)

        # setup subscription on agent

        rc = self.console.create_subscription(agent,
                                              query,
                                              "my-handle",
                                              _blocking=False)
        self.assertTrue(rc)

        r_count = 0
        sp = None
        while self.notifier.wait_for_work(4):
            wi = self.console.get_next_workitem(timeout=0)
            while wi is not None:
                r_count += 1
                if wi.get_type() == WorkItem.SUBSCRIBE_RESPONSE:
                    self.assertTrue(wi.get_handle() == "my-handle")
                    sp = wi.get_params()
                    self.assertTrue(isinstance(sp, qmf2.console.SubscribeParams))
                    self.assertTrue(sp.succeeded())
                    self.assertTrue(sp.get_error() == None)
                else:
                    self.assertTrue(wi.get_type() ==
                                    WorkItem.SUBSCRIBE_INDICATION)
                    # sp better be set up by now!
                    self.assertTrue(isinstance(sp, qmf2.console.SubscribeParams))
                    reply = wi.get_params()
                    self.assertTrue(isinstance(reply, type([])))
                    self.assertTrue(len(reply) == 1)
                    self.assertTrue(isinstance(reply[0], QmfData))
                    self.assertTrue(reply[0].get_object_id() == "p2c1_key2")
                    sid = reply[0].get_schema_class_id()
                    self.assertTrue(isinstance(sid, SchemaClassId))
                    self.assertTrue(sid.get_package_name() == "package2")
                    self.assertTrue(sid.get_class_name() == "class1")
                    self.assertTrue(wi.get_handle() == "my-handle")

                self.console.release_workitem(wi)

                wi = self.console.get_next_workitem(timeout=0)

        # for now, I expect 5 publish per subscription
        self.assertTrue(r_count == 6)

        self.console.destroy(10)

    def test_async_refresh(self):
        # create console
        # find one agent
        # async subscribe to changes to any object in package1/class1
        # refresh after third data indication
        self.notifier = _testNotifier()
        self.console = qmf2.console.Console(notifier=self.notifier,
                                              agent_timeout=3)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.add_connection(self.conn)

        # query to match object "p2c1_key2" in schema package2/class1
        sid = SchemaClassId.create("package2", "class1")
        query = QmfQuery.create_id_object("p2c1_key2", sid)

        agent_app = self.agents[0]
        aname = agent_app.agent.get_name()
        agent = self.console.find_agent(aname, timeout=3)
        self.assertTrue(agent and agent.get_name() == aname)

        # setup subscription on agent

        rc = self.console.create_subscription(agent,
                                              query,
                                              "my-handle",
                                              _blocking=False)
        self.assertTrue(rc)

        # refresh after three subscribe indications, count all
        # indications to verify refresh worked
        r_count = 0
        sp = None
        rp = None
        while self.notifier.wait_for_work(4):
            wi = self.console.get_next_workitem(timeout=0)
            while wi is not None:
                r_count += 1
                if wi.get_type() == WorkItem.SUBSCRIBE_RESPONSE:
                    self.assertTrue(wi.get_handle() == "my-handle")
                    sp = wi.get_params()
                    self.assertTrue(isinstance(sp, qmf2.console.SubscribeParams))
                    self.assertTrue(sp.succeeded())
                    self.assertTrue(sp.get_error() == None)
                elif wi.get_type() == WorkItem.RESUBSCRIBE_RESPONSE:
                    self.assertTrue(wi.get_handle() == "my-handle")
                    rp = wi.get_params()
                    self.assertTrue(isinstance(rp, qmf2.console.SubscribeParams))
                    self.assertTrue(rp.succeeded())
                    self.assertTrue(rp.get_error() == None)
                else:
                    self.assertTrue(wi.get_type() ==
                                    WorkItem.SUBSCRIBE_INDICATION)
                    # sp better be set up by now!
                    self.assertTrue(isinstance(sp, qmf2.console.SubscribeParams))
                    reply = wi.get_params()
                    self.assertTrue(isinstance(reply, type([])))
                    self.assertTrue(len(reply) == 1)
                    self.assertTrue(isinstance(reply[0], QmfData))
                    self.assertTrue(reply[0].get_object_id() == "p2c1_key2")
                    sid = reply[0].get_schema_class_id()
                    self.assertTrue(isinstance(sid, SchemaClassId))
                    self.assertTrue(sid.get_package_name() == "package2")
                    self.assertTrue(sid.get_class_name() == "class1")
                    self.assertTrue(wi.get_handle() == "my-handle")

                    if r_count == 4:  # + 1 for subscribe reply
                        rp = self.console.refresh_subscription(sp.get_subscription_id())
                        self.assertTrue(rp)

                self.console.release_workitem(wi)

                wi = self.console.get_next_workitem(timeout=0)

        # for now, I expect 5 publish per subscription, + 2 replys
        self.assertTrue(r_count > 7)

        self.console.destroy(10)


    def test_async_cancel(self):
        # create console
        # find one agent
        # async subscribe to changes to any object in package1/class1
        # cancel after first data indication
        self.notifier = _testNotifier()
        self.console = qmf2.console.Console(notifier=self.notifier,
                                              agent_timeout=3)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.add_connection(self.conn)

        # query to match object "p2c1_key2" in schema package2/class1
        sid = SchemaClassId.create("package2", "class1")
        query = QmfQuery.create_id_object("p2c1_key2", sid)

        agent_app = self.agents[0]
        aname = agent_app.agent.get_name()
        agent = self.console.find_agent(aname, timeout=3)
        self.assertTrue(agent and agent.get_name() == aname)

        # setup subscription on agent

        rc = self.console.create_subscription(agent,
                                              query,
                                              "my-handle",
                                              _blocking=False)
        self.assertTrue(rc)

        # refresh after three subscribe indications, count all
        # indications to verify refresh worked
        r_count = 0
        sp = None
        rp = None
        while self.notifier.wait_for_work(4):
            wi = self.console.get_next_workitem(timeout=0)
            while wi is not None:
                r_count += 1
                if wi.get_type() == WorkItem.SUBSCRIBE_RESPONSE:
                    self.assertTrue(wi.get_handle() == "my-handle")
                    sp = wi.get_params()
                    self.assertTrue(isinstance(sp, qmf2.console.SubscribeParams))
                    self.assertTrue(sp.succeeded())
                    self.assertTrue(sp.get_error() == None)
                else:
                    self.assertTrue(wi.get_type() ==
                                    WorkItem.SUBSCRIBE_INDICATION)
                    # sp better be set up by now!
                    self.assertTrue(isinstance(sp, qmf2.console.SubscribeParams))
                    reply = wi.get_params()
                    self.assertTrue(isinstance(reply, type([])))
                    self.assertTrue(len(reply) == 1)
                    self.assertTrue(isinstance(reply[0], QmfData))
                    self.assertTrue(reply[0].get_object_id() == "p2c1_key2")
                    sid = reply[0].get_schema_class_id()
                    self.assertTrue(isinstance(sid, SchemaClassId))
                    self.assertTrue(sid.get_package_name() == "package2")
                    self.assertTrue(sid.get_class_name() == "class1")
                    self.assertTrue(wi.get_handle() == "my-handle")

                    self.console.cancel_subscription(sp.get_subscription_id())

                self.console.release_workitem(wi)

                wi = self.console.get_next_workitem(timeout=0)

        # for now, I expect 1 subscribe reply and 1 data_indication
        self.assertTrue(r_count == 2)

        self.console.destroy(10)
