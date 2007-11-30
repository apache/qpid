#!/usr/bin/env python
"""
 client.py

 Client for a client/server example

"""

import base64

import qpid
from qpid.client import Client
from qpid.content import Content
from qpid.queue import Empty

#----- Functions -------------------------------------------

def dump_queue(client, queue_name):

  print "Messages queue: " + queue_name 

  consumer_tag = queue_name     # Use the queue name as the consumer tag - need a unique tag
  queue = client.queue(consumer_tag)

  # Call basic_consume() to tell the broker to deliver messages
  # from the AMQP queue to a local client queue. The broker will
  # start delivering messages as soon as basic_consume() is called.

  session.message_subscribe(queue=queue_name, destination=consumer_tag)
  session.message_flow(consumer_tag, 0, 0xFFFFFFFF)
  session.message_flow(consumer_tag, 1, 0xFFFFFFFF)

  message = 0

  while True:
    try:
      message = queue.get(timeout=10)
      content = message.content.body
      print "Response: " + content
    except Empty:
      print "No more messages!"
      break
    except:
      print "Unexpected exception!"
      break


  #  Messages are not removed from the queue until they
  #  are acknowledged. Using multiple=True, all messages
  #  in the channel up to and including the one identified
  #  by the delivery tag are acknowledged. This is more efficient,
  #  because there are fewer network round-trips.

  if message != 0:
    message.complete(cumulative=True)


#----- Initialization --------------------------------------

#  Set parameters for login

host="127.0.0.1"
port=5672
amqp_spec="/usr/share/amqp/amqp.0-10-preview.xml"
user="guest"
password="guest"

#  Create a client and log in to it.

spec = qpid.spec.load(amqp_spec)
client = Client(host, port, spec)
client.start({"LOGIN": user, "PASSWORD": password})

session = client.session()
session.session_open() 

#----- Main Body -- ----------------------------------------

# Create a response queue for the server to send responses to. Use the
# same string as the name of the queue and the name of the routing
# key.

replyTo = "ReplyTo:" # + base64.urlsafe_b64encode(session.session_id)
session.queue_declare(queue=replyTo, exclusive=True)
session.queue_bind(exchange="amq.direct", queue=replyTo, routing_key=replyTo)

# Send some messages to the server's request queue

lines = ["Twas brilling, and the slithy toves",
         "Did gyre and gimble in the wabe.",
         "All mimsy were the borogroves,",
         "And the mome raths outgrabe."]

for l in lines:
  print "Request: " + l
  request=Content(l)
  request["routing_key"] = "request"
  request["reply_to"] = client.spec.struct("reply_to")
  request["reply_to"]["exchange_name"] = "amq.direct"
  request["reply_to"]["routing_key"] = replyTo
  session.message_transfer(destination="amq.direct", content=request)

# Now see what messages the server sent to our replyTo queue

dump_queue(client, replyTo)


#----- Cleanup ------------------------------------------------

# Clean up before exiting so there are no open threads.

session.session_close()
