#!/usr/bin/env python
"""
 fanout_consumer.py

 This AMQP client reads messages from a message
 queue named "message_queue".
"""

import qpid
from qpid.client import Client
from qpid.content import Content
from qpid.queue import Empty


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

session = client.session()
session.session_open()

#----- Read from queue --------------------------------------------

# Now let's create a local client queue and tell it to read
# incoming messages.

# The consumer tag identifies the client-side queue.

consumer_tag = "consumer1"
queue = client.queue(consumer_tag)

# Call message_subscribe() to tell the broker to deliver messages
# from the AMQP queue to this local client queue. The broker will
# start delivering messages as soon as message_subscribe() is called.

session.message_subscribe(queue="message_queue", destination=consumer_tag)
session.message_flow(consumer_tag, 0, 0xFFFFFFFF)
session.message_flow(consumer_tag, 1, 0xFFFFFFFF)

#  Initialize 'final' and 'content', variables used to identify the last message.

final = "That's all, folks!"   # In a message body, signals the last message
content = ""		       # Content of the last message read

message = None
while content != final:
	message = queue.get(timeout=10)
	content = message.content.body
	print content

#  Messages are not removed from the queue until they are
#  acknowledged. Using cumulative=True, all messages from the session
#  up to and including the one identified by the delivery tag are
#  acknowledged. This is more efficient, because there are fewer
#  network round-trips.

message.complete(cumulative=True)

#----- Cleanup ------------------------------------------------

# Clean up before exiting so there are no open threads.
#

session.session_close()
