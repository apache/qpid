#!/usr/bin/env python
"""
 topic_consumer.py

 This AMQP client reads all messages from the
 "news", "weather", "usa", and "europe" queues
 created and bound by config_topic_exchange.py. 
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

  content = ""		         # Content of the last message read
  final = "That's all, folks!"   # In a message body, signals the last message
  message = 0

  while content != final:
    try:
      message = queue.get()
      content = message.content.body
      print content
    except Empty:
      if message != 0:
        message.complete(cumulative=True)
      print "No more messages!"
      return


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
session = session.session_open()  # keep the session object, we'll need the session id

#----- Main Body -- ----------------------------------------


news = "news" + base64.urlsafe_b64encode(session.session_id)
weather = "weather" + base64.urlsafe_b64encode(session.session_id)
usa = "usa" + base64.urlsafe_b64encode(session.session_id)
europe = "europe" + base64.urlsafe_b64encode(session.session_id)

session.queue_declare(queue=news, exclusive=True)
session.queue_declare(queue=weather, exclusive=True)
session.queue_declare(queue=usa, exclusive=True)
session.queue_declare(queue=europe, exclusive=True)

# Routing keys may be "usa.news", "usa.weather", "europe.news", or "europe.weather".

# The '#' symbol matches one component of a multipart name, e.g. "#.news" matches
# "europe.news" or "usa.news".

session.queue_bind(exchange="amq.topic", queue=news, routing_key="#.news")
session.queue_bind(exchange="amq.topic", queue=weather, routing_key="#.weather")
session.queue_bind(exchange="amq.topic", queue=usa, routing_key="usa.#")
session.queue_bind(exchange="amq.topic", queue=europe, routing_key="europe.#")

# Remind the user to start the topic producer

print "Queues create - please start the topic producer"

# Call dump_queue to print messages from each queue

dump_queue(client, news)
dump_queue(client, weather)
dump_queue(client, usa)
dump_queue(client, europe)

#----- Cleanup ------------------------------------------------

# Clean up before exiting so there are no open threads.
#
# Close Channel 1.

session.session_close()

