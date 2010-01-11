import logging
import time
from threading import Semaphore


from qpid.messaging import *
from qmfCommon import (Notifier, QmfQuery, MsgKey, SchemaClassId, SchemaClass)
from qmfConsole import Console


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

# Discover only agents from vendor "redhat.com" that 
# are a "qmf" product....
# @todo: replace "manual" query construction with 
# a formal class-based Query API
_query = {QmfQuery._TARGET: 
          {QmfQuery._TARGET_AGENT:None},
          QmfQuery._PREDICATE:
              {QmfQuery._CMP_EQ: ["_name",  "qmf.testAgent"]}}
_query = QmfQuery(_query)

_myConsole.enableAgentDiscovery(_query)

_done = False
while not _done:
#    try:
    _notifier.waitForWork()

    _wi = _myConsole.getNextWorkItem(timeout=0)
    while _wi:
        print("!!! work item received %d:%s" % (_wi.getType(),
                                                str(_wi.getParams())))


        if _wi.getType() == _wi.AGENT_ADDED:
            _agent = _wi.getParams().get("agent")
            if not _agent:
                print("!!!! AGENT IN REPLY IS NULL !!! ")

            _query = QmfQuery( {QmfQuery._TARGET: 
                                {QmfQuery._TARGET_PACKAGES:None}} )

            _reply = _myConsole.doQuery(_agent, _query)

            package_list = _reply.get(MsgKey.package_info)
            for pname in package_list:
                print("!!! Querying for schema from package: %s" % pname)
                _query = QmfQuery({QmfQuery._TARGET: 
                                   {QmfQuery._TARGET_SCHEMA_ID:None},
                                   QmfQuery._PREDICATE:
                                       {QmfQuery._CMP_EQ: 
                                        [SchemaClassId.KEY_PACKAGE, pname]}})

                _reply = _myConsole.doQuery(_agent, _query)

                schema_id_list = _reply.get(MsgKey.schema_id)
                for sid_map in schema_id_list:
                    _query = QmfQuery({QmfQuery._TARGET: 
                                       {QmfQuery._TARGET_SCHEMA:None},
                                       QmfQuery._PREDICATE:
                                           {QmfQuery._CMP_EQ: 
                                            [SchemaClass.KEY_SCHEMA_ID, sid_map]}})

                    _reply = _myConsole.doQuery(_agent, _query)



        _myConsole.releaseWorkItem(_wi)
        _wi = _myConsole.getNextWorkItem(timeout=0)
#    except:
#        logging.info( "shutting down..." )
#        _done = True

print( "Removing connection" )
_myConsole.removeConnection( _c, 10 )

print( "Destroying console:" )
_myConsole.destroy( 10 )

print( "******** console test done ********" )
