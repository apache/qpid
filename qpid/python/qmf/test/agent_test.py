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
import unittest
from threading import Semaphore


from qpid.messaging import *
from qmf.qmfCommon import (qmfTypes, SchemaProperty, SchemaObjectClass, QmfData,
                       QmfEvent, SchemaMethod, Notifier, SchemaClassId,
                       WorkItem) 
from qmf.qmfAgent import (Agent, QmfAgentData)



class ExampleNotifier(Notifier):
    def __init__(self):
        self._sema4 = Semaphore(0)   # locked

    def indication(self):
        self._sema4.release()

    def waitForWork(self):
        print("Waiting for event...")
        self._sema4.acquire()
        print("...event present")




class QmfTest(unittest.TestCase):
    def test_begin(self):
        print("!!! being test")

    def test_end(self):
        print("!!! end test")


#
# An example agent application
#


if __name__ == '__main__':
    _notifier = ExampleNotifier()
    _agent = Agent( "qmf.testAgent", _notifier=_notifier )

    # Dynamically construct a class schema

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

    _agent.register_object_class(_schema)

    # instantiate managed data objects matching the schema

    _obj1 = QmfAgentData( _agent, _schema=_schema )
    _obj1.set_value("index1", 100)
    _obj1.set_value("index2", "a name" )
    _obj1.set_value("set_string", "UNSET")
    _obj1.set_value("set_int", 0)
    _obj1.set_value("query_count", 0)
    _obj1.set_value("method_call_count", 0)
    _agent.add_object( _obj1 )

    _agent.add_object( QmfAgentData( _agent, _schema=_schema,
                                     _values={"index1":99, 
                                              "index2": "another name",
                                              "set_string": "UNSET",
                                              "set_int": 0,
                                              "query_count": 0,
                                              "method_call_count": 0} ))

    # add an "unstructured" object to the Agent
    _obj2 = QmfAgentData(_agent, _object_id="01545")
    _obj2.set_value("field1", "a value")
    _obj2.set_value("field2", 2)
    _obj2.set_value("field3", {"a":1, "map":2, "value":3})
    _obj2.set_value("field4", ["a", "list", "value"])
    _agent.add_object(_obj2)


    ## Now connect to the broker

    _c = Connection("localhost")
    _c.connect()
    _agent.setConnection(_c)

    _error_data = QmfData.create({"code": -1, "description": "You made a boo-boo."})

    _done = False
    while not _done:
        # try:
        _notifier.waitForWork()

        _wi = _agent.get_next_workitem(timeout=0)
        while _wi:

            if _wi.get_type() == WorkItem.METHOD_CALL:
                mc = _wi.get_params()
            
                if mc.get_name() == "set_meth":
                    print("!!! Calling 'set_meth' on Object_id = %s" % mc.get_object_id())
                    print("!!! args='%s'" % str(mc.get_args()))
                    print("!!! userid=%s" % str(mc.get_user_id()))
                    print("!!! handle=%s" % _wi.get_handle())
                    _agent.method_response(_wi.get_handle(),
                                           {"rc1": 100, "rc2": "Success"})
                else:
                    print("!!! Unknown Method name = %s" % mc.get_name())
                    _agent.method_response(_wi.get_handle(), _error=_error_data)
            else:
                print("TBD: work item %d:%s" % (_wi.get_type(), str(_wi.get_params())))

            _agent.release_workitem(_wi)
            _wi = _agent.get_next_workitem(timeout=0)
            #    except:
            #        print( "shutting down...")
            #        _done = True

    print( "Removing connection... TBD!!!" )
    #_myConsole.remove_connection( _c, 10 )

    print( "Destroying agent... TBD!!!" )
    #_myConsole.destroy( 10 )

    print( "******** agent test done ********" )



