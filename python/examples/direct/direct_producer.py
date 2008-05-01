#!/usr/bin/env python
"""
 direct_producer.py

 Publishes messages to an AMQP direct exchange, using
 the routing key "routing_key"
"""

import qpid
import sys
import os
from random import randint
from qpid.util import connect
from qpid.connection import Connection
from qpid.datatypes import Message
from qpid.queue import Empty

#----- Initialization -----------------------------------

#  Set parameters for login

host=len(sys.argv) > 1 and sys.argv[1] or "127.0.0.1"
port=len(sys.argv) > 2 and int(sys.argv[2]) or 5672
user="guest"
password="guest"
amqp_spec=""

try:
     amqp_spec = os.environ["AMQP_SPEC"]
except KeyError:
     amqp_spec="/usr/share/amqp/amqp.0-10.xml"

#  Create a connection.
conn = Connection (connect (host,port), qpid.spec.load(amqp_spec))
conn.start()

session = conn.session(str(randint(1,64*1024)))

#----- Publish some messages ------------------------------

# Create some messages and put them on the broker.
props = session.delivery_properties(routing_key="routing_key")

for i in range(10):
  session.message_transfer("amq.direct",None, None, Message(props,"message " + str(i)))

session.message_transfer("amq.direct",None,None, Message(props,"That's all, folks!"))

#----- Cleanup --------------------------------------------

# Clean up before exiting so there are no open threads.

session.close(timeout=10)
