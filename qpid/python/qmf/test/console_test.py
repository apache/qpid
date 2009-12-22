import logging
import time
from threading import Semaphore


from qpid.messaging import *
from qmfCommon import (Notifier, QmfQuery)
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
_myConsole.addConnection( _c )

# Discover only agents from vendor "redhat.com" that 
# are a "qmf" product....
# @todo: replace "manual" query construction with 
# a formal class-based Query API
_query = {QmfQuery._TARGET: 
          {QmfQuery._TARGET_AGENT:None},
          QmfQuery._PREDICATE:
              {QmfQuery._LOGIC_AND: 
               [{QmfQuery._CMP_EQ: ["vendor",  "redhat.com"]},
                {QmfQuery._CMP_EQ: ["product", "qmf"]}]}}
_query = QmfQuery(_query)

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
_myConsole.removeConnection( _c, 10 )

logging.info( "Destroying console:" )
_myConsole.destroy( 10 )

logging.info( "******** console test done ********" )
