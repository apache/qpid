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
from qpid.datatypes import Message, RangedSet
from qpid.queue import Empty


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

session = conn.session(str(randint(1,64*1024)))

#----- Read from queue --------------------------------------------

# Now let's create a local client queue and tell it to read
# incoming messages.

# The consumer tag identifies the client-side queue.

consumer_tag = "consumer1"
queue = session.incoming(consumer_tag)

# Call message_consume() to tell the broker to deliver messages
# from the AMQP queue to this local client queue. The broker will
# start delivering messages as soon as message_consume() is called.

session.message_subscribe(queue="message_queue", destination=consumer_tag)
session.message_flow(consumer_tag, 0, 0xFFFFFFFF)  # Kill these?
session.message_flow(consumer_tag, 1, 0xFFFFFFFF) # Kill these?

#  Initialize 'final' and 'content', variables used to identify the last message.

final = "That's all, folks!"   # In a message body, signals the last message
content = ""		       # Content of the last message read

message = None
while content != final:
	message = queue.get(timeout=10)
	content = message.body          
        session.message_accept(RangedSet(message.id))
	print content

#  Messages are not removed from the queue until they are
#  acknowledged. Using cumulative=True, all messages from the session
#  up to and including the one identified by the delivery tag are
#  acknowledged. This is more efficient, because there are fewer
#  network round-trips.

#message.complete(cumulative=True)
# ? Is there an equivakent to the above in the new API ?

#----- Cleanup ------------------------------------------------

# Clean up before exiting so there are no open threads.
#

session.close(timeout=10)
