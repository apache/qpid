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
import logging
import time
from threading import Semaphore


from qpid.messaging import *
from qmf.qmfCommon import (Notifier, QmfQuery, QmfQueryPredicate, MsgKey,
                       SchemaClassId, SchemaClass, QmfData) 
from qmf.qmfConsole import Console


class ExampleNotifier(Notifier):
    def __init__(self):
        self._sema4 = Semaphore(0)   # locked

    def indication(self):
        self._sema4.release()

    def waitForWork(self):
        print("Waiting for event...")
        self._sema4.acquire()
        print("...event present")


logging.getLogger().setLevel(logging.INFO)

print( "Starting Connection" )
_c = Connection("localhost")
_c.connect()

print( "Starting Console" )

_notifier = ExampleNotifier()
_myConsole = Console(notifier=_notifier)
_myConsole.addConnection( _c )

# Allow discovery only for the agent named "qmf.testAgent"
# @todo: replace "manual" query construction with 
# a formal class-based Query API
_query = QmfQuery.create_predicate(QmfQuery.TARGET_AGENT, 
                                   QmfQueryPredicate({QmfQuery.CMP_EQ:
                                                          [QmfQuery.KEY_AGENT_NAME,
                                                           "qmf.testAgent"]}))
_myConsole.enable_agent_discovery(_query)

_done = False
while not _done:
#    try:
    _notifier.waitForWork()

    _wi = _myConsole.get_next_workitem(timeout=0)
    while _wi:
        print("!!! work item received %d:%s" % (_wi.get_type(),
                                                str(_wi.get_params())))


        if _wi.get_type() == _wi.AGENT_ADDED:
            _agent = _wi.get_params().get("agent")
            if not _agent:
                print("!!!! AGENT IN REPLY IS NULL !!! ")

            _query = QmfQuery.create_wildcard(QmfQuery.TARGET_OBJECT_ID)
            oid_list = _myConsole.doQuery(_agent, _query)

            print("!!!************************** REPLY=%s" % oid_list)

            for oid in oid_list:
                _query = QmfQuery.create_id(QmfQuery.TARGET_OBJECT, 
                                            oid)
                obj_list = _myConsole.doQuery(_agent, _query)

                print("!!!************************** REPLY=%s" % obj_list)

                if obj_list is None:
                    obj_list={}

                for obj in obj_list:
                    resp = obj.invoke_method( "set_meth", 
                                              {"arg_int": -11,
                                               "arg_str": "are we not goons?"},
                                              None,
                                              3)
                    if resp is None:
                        print("!!!*** NO RESPONSE FROM METHOD????") 
                    else:
                        print("!!! method succeeded()=%s" % resp.succeeded())
                        print("!!! method exception()=%s" % resp.get_exception())
                        print("!!! method get args() = %s" % resp.get_arguments())

                    if not obj.is_described():
                        resp = obj.invoke_method( "bad method", 
                                                  {"arg_int": -11,
                                                   "arg_str": "are we not goons?"},
                                                  None,
                                                  3)
                        if resp is None:
                            print("!!!*** NO RESPONSE FROM METHOD????") 
                        else:
                            print("!!! method succeeded()=%s" % resp.succeeded())
                            print("!!! method exception()=%s" % resp.get_exception())
                            print("!!! method get args() = %s" % resp.get_arguments())


            #---------------------------------
            #_query = QmfQuery.create_id(QmfQuery.TARGET_OBJECT, "99another name")

            #obj_list = _myConsole.doQuery(_agent, _query)

            #---------------------------------

            # _query = QmfQuery.create_wildcard(QmfQuery.TARGET_PACKAGES)

            # package_list = _myConsole.doQuery(_agent, _query)

            # for pname in package_list:
            #     print("!!! Querying for schema from package: %s" % pname)
            #     _query = QmfQuery.create_predicate(QmfQuery.TARGET_SCHEMA_ID,
            #                                        QmfQueryPredicate(
            #             {QmfQuery.CMP_EQ: [SchemaClassId.KEY_PACKAGE, pname]}))

            #     schema_id_list = _myConsole.doQuery(_agent, _query)
            #     for sid in schema_id_list:
            #         _query = QmfQuery.create_predicate(QmfQuery.TARGET_SCHEMA,
            #                                            QmfQueryPredicate(
            #                 {QmfQuery.CMP_EQ: [SchemaClass.KEY_SCHEMA_ID,
            #                                    sid.map_encode()]}))

            #         schema_list = _myConsole.doQuery(_agent, _query)
            #         for schema in schema_list:
            #             sid = schema.get_class_id()
            #             _query = QmfQuery.create_predicate(
            #                 QmfQuery.TARGET_OBJECT_ID,
            #                 QmfQueryPredicate({QmfQuery.CMP_EQ:
            #                                        [QmfData.KEY_SCHEMA_ID,
            #                                         sid.map_encode()]}))

            #             oid_list = _myConsole.doQuery(_agent, _query)
            #             for oid in oid_list:
            #                 _query = QmfQuery.create_id(
            #                     QmfQuery.TARGET_OBJECT, oid)
            #                 _reply = _myConsole.doQuery(_agent, _query)

            #                 print("!!!************************** REPLY=%s" % _reply)


        _myConsole.release_workitem(_wi)
        _wi = _myConsole.get_next_workitem(timeout=0)
#    except:
#        logging.info( "shutting down..." )
#        _done = True

print( "Removing connection" )
_myConsole.removeConnection( _c, 10 )

print( "Destroying console:" )
_myConsole.destroy( 10 )

print( "******** console test done ********" )
