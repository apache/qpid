#!/usr/bin/env python
"""
 declare_queues.py 

 Creates and binds a queue on an AMQP direct exchange.

 All messages using the routing key "routing_key" are
 sent to the queue named "message_queue".
"""

import qpid
from qpid.client import Client
from qpid.content import Content
from qpid.queue import Empty

#----- Initialization -----------------------------------

#  Set parameters for login

host="127.0.0.1"
port=5672
amqp_spec="/usr/share/amqp/amqp.0-10-preview.xml"
user="guest"
password="guest"

#  Create a client and log in to it.

client = Client(host, port, qpid.spec.load(amqp_spec))
client.start({"LOGIN": user, "PASSWORD": password})

session = client.session()
session.session_open()

#----- Create a queue -------------------------------------

# queue_declare() creates an AMQP queue, which is held
# on the broker. Published messages are sent to the AMQP queue, 
# from which messages are delivered to consumers. 
# 
# queue_bind() determines which messages are routed to a queue. 
# Route all messages with the routing key "routing_key" to
# the AMQP queue named "message_queue".

session.queue_declare(queue="message_queue")
session.queue_bind(exchange="amq.direct", queue="message_queue", routing_key="routing_key")

#----- Cleanup ---------------------------------------------

session.session_close()


