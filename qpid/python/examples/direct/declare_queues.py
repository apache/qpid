#!/usr/bin/env python
"""
 declare_queues.py 

 Creates and binds a queue on an AMQP direct exchange.

 All messages using the routing key "routing_key" are
 sent to the queue named "message_queue".
"""

# Common includes

import qpid
import sys
import os
from qpid.util import connect
from qpid.connection import Connection
from qpid.datatypes import Message, RangedSet, uuid4
from qpid.queue import Empty

#----- Initialization -----------------------------------

#  Set parameters for login

host="127.0.0.1"
port=5672
user="guest"
password="guest"

# If an alternate host or port has been specified, use that instead
# (this is used in our unit tests)
if len(sys.argv) > 1 :
  host=sys.argv[1]
if len(sys.argv) > 2 :
  port=int(sys.argv[2])

#  Create a connection.
socket = connect(host, port)
connection = Connection (sock=socket)
connection.start()
session = connection.session(str(uuid4()))

#----- Create a queue -------------------------------------

# queue_declare() creates an AMQP queue, which is held
# on the broker. Published messages are sent to the AMQP queue, 
# from which messages are delivered to consumers. 
# 
# exchange_bind() determines which messages are routed to a queue. 
# Route all messages with the binding key "routing_key" to
# the AMQP queue named "message_queue".

session.queue_declare(queue="message_queue")
session.exchange_bind(exchange="amq.direct", queue="message_queue", binding_key="routing_key")

#----- Cleanup ---------------------------------------------

session.close(timeout=10)
