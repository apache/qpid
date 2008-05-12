#!/usr/bin/env python
"""
 topic_publisher.py

 This is a simple AMQP publisher application that uses a 
 Topic exchange. The publisher specifies the routing key
 and the exchange for each message.
"""

import qpid
import sys
import os
from qpid.util import connect
from qpid.connection import Connection
from qpid.datatypes import Message, RangedSet, uuid4
from qpid.queue import Empty

#----- Functions ----------------------------------------

def send_msg(routing_key):
  props = session.delivery_properties(routing_key=routing_key)
  for i in range(5):
     session.message_transfer(destination="amq.topic", message=Message(props,routing_key + " " + str(i)))

#----- Initialization -----------------------------------

#  Set parameters for login

host="127.0.0.1"
port=5672
user="guest"
password="guest"
amqp_spec="/usr/share/amqp/amqp.0-10.xml"     

# If an alternate host or port has been specified, use that instead
# (this is used in our unit tests)
#
# If AMQP_SPEC is defined, use it to locate the spec file instead of
# looking for it in the default location.

if len(sys.argv) > 1 :
  host=sys.argv[1]
if len(sys.argv) > 2 :
  port=int(sys.argv[2])

try:
     amqp_spec = os.environ["AMQP_SPEC"]
except KeyError:
     amqp_spec="/usr/share/amqp/amqp.0-10.xml"

#  Create a connection.
socket = connect(host, port)
connection = Connection (sock=socket, spec=qpid.spec.load(amqp_spec))
connection.start()
session = connection.session(str(uuid4()))

#----- Publish some messages ------------------------------

# Create some messages and put them on the broker. Use the
# topic exchange.  The routing keys are "usa.news", "usa.weather", 
# "europe.news", and "europe.weather".

# usa.news
send_msg("usa.news")

# usa.weather
send_msg("usa.weather")

# europe.news
send_msg("europe.news")

# europe.weather
send_msg("europe.weather")

# Signal termination
props = session.delivery_properties(routing_key="control")
session.message_transfer(destination="amq.topic", message=Message(props,"That's all, folks!"))


#----- Cleanup --------------------------------------------

# Clean up before exiting so there are no open threads.

session.close(timeout=10)
