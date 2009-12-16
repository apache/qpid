import logging
import time
from threading import Semaphore


from qpid.messaging import *
from qmfCommon import (AgentId, SchemaEventClassFactory, qmfTypes, SchemaProperty,
                       SchemaObjectClass, ObjectIdFactory, QmfData, QmfDescribed,
                       QmfDescribedFactory, QmfManaged, QmfManagedFactory, QmfDataFactory,
                       QmfEvent, SchemaMethod, Notifier)
from qmfAgent import (Agent, QmfAgentData)



class ExampleNotifier(Notifier):
    def __init__(self):
        self._sema4 = Semaphore(0)   # locked

    def indication(self):
        self._sema4.release()

    def waitForWork(self):
        logging.error("Waiting for event...")
        self._sema4.acquire()
        logging.error("...event present")



#
# An example agent application
#

_notifier = ExampleNotifier()
_agent = Agent( "redhat.com", "qmf", "testAgent", _notifier )
        
# Dynamically construct a class schema

_schema = SchemaObjectClass( "MyPackage", "MyClass",
                             desc="A test data schema",
                             _pkey=["index1", "index2"] )
# add properties
_schema.addProperty( "index1",
                     SchemaProperty(qmfTypes.TYPE_UINT8))
_schema.addProperty( "index2",
                     SchemaProperty(qmfTypes.TYPE_LSTR))

# add method
_meth = SchemaMethod( _desc="A test method" )
_meth.addArgument( "arg1", SchemaProperty(qmfTypes.TYPE_UINT32) )
_meth.addArgument( "arg2", SchemaProperty(qmfTypes.TYPE_LSTR) )
_meth.addArgument( "arg3", SchemaProperty(qmfTypes.TYPE_BOOL) )

_schema.addMethod( "meth_1", _meth )

# Add schema to Agent

_agent.registerObjectClass(_schema)

# instantiate managed data objects matching the schema

_obj = QmfAgentData( _agent, _schema )
_obj.setProperty("index1", 100)
_obj.setProperty("index2", "a name" )

_agent.addObject( _obj )
_agent.addObject( QmfAgentData( _agent, _schema,
                                _props={"index1":99, 
                                        "index2": "another name"} ))

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
            _wi = _agent.getNextWorkitem(timeout=0)
    except:
        logging.info( "shutting down..." )
        _done = True

logging.info( "Removing connection... TBD!!!" )
#_myConsole.remove_connection( _c, 10 )

logging.info( "Destroying agent... TBD!!!" )
#_myConsole.destroy( 10 )

logging.info( "******** agent test done ********" )



