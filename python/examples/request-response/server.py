#!/usr/bin/env python
"""
 server.py

 Server for a client/server example
"""

import qpid
from qpid.client import Client
from qpid.content import Content
from qpid.queue import Empty

#----- Functions -------------------------------------------

def respond(session, request):

    # The routing key for the response is the request's reply-to
    # property.  The body for the response is the request's body,
    # converted to upper case.
    
    response=Content(request.body.upper())
    response["routing_key"] = request["reply_to"]["routing_key"]

    session.message_transfer(destination=request["reply_to"]["exchange_name"], content=response)

#----- Initialization --------------------------------------

#  Set parameters for login

host="127.0.0.1"
port=5672
amqp_spec="/usr/share/amqp/amqp.0-10-preview.xml"
user="guest"
password="guest"

#  Create a client and log in to it.

client = Client(host, port, qpid.spec.load(amqp_spec))
client.start({"LOGIN": user, "PASSWORD": password})

# Create a session and open it.

session = client.session()
session.session_open() 

#----- Main Body -- ----------------------------------------

# Create a request queue and subscribe to it

session.queue_declare(queue="request", exclusive=True)
session.queue_bind(exchange="amq.direct", queue="request", routing_key="request")

dest = "request_destination"

session.message_subscribe(queue="request", destination=dest)
session.message_flow(dest, 0, 0xFFFFFFFF)
session.message_flow(dest, 1, 0xFFFFFFFF)


# Remind the user to start the client program

print "Request server running - run your client now."
print "(Times out after 100 seconds ...)"

# Respond to each request

queue = client.queue(dest)

# If we get a message, send it back to the user (as indicated in the
# ReplyTo property)

while True:
  try:
    request = queue.get(timeout=100)
    respond(session, request.content)
    request.complete()
  except Empty:
    print "No more messages!"
    break;


#----- Cleanup ------------------------------------------------

# Clean up before exiting so there are no open threads.

session.session_close()
