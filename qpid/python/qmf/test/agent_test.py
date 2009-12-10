import logging
import time

from qpid.messaging import *
from qmfCommon import (AgentId, SchemaEventClassFactory, qmfTypes, SchemaProperty,
                       SchemaObjectClass, ObjectIdFactory, QmfData, QmfDescribed,
                       QmfDescribedFactory, QmfManaged, QmfManagedFactory, QmfDataFactory,
                       QmfEvent, SchemaMethod)
from qmfAgent import (Agent, QmfAgentData)


class MyAgent(object):
    def main(self):

        self._agent = Agent( "redhat.com", "qmf", "testAgent" )
        
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

        _schema.addMethod( "meth_3", _meth )

        # Add schema to Agent

        self._agent.registerObjectClass(_schema)

        # instantiate managed data objects matching the schema

        obj = QmfAgentData( self._agent, _schema )
        obj.setProperty("index1", 100)
        obj.setProperty("index2", "a name" )


        self._agent.addObject( QmfAgentData( self._agent, _schema,
                                             _props={"index1":99, 
                                                     "index2": "another name"} ))


        return None




app = MyAgent()
print( "s='%s'", str(app.main()))
