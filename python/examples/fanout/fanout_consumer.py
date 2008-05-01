#!/usr/bin/env python
"""
 fanout_consumer.py

 This AMQP client reads messages from a message
 queue named "message_queue".
"""
import qpid
import sys
import os
from qpid.util import connect
from qpid.connection import Connection
from qpid.datatypes import Message, RangedSet, uuid4
from qpid.queue import Empty

#----- Functions -------------------------------------------

def dump_queue(session, queue_name):

  print "Messages queue: " + queue_name 

  consumer_tag = queue_name     # Use the queue name as the consumer tag - need a unique tag
  queue = session.incoming(queue_name)

  # Call message_subscribe() to tell the broker to deliver messages
  # from the AMQP queue to a local client queue. The broker will
  # start delivering messages as soon as message_subscribe() is called.

  session.message_subscribe(queue=queue_name, destination=consumer_tag)
  session.message_flow(consumer_tag, 0, 0xFFFFFFFF)
  session.message_flow(consumer_tag, 1, 0xFFFFFFFF)

  print "Subscribed to queue " + queue_name
  sys.stdout.flush()

  message = 0

  while True:
    try:
      message = queue.get(timeout=10)
      content = message.body
      session.message_accept(RangedSet(message.id))
      print "Response: " + content
    except Empty:
      print "No more messages!"
      break
    except:
      print "Unexpected exception!"
      break


  #  Messages are not removed from the queue until they
  #  are acknowledged. Using cumulative=True, all messages
  #  in the session up to and including the one identified
  #  by the delivery tag are acknowledged. This is more efficient,
  #  because there are fewer network round-trips.

  #if message != 0:
  # message.complete(cumulative=True)


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

# Make a unique queue name for my queue from the session ID.
my_queue = session_id
session.queue_declare(queue=my_queue)

# Bind my queue to the fanout exchange. No routing key is required
# the fanout exchange copies messages unconditionally to every
# bound queue
session.exchange_bind(queue=my_queue, exchange="amq.fanout")

# Dump the messages on the queue.
dump_queue(session, my_queue)

#----- Cleanup ------------------------------------------------

# Clean up before exiting so there are no open threads.
#

session.close(timeout=10)
