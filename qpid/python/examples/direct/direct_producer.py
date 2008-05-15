#!/usr/bin/env python
"""
 direct_producer.py

 Publishes messages to an AMQP direct exchange, using
 the routing key "routing_key"
"""

import qpid
import sys
import os
from qpid.util import connect
from qpid.connection import Connection
from qpid.datatypes import Message
from qpid.datatypes import uuid4
from qpid.queue import Empty


#----- Initialization -----------------------------------

#  Set parameters for login

host="127.0.0.1"
port=5672
user="guest"
password="guest"

# If an alternate host or port has been specified, use that instead
# (this is used in our unit tests)
#
# If AMQP_SPEC is defined, use it to locate the spec file instead of
# looking for it in the default location.

if len(sys.argv) > 1 :
  host=sys.argv[1]
if len(sys.argv) > 2 :
  port=int(sys.argv[2])

#  Create a connection.
socket = connect(host, port)
connection = Connection (sock=socket)
connection.start()
session = connection.session(str(uuid4()))

#----- Publish some messages ------------------------------

# Create some messages and put them on the broker.
props = session.delivery_properties(routing_key="routing_key")

for i in range(10):
     session.message_transfer(destination="amq.direct", message=Message(props,"message " + str(i)))

session.message_transfer(destination="amq.direct", message=Message(props,"That's all, folks!"))

#----- Cleanup --------------------------------------------

# Clean up before exiting so there are no open threads.

session.close(timeout=10)
