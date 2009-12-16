import logging
import time
from threading import Semaphore


from qpid.messaging import *
from qmfCommon import (Notifier, Query)
from qmfConsole import Console


class ExampleNotifier(Notifier):
    def __init__(self):
        self._sema4 = Semaphore(0)   # locked

    def indication(self):
        self._sema4.release()

    def waitForWork(self):
        logging.error("Waiting for event...")
        self._sema4.acquire()
        logging.error("...event present")


logging.getLogger().setLevel(logging.INFO)

logging.info( "Starting Connection" )
_c = Connection("localhost")
_c.connect()

logging.info( "Starting Console" )

_notifier = ExampleNotifier()
_myConsole = Console(notifier=_notifier)
_myConsole.add_connection( _c )

# Discover only agents from vendor "redhat.com" that 
# are a "qmf" product....
# @todo: replace "manual" query construction with 
# a formal class-based Query API
_query = {Query._TARGET: {Query._TARGET_AGENT_ID:None},
          Query._PREDICATE:
              [Query._LOGIC_AND,
               [Query._CMP_EQ, "vendor",  "redhat.com"],
               [Query._CMP_EQ, "product", "qmf"]]}

_myConsole.enableAgentDiscovery(_query)

_done = False
while not _done:
    try:
        _notifier.waitForWork()

        _wi = _myConsole.get_next_workitem(timeout=0)
        while _wi:
            print("!!! work item received %d:%s" % (_wi.getType(), str(_wi.getParams())))
            _wi = _myConsole.get_next_workitem(timeout=0)
    except:
        logging.info( "shutting down..." )
        _done = True

logging.info( "Removing connection" )
_myConsole.remove_connection( _c, 10 )

logging.info( "Destroying console:" )
_myConsole.destroy( 10 )

logging.info( "******** console test done ********" )
