#!/usr/bin/env python
"""
 listener.py

 This AMQP client reads messages from a message
 queue named "message_queue". It is implemented
 as a message listener.
"""


import qpid
import sys
import os
from qpid.util import connect
from qpid.connection import Connection
from qpid.datatypes import Message, RangedSet, uuid4
from qpid.queue import Empty

# 

from time         import sleep


#----- Message Receive Handler -----------------------------
class Receiver:
  def __init__ (self):
    self.finalReceived = False

  def isFinal (self):
    return self.finalReceived
    
  def Handler (self, message):
    content = message.body
    session.message_accept(RangedSet(message.id))
    print content

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

# Call message_subscribe() to tell the broker to deliver messages
# from the AMQP queue to this local client queue. The broker will
# start delivering messages as soon as message_subscribe() is called.

session.message_subscribe(queue="message_queue", destination=local_queue_name)
session.message_flow(local_queue_name,  session.credit_unit.message, 0xFFFFFFFF)  
session.message_flow(local_queue_name, session.credit_unit.byte, 0xFFFFFFFF) 

receiver = Receiver ()
local_queue.listen (receiver.Handler)

sleep (10)


#----- Cleanup ------------------------------------------------

# Clean up before exiting so there are no open threads.
#

session.close()
