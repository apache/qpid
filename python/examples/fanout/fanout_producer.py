#!/usr/bin/env python
"""
 fanout_producer.py

 Publishes messages to an AMQP direct exchange, using
 the routing key "routing_key"
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

#----- Publish some messages ------------------------------

# Create some messages and put them on the broker.

for i in range(10):
  message = Content(body="message " + str(i))
  session.message_transfer(destination="amq.fanout", content=message)

final="That's all, folks!"
message=Content(final)
session.message_transfer(destination="amq.fanout", content=message)

#----- Cleanup --------------------------------------------

# Clean up before exiting so there are no open threads.

session.session_close()
