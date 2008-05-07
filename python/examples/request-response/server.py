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
       raise Exception("reply to property needs to be there")

    props = session.delivery_properties(routing_key=reply_to["routing_key"])
    session.message_transfer(reply_to["exchange"],None, None, Message(props,request.body.upper()))

#----- Initialization --------------------------------------

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

session_id = str(uuid4())
session = conn.session(session_id)

#----- Main Body -- ----------------------------------------

# Create a request queue and subscribe to it

session.queue_declare(queue="request", exclusive=True)
session.exchange_bind(exchange="amq.direct", queue="request", binding_key="request")

dest = "request_destination"

session.message_subscribe(queue="request", destination=dest)
session.message_flow(dest, 0, 0xFFFFFFFF)
session.message_flow(dest, 1, 0xFFFFFFFF)


# Remind the user to start the client program

print "Request server running - run your client now."
print "(Times out after 100 seconds ...)"
sys.stdout.flush()

# Respond to each request

queue = session.incoming(dest)

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
