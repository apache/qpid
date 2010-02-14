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
from qmf2.agent import(QmfAgentData, Agent, MethodCallParams)


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
        # the input value of cookie is returned in the response
        _meth.add_argument( "cookie", SchemaProperty(qmfTypes.TYPE_LSTR,
                                                     kwargs={"dir":"IO"}))
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

        # add an "unstructured" object to the Agent
        _obj2 = QmfAgentData(self.agent, _object_id="01545")
        _obj2.set_value("field1", "a value")
        _obj2.set_value("field2", 2)
        _obj2.set_value("field3", {"a":1, "map":2, "value":3})
        _obj2.set_value("field4", ["a", "list", "value"])
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

        # Agent application main processing loop
        while self.running:
            self.notifier.wait_for_work(None)
            wi = self.agent.get_next_workitem(timeout=0)
            while wi is not None:
                if wi.get_type() == WorkItem.METHOD_CALL:
                    mc = wi.get_params()
                    if not isinstance(mc, MethodCallParams):
                        raise Exception("Unexpected method call parameters")

                    if mc.get_name() == "set_meth":
                        obj = self.agent.get_object(mc.get_object_id(),
                                                    mc.get_schema_id())
                        if obj is None:
                            error_info = QmfData.create({"code": -2, 
                                                         "description":
                                                             "Bad Object Id."},
                                                        _object_id="_error")
                            self.agent.method_response(wi.get_handle(),
                                                       _error=error_info)
                        else:
                            obj.inc_value("method_call_count")
                            out_args = {"code" : 0}
                            if "cookie" in mc.get_args():
                                out_args["cookie"] = mc.get_args()["cookie"]
                            if "arg_int" in mc.get_args():
                                obj.set_value("set_int", mc.get_args()["arg_int"])
                            if "arg_str" in mc.get_args():
                                obj.set_value("set_string", mc.get_args()["arg_str"])
                            self.agent.method_response(wi.get_handle(),
                                                       out_args)
                    elif mc.get_name() == "a_method":
                        obj = self.agent.get_object(mc.get_object_id(),
                                                    mc.get_schema_id())
                        if obj is None:
                            error_info = QmfData.create({"code": -3, 
                                                         "description":
                                                             "Unknown object id."},
                                                        _object_id="_error")
                            self.agent.method_response(wi.get_handle(),
                                                       _error=error_info)
                        elif obj.get_object_id() != "01545":
                            error_info = QmfData.create( {"code": -4, 
                                                          "description":
                                                              "Unexpected id."},
                                                         _object_id="_error")
                            self.agent.method_response(wi.get_handle(),
                                                       _error=error_info)
                        else:
                            args = mc.get_args()
                            if ("arg1" in args and args["arg1"] == 1 and
                                "arg2" in args and args["arg2"] == "Now set!"
                                and "arg3" in args and args["arg3"] == 1966): 
                                out_args = {"code" : 0}
                                if "cookie" in mc.get_args():
                                    out_args["cookie"] = mc.get_args()["cookie"]
                                self.agent.method_response(wi.get_handle(),
                                                           out_args)
                            else:
                                error_info = QmfData.create(
                                    {"code": -5, 
                                     "description":
                                         "Bad Args."},
                                    _object_id="_error")
                                self.agent.method_response(wi.get_handle(),
                                                           _error=error_info)
                    else:
                        error_info = QmfData.create( {"code": -1, 
                                                     "description":
                                                         "Unknown method call."},
                                                     _object_id="_error")
                        self.agent.method_response(wi.get_handle(), _error=error_info)

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
        # one second agent heartbeat interval
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

    def test_described_obj(self):
        # create console
        # find agents
        # synchronous query for all objects in schema
        # method call on each object
        self.notifier = _testNotifier()
        self.console = qmf2.console.Console(notifier=self.notifier,
                                            agent_timeout=3)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.add_connection(self.conn)

        i_count = 0
        for aname in ["agent1", "agent2"]:
            agent = self.console.find_agent(aname, timeout=3)
            self.assertTrue(agent and agent.get_name() == aname)

            query = QmfQuery.create_wildcard(QmfQuery.TARGET_SCHEMA_ID)

            sid_list = self.console.do_query(agent, query)
            self.assertTrue(sid_list and len(sid_list) == 1)
            for sid in sid_list:
                t_params = {QmfData.KEY_SCHEMA_ID: sid}
                query = QmfQuery.create_wildcard(QmfQuery.TARGET_OBJECT,
                                                _target_params=t_params)
                obj_list = self.console.do_query(agent, query)
                self.assertTrue(len(obj_list) == 2)
                for obj in obj_list:
                    cookie = "cookie-" + str(i_count)
                    i_count += 1
                    mr = obj.invoke_method( "set_meth", 
                                            {"arg_int": -99,
                                             "arg_str": "Now set!",
                                             "cookie": cookie},
                                            _reply_handle=cookie,
                                            _timeout=3)
                    self.assertTrue(mr)

        # done, now wait for async responses

        r_count = 0
        while self.notifier.wait_for_work(3):
            wi = self.console.get_next_workitem(timeout=0)
            while wi is not None:
                r_count += 1
                self.assertTrue(wi.get_type() == WorkItem.METHOD_RESPONSE)
                reply = wi.get_params()
                self.assertTrue(isinstance(reply, qmf2.console.MethodResult))
                self.assertTrue(reply.succeeded())
                self.assertTrue(reply.get_argument("cookie") == wi.get_handle())

                wi = self.console.get_next_workitem(timeout=0)

        self.assertTrue(r_count == i_count)

        self.console.destroy(10)


    def test_managed_obj(self):
        # create console
        # find agents
        # synchronous query for a managed object
        # method call on each object
        self.notifier = _testNotifier()
        self.console = qmf2.console.Console(notifier=self.notifier,
                                              agent_timeout=3)
        self.conn = qpid.messaging.Connection(self.broker.host,
                                              self.broker.port,
                                              self.broker.user,
                                              self.broker.password)
        self.conn.connect()
        self.console.add_connection(self.conn)

        i_count = 0
        for aname in ["agent1", "agent2"]:
            agent = self.console.find_agent(aname, timeout=3)
            self.assertTrue(agent and agent.get_name() == aname)

            query = QmfQuery.create_id(QmfQuery.TARGET_OBJECT, "01545")
            obj_list = self.console.do_query(agent, query)

            self.assertTrue(isinstance(obj_list, type([])))
            self.assertTrue(len(obj_list) == 1)
            obj = obj_list[0]

            cookie = "cookie-" + str(i_count)
            i_count += 1
            mr = obj.invoke_method("a_method",
                                   {"arg1": 1,
                                    "arg2": "Now set!",
                                    "arg3": 1966,
                                    "cookie": cookie},
                                   _reply_handle=cookie,
                                   _timeout=3)
            self.assertTrue(mr)

        # done, now wait for async responses

        r_count = 0
        while self.notifier.wait_for_work(3):
            wi = self.console.get_next_workitem(timeout=0)
            while wi is not None:
                r_count += 1
                self.assertTrue(wi.get_type() == WorkItem.METHOD_RESPONSE)
                reply = wi.get_params()
                self.assertTrue(isinstance(reply, qmf2.console.MethodResult))
                self.assertTrue(reply.succeeded())
                self.assertTrue(reply.get_argument("cookie") == wi.get_handle())

                wi = self.console.get_next_workitem(timeout=0)

        self.assertTrue(r_count == i_count)

        self.console.destroy(10)
