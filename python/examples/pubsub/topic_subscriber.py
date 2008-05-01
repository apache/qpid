#!/usr/bin/env python
"""
 topic_subscriber.py

 This subscriber creates private queues and binds them
 to the topics 'usa.#', 'europe.#', '#.news', and '#.weather'.
"""

import qpid
import sys
import os
from qpid.util import connect
from qpid.connection import Connection
from qpid.datatypes import Message, RangedSet, uuid4
from qpid.queue import Empty

#----- Functions -------------------------------------------

def dump_queue(queue_name):

  print "Messages queue: " + queue_name 

  consumer_tag = queue_name     # Use the queue name as the consumer tag - need a unique tag
  queue = session.incoming(consumer_tag)

  # Call message_subscribe() to tell the broker to deliver messages
  # from the AMQP queue to a local client queue. The broker will
  # start delivering messages as soon as message_subscribe() is called.

  session.message_subscribe(queue=queue_name, destination=consumer_tag)
  session.message_flow(consumer_tag, 0, 0xFFFFFFFF)
  session.message_flow(consumer_tag, 1, 0xFFFFFFFF)

  content = ""		         # Content of the last message read
  final = "That's all, folks!"   # In a message body, signals the last message
  message = 0

  while content != final:
    try:
      message = queue.get()
      content = message.body
      session.message_accept(RangedSet(message.id)) 
      print content
    except Empty:
      #if message != 0:
      #  message.complete(cumulative=True)
      print "No more messages!"
      return


  #  Messages are not removed from the queue until they
  #  are acknowledged. Using multiple=True, all messages
  #  in the channel up to and including the one identified
  #  by the delivery tag are acknowledged. This is more efficient,
  #  because there are fewer network round-trips.

  #if message != 0:
  #  message.complete(cumulative=True)


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


news = "news" + session_id
weather = "weather" + session_id
usa = "usa" + session_id
europe = "europe" + session_id

session.queue_declare(queue=news, exclusive=True)
session.queue_declare(queue=weather, exclusive=True)
session.queue_declare(queue=usa, exclusive=True)
session.queue_declare(queue=europe, exclusive=True)

# Routing keys may be "usa.news", "usa.weather", "europe.news", or "europe.weather".

# The '#' symbol matches one component of a multipart name, e.g. "#.news" matches
# "europe.news" or "usa.news".

session.exchange_bind(exchange="amq.topic", queue=news, binding_key="#.news")
session.exchange_bind(exchange="amq.topic", queue=weather, binding_key="#.weather")
session.exchange_bind(exchange="amq.topic", queue=usa, binding_key="usa.#")
session.exchange_bind(exchange="amq.topic", queue=europe, binding_key="europe.#")

# Bind each queue to the control queue so we know when to stop

session.exchange_bind(exchange="amq.topic", queue=news, binding_key="control")
session.exchange_bind(exchange="amq.topic", queue=weather, binding_key="control")
session.exchange_bind(exchange="amq.topic", queue=usa, binding_key="control")
session.exchange_bind(exchange="amq.topic", queue=europe, binding_key="control")

# Remind the user to start the topic producer

print "Queues created - please start the topic producer"
sys.stdout.flush()

# Call dump_queue to print messages from each queue

dump_queue(news)
dump_queue(weather)
dump_queue(usa)
dump_queue(europe)

#----- Cleanup ------------------------------------------------

# Clean up before exiting so there are no open threads.

session.close(timeout=10)
