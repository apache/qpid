#!/usr/bin/env python
"""
 direct_consumer.py

 This AMQP client reads messages from a message
 queue named "message_queue".
"""

import qpid
import sys
import os
from random import randint
from qpid.util import connect
from qpid.connection import Connection
from qpid.datatypes import Message, RangedSet, uuid4
from qpid.queue import Empty


#----- Initialization --------------------------------------

#  Set parameters for login

host="127.0.0.1"
port=5672
user="guest"
password="guest"

# If an alternate host or port has been specified, use that instead
# (this is used in our unit tests)
if len(sys.argv) > 1 :
  host=sys.argv[1]
if len(sys.argv) > 2 :
  port=int(sys.argv[2])

#  Create a connection.
socket = connect(host, port)
connection = Connection (sock=socket)
connection.start()
session = connection.session(str(uuid4()))


#----- Read from queue --------------------------------------------

# Now let's create a local client queue and tell it to read
# incoming messages.

# The consumer tag identifies the client-side queue.

local_queue_name = "local_queue"
local_queue = session.incoming(local_queue_name)

# Call message_consume() to tell the broker to deliver messages
# from the AMQP queue to this local client queue. The broker will
# start delivering messages as soon as message_consume() is called.

session.message_subscribe(queue="message_queue", destination=local_queue_name)
session.message_flow(local_queue_name,  session.credit_unit.message, 0xFFFFFFFF)
session.message_flow(local_queue_name, session.credit_unit.byte, 0xFFFFFFFF)

#  Initialize 'final' and 'content', variables used to identify the last message.

message = None
while True:
   try:
	message = local_queue.get(timeout=10)
        session.message_accept(RangedSet(message.id))
	content = message.body
	print content
   except Empty:
        print "No more messages!"
        break


#----- Cleanup ------------------------------------------------

# Clean up before exiting so there are no open threads.
#

session.close()
