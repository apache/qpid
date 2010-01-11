import logging
import time
from threading import Semaphore


from qpid.messaging import *
from qmfCommon import (qmfTypes, SchemaProperty, SchemaObjectClass, QmfData,
                       QmfEvent, SchemaMethod, Notifier, SchemaClassId) 
from qmfAgent import (Agent, QmfAgentData)



class ExampleNotifier(Notifier):
    def __init__(self):
        self._sema4 = Semaphore(0)   # locked

    def indication(self):
        self._sema4.release()

    def waitForWork(self):
        print("Waiting for event...")
        self._sema4.acquire()
        print("...event present")



#
# An example agent application
#

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

_obj = QmfAgentData( _agent, _schema=_schema )
_obj.set_value("index1", 100)
_obj.set_value("index2", "a name" )
_obj.set_value("set_string", "UNSET")
_obj.set_value("set_int", 0)
_obj.set_value("query_count", 0)
_obj.set_value("method_call_count", 0)
_agent.add_object( _obj )

_agent.add_object( QmfAgentData( _agent, _schema=_schema,
                                _values={"index1":99, 
                                         "index2": "another name",
                                         "set_string": "UNSET",
                                         "set_int": 0,
                                         "query_count": 0,
                                         "method_call_count": 0} ))

## Now connect to the broker

_c = Connection("localhost")
_c.connect()
_agent.setConnection(_c)


_done = False
while not _done:
    try:
        _notifier.waitForWork()

        _wi = _agent.getNextWorkItem(timeout=0)
        while _wi:
            print("work item %d:%s" % (_wi.getType(), str(_wi.getParams())))
            _agent.releaseWorkItem(_wi)
            _wi = _agent.getNextWorkItem(timeout=0)
    except:
        print( "shutting down..." )
        _done = True

print( "Removing connection... TBD!!!" )
#_myConsole.remove_connection( _c, 10 )

print( "Destroying agent... TBD!!!" )
#_myConsole.destroy( 10 )

print( "******** agent test done ********" )



