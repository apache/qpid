#!/usr/bin/env python
"""
 server.py

 Server for a client/server example
"""

import qpid
import sys
import os
from qpid.util import connect
from qpid.connection import Connection
from qpid.datatypes import Message, RangedSet, uuid4
from qpid.queue import Empty

#----- Functions -------------------------------------------
def respond(session, request):

    # The routing key for the response is the request's reply-to
    # property.  The body for the response is the request's body,
    # converted to upper case.

    message_properties = request.get("message_properties")
    reply_to = message_properties.reply_to
    if reply_to == None:
       raise Exception("This message is missing the 'reply_to' property, which is required")   
   
    props = session.delivery_properties(routing_key=reply_to["routing_key"]) 
    session.message_transfer(destination=reply_to["exchange"], message=Message(props,request.body.upper()))

#----- Initialization --------------------------------------


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

socket = connect(host, port)
connection = Connection (sock=socket)
connection.start()
session = connection.session(str(uuid4()))

#----- Main Body -- ----------------------------------------

# Create a request queue and subscribe to it

session.queue_declare(queue="request", exclusive=True)
session.exchange_bind(exchange="amq.direct", queue="request", binding_key="request")

local_queue_name = "local_queue"

session.message_subscribe(queue="request", destination=local_queue_name)
session.message_flow(local_queue_name, session.credit_unit.message, 0xFFFFFFFF)
session.message_flow(local_queue_name, session.credit_unit.byte, 0xFFFFFFFF)


# Remind the user to start the client program

print "Request server running - run your client now."
print "(Times out after 100 seconds ...)"
sys.stdout.flush()

# Respond to each request

queue = session.incoming(local_queue_name)

# If we get a message, send it back to the user (as indicated in the
# ReplyTo property)

while True:
  try:
    request = queue.get(timeout=100)
    respond(session, request)
    session.message_accept(RangedSet(request.id))
  except Empty:
    print "No more messages!"
    break;


#----- Cleanup ------------------------------------------------

# Clean up before exiting so there are no open threads.

session.close(timeout=10)
