#!/usr/bin/env python
"""
 topic_publisher.py

 This is a simple AMQP publisher application that uses a 
 Topic exchange. The publisher specifies the routing key
 and the exchange for each message.
"""

import qpid
import sys
from qpid.client import Client
from qpid.content import Content
from qpid.queue import Empty

#----- Initialization -----------------------------------

#  Set parameters for login. 
host=len(sys.argv) > 1 and sys.argv[1] or "127.0.0.1"
port=len(sys.argv) > 2 and int(sys.argv[2]) or 5672
amqp_spec="/usr/share/amqp/amqp.0-10-preview.xml"
user="guest"
password="guest"

#  Create a client and log in to it.

spec = qpid.spec.load(amqp_spec)
client = Client(host, port, spec)
client.start({"LOGIN": user, "PASSWORD": password})

session = client.session()
session.session_open()

#----- Publish some messages ------------------------------

# Create some messages and put them on the broker. Use the
# topic exchange.  The routing keys are "usa.news", "usa.weather", 
# "europe.news", and "europe.weather".


# usa.news

for i in range(5):
  message = Content("message " + str(i))
  message["routing_key"] = "usa.news"
  session.message_transfer(destination="amq.topic", content=message)

# usa.weather

for i in range(5):
  message = Content("message " + str(i))
  message["routing_key"] = "usa.weather"
  session.message_transfer(destination="amq.topic", content=message)

# europe.news

for i in range(5):
  message = Content("message " + str(i))
  message["routing_key"] = "europe.news"
  session.message_transfer(destination="amq.topic", content=message)

# europe.weather

for i in range(5):
  message = Content("message " + str(i))
  message["routing_key"] = "europe.weather"
  session.message_transfer(destination="amq.topic", content=message)

# Signal termination

message = Content("That's all, folks!")
message["routing_key"] = "control"
session.message_transfer(destination="amq.topic", content=message)


#----- Cleanup --------------------------------------------

# Clean up before exiting so there are no open threads.

session.session_close()
