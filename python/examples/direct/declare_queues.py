#!/usr/bin/env python
"""
 declare_queues.py 

 Creates and binds a queue on an AMQP direct exchange.

 All messages using the routing key "routing_key" are
 sent to the queue named "message_queue".
"""

import qpid
import sys
import os
from random import randint
from qpid.util import connect
from qpid.connection import Connection
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
